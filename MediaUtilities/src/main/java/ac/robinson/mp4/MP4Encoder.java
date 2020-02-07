// Enormous thanks to Andrew McFadden for his MediaCodec examples!
// Adapted from http://bigflake.com/mediacodec/CameraToMpegTest.java.txt
// and https://github.com/OnlyInAmerica/HWEncoderExperiments/blob/master/HWEncoderExperiments/src/main/java/net/openwatch
// /hwencoderexperiments/MP4Encoder.java

package ac.robinson.mp4;

import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;

import com.bric.audio.AudioInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import ac.robinson.mediautilities.AudioUtilities;
import ac.robinson.mediautilities.FrameMediaContainer;
import ac.robinson.mediautilities.MediaUtilities;
import ac.robinson.util.IOUtilities;
import androidx.annotation.RequiresApi;

/**
 * Utility class that can export narratives in MP4 format. Based on examples at https://www.bigflake.com/mediacodec/ and
 * https://github.com/OnlyInAmerica/HWEncoderExperiments/blob/master/HWEncoderExperiments/src/main/java/net/openwatch
 * /hwencoderexperiments/ChunkedHWRecorder.java
 * <p>
 * TODO: in future, use MediaExtractor to decode narrative audio files, mix (manually) the PCM files as now, then re-encode here?
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MP4Encoder {
	private static final String LOG_TAG = "MP4Encoder";
	private static final boolean VERBOSE = false; // lots of logging if enabled

	// parameters for the encoder
	private static final String VIDEO_MIME_TYPE = "video/avc"; // H.264; Advanced Video Coding (MediaFormat.MIMETYPE_VIDEO_AVC)
	private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm"; // AAC; Advanced Audio Coding (^ MIMETYPE_AUDIO_AAC)
	private static final int OUTPUT_FORMAT = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
	private static final int BIT_RATE = 12288000; // bits per second
	private static final int FRAME_RATE = 30; // frames per second (variable at narrative frame transitions)

	// encoder / muxer state and track info
	private MediaCodec mVideoEncoder;
	private MediaCodec mAudioEncoder;
	private MP4CodecInputSurface mInputSurface;
	private MediaMuxerWrapper mMuxerWrapper;
	private TrackInfo mVideoTrackInfo;
	private TrackInfo mAudioTrackInfo;

	// allocate buffer descriptors upfront so we don't need to do it every time
	private MediaCodec.BufferInfo mVideoBufferInfo;
	private MediaCodec.BufferInfo mAudioBufferInfo;

	// surface for drawing images/text
	private MP4DrawSurface mDrawSurface;
	private float[] mModelViewProjectionMatrix = new float[16];

	// audio state
	private int mAudioBufferSize;
	private byte[] mAudioInputBuffer;
	private long mLastEncodedAudioTimeStamp = 0;
	private long mAudioPresentationTimeUs = 0;

	// recording state
	private boolean mAudioEnded;
	private boolean mEndOfOutputReached;

	public boolean createMP4(Resources resources, File outputFile, ArrayList<FrameMediaContainer> videoFrames,
							 AudioUtilities.CombinedAudioTrack combinedAudioTrack, Map<Integer, Object> settings) {

		if (videoFrames == null || videoFrames.size() <= 0) {
			return false;
		}

		// should really do proper checking on these
		final int outputWidth = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_WIDTH);
		final int outputHeight = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_HEIGHT);

		boolean hasAudio = combinedAudioTrack.mCombinedPCMFile != null;
		int audioSampleRate = hasAudio ? (int) combinedAudioTrack.mCombinedPCMAudioFormat.getSampleRate() : 0;
		AudioInputStream audioInputStream = null;

		long totalDuration = 0;
		for (FrameMediaContainer frame : videoFrames) {
			totalDuration += frame.mFrameMaxDuration; // better fit I frames to image changes
		}
		int iFrameInterval = (int) ((totalDuration / videoFrames.size()) / 1000f);
		Log.d(LOG_TAG, "Setting I frame interval to " + iFrameInterval);

		Log.d(LOG_TAG,
				"Creating " + VIDEO_MIME_TYPE + "+" + AUDIO_MIME_TYPE + " output, " + outputWidth + "x" + outputHeight + " at " +
						FRAME_RATE + " fps, " + BIT_RATE + "bps, i-frame interval " + iFrameInterval + ", " +
						(hasAudio ? "with" : "no") + " audio");

		try {
			// initialise - throws IOException if either audio or video encoders couldn't be created
			prepareEncoder(outputFile, outputWidth, outputHeight, iFrameInterval, audioSampleRate);

			// set up video output
			mInputSurface.makeCurrent();
			initialiseDrawSurface(resources, outputWidth, outputHeight, settings);

			if (hasAudio) {
				// use audio buffer size that means audio blocks are the same length as video ones (for simpler synchronisation)
				// alternative: AudioRecord.getMinBufferSize(audioSampleRate, CHANNEL_CONFIG, AUDIO_FORMAT);
				// TODO: will this be okay for all devices, sample rates and frame rates? (e.g., low frame rates will overflow)
				mAudioBufferSize = audioSampleRate * 2 / FRAME_RATE;
				mAudioInputBuffer = new byte[mAudioBufferSize];

				// default input is PCM, mono, 16-bit (i.e., 2 bytes per sample (or 'frame')) at 44100 samples per second
				// AudioFormat audioFormat = new AudioFormat(audioSampleRate, 16, 1, true, false);
				// PCM 'frame' is 2 bytes (e.g., see AudioFormat: ((sampleSizeInBits + 7) / 8) * channels); length in frames is
				// therefore file size / 2
				audioInputStream = new AudioInputStream(new FileInputStream(combinedAudioTrack.mCombinedPCMFile),
						combinedAudioTrack.mCombinedPCMAudioFormat,
						combinedAudioTrack.mCombinedPCMFile.length() / 2);
			}

			long startTime = System.nanoTime();
			int videoFrameCount = 0;
			long videoPresentationTimeNs = 0;
			long videoPresentationTimeIncrementNs = (long) (1000000000L / (float) FRAME_RATE);

			FrameMediaContainer currentNarrativeFrame = videoFrames.remove(0);
			long currentFrameEndNs = currentNarrativeFrame.mFrameMaxDuration * 1000000L;

			mAudioEnded = false;
			mEndOfOutputReached = false;
			boolean firstFrameReady = false;
			while (true) {
				// feed any pending encoder video/audio output into the muxer
				synchronized (mVideoTrackInfo.mMuxerWrapper.mSync) {
					drainEncoder(mVideoEncoder, mVideoBufferInfo, mVideoTrackInfo, mEndOfOutputReached);
				}
				if (firstFrameReady && hasAudio) { // need to send at least one video frame before beginning audio
					synchronized (mAudioTrackInfo.mMuxerWrapper.mSync) {
						drainEncoder(mAudioEncoder, mAudioBufferInfo, mAudioTrackInfo, mEndOfOutputReached);
					}
				}

				if (mEndOfOutputReached) {
					break; // *after* draining existing output
				}

				// draw a new frame of input, and render it to the Surface, setting its presentation timestamp (nanoseconds)
				// TODO: fix text only, image only, etc
				mDrawSurface.drawNarrativeFrame(currentNarrativeFrame);
				mDrawSurface.draw(mModelViewProjectionMatrix);
				mInputSurface.setPresentationTime(videoPresentationTimeNs);

				videoPresentationTimeNs += videoPresentationTimeIncrementNs;

				// switch to the next narrative frame if we've exceeded the current frame's presentation time
				if (videoPresentationTimeNs > currentFrameEndNs) {
					if (VERBOSE) {
						Log.d(LOG_TAG, "Switching narrative frames; " + videoFrames.size() + " remaining; video time: " +
								videoPresentationTimeNs + "; current frame time: " + currentFrameEndNs);
					}
					if (videoFrames.size() > 0) {
						currentNarrativeFrame = videoFrames.remove(0);
						videoPresentationTimeNs = currentFrameEndNs;
						currentFrameEndNs += currentNarrativeFrame.mFrameMaxDuration * 1000000L;
						if (VERBOSE) {
							Log.d(LOG_TAG,
									"New video time: " + videoPresentationTimeNs + "; frame end time " + currentFrameEndNs);
						}
					} else {
						if (VERBOSE) {
							Log.d(LOG_TAG, "End of video output reached");
						}
						mEndOfOutputReached = true;
					}
				}

				// add this frame's audio (or silence)
				if (hasAudio) {
					sendAudioToEncoder(audioInputStream, audioSampleRate, mEndOfOutputReached);

					if (mEndOfOutputReached) {
						// make sure that the last video frame is presented at the same time as the last audio frame's end
						mInputSurface.setPresentationTime(mAudioPresentationTimeUs * 1000);
					}
				}

				// submit the new frame to the encoder - the eglSwapBuffers call will block if the input is full, which would
				// be bad if it stayed full until we dequeued an output buffer (which we can't do, since we're stuck here), but
				// as long as we fully drain the encoder before supplying additional input (which we do above), the system
				// guarantees that we can supply another frame without blocking.
				if (VERBOSE) {
					Log.d(LOG_TAG, "Sending frames to encoder (main loop)");
				}
				mInputSurface.swapBuffers();

				videoFrameCount++;
				firstFrameReady = true;
			}

			double recordingDurationSec = (System.nanoTime() - startTime) / 1000000000.0;
			Log.d(LOG_TAG, "Finished encode loop. Processing for " + recordingDurationSec + "s. Produced " + videoFrameCount +
					" video frames; " + Math.round((videoFrameCount / (videoPresentationTimeNs / 1000000000.0))) + " fps");

		} catch (Exception e) {
			Log.e(LOG_TAG, "MP4 encoding loop exception - aborting");
			e.printStackTrace();
			return false;
		} finally {
			IOUtilities.closeStream(audioInputStream);
			releaseRecordingResources();
		}

		return true;
	}


	/**
	 * Sends the next chunk of the PCM audio file to the encoder
	 */
	private void sendAudioToEncoder(AudioInputStream audioInputStream, int sampleRate, boolean endOfStream) {
		try {
			ByteBuffer[] inputBuffers = mAudioEncoder.getInputBuffers();
			int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1); // no timeout (to keep video/audio in sync)
			if (inputBufferIndex >= 0) {
				ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
				inputBuffer.clear();

				int inputLength = -1;
				if (!mAudioEnded) {
					inputLength = audioInputStream.read(mAudioInputBuffer, 0, mAudioBufferSize);
				}

				// when the stream is empty, fill with silence
				if (inputLength < 0) {
					Arrays.fill(mAudioInputBuffer, (byte) 0);
					inputLength = mAudioBufferSize;
					mAudioEnded = true;
				}
				inputBuffer.put(mAudioInputBuffer);

				if (VERBOSE) {
					Log.i(LOG_TAG, "Queueing " + inputLength + " audio bytes at " + mAudioPresentationTimeUs +
							" microseconds (end of stream: " + endOfStream + ")");
				}
				mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, mAudioPresentationTimeUs, endOfStream ?
						MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

				long inputTime = (long) (1000000 / (sampleRate / (float) inputLength) / 2f); // chunk length, microseconds
				mAudioPresentationTimeUs += inputTime;
			}
		} catch (Throwable t) {
			Log.e(LOG_TAG, "sendAudioToEncoder exception");
			t.printStackTrace();
		}
	}

	private class TrackInfo {
		private int mIndex = 0;
		private MediaMuxerWrapper mMuxerWrapper;
	}

	private class MediaMuxerWrapper {
		private final int TOTAL_NUM_TRACKS;
		private MediaMuxer mMuxer;
		private boolean mStarted = false;

		private int mNumTracksAdded = 0;
		private int mNumTracksFinished = 0;

		private final Object mSync = new Object();

		private MediaMuxerWrapper(int format, String outputPath, boolean hasAudioTrack) {
			TOTAL_NUM_TRACKS = hasAudioTrack ? 2 : 1; // just video, or audio+video
			restart(format, outputPath);
		}

		private int addTrack(MediaFormat format) {
			mNumTracksAdded++;
			int trackIndex = mMuxer.addTrack(format);
			if (mNumTracksAdded == TOTAL_NUM_TRACKS) {
				if (VERBOSE) {
					Log.i(LOG_TAG, "All tracks added, starting muxer");
				}
				mMuxer.start();
				mStarted = true;
			}
			return trackIndex;
		}

		private void finishTrack() {
			mNumTracksFinished++;
			if (mNumTracksFinished == TOTAL_NUM_TRACKS) {
				if (VERBOSE) {
					Log.i(LOG_TAG, "All tracks finished, stopping muxer");
				}
				stop();
			}
		}

		private boolean allTracksAdded() {
			return (mNumTracksAdded == TOTAL_NUM_TRACKS);
		}

		private boolean allTracksFinished() {
			return (mNumTracksFinished == TOTAL_NUM_TRACKS);
		}

		private void stop() {
			if (mMuxer != null) {
				if (!allTracksFinished()) {
					Log.e(LOG_TAG, "Stopping muxer before all tracks have been added");
				}
				if (!mStarted) {
					Log.e(LOG_TAG, "Stopping muxer before it was started");
				}
				mMuxer.stop();
				mMuxer.release();
				mMuxer = null;
				mStarted = false;
				mNumTracksAdded = 0;
				mNumTracksFinished = 0;
			}
		}

		private void restart(int format, String outputPath) {
			stop();
			try {
				mMuxer = new MediaMuxer(outputPath, format);
			} catch (IOException e) {
				throw new RuntimeException("MediaMuxer creation failed", e);
			}
		}
	}

	/**
	 * Configures encoder and mMuxer state, and prepares the input Surface. Initialises mVideoEncoder, mAudioEncoder,
	 * mMuxerWrapper, mInputSurface, mVideoBufferInfo, mVideoTrackInfo, mAudioBufferInfo, mAudioTrackInfo.
	 * <p>
	 * An audioSampleRate value <= 0 indicates that there is no audio stream
	 */
	private void prepareEncoder(File outputFile, int videoWidth, int videoHeight, int iFrameInterval, int audioSampleRate) throws IOException {
		mVideoBufferInfo = new MediaCodec.BufferInfo();
		mVideoTrackInfo = new TrackInfo();

		// configure video output
		// failing to specify some of these properties can cause the MediaCodec configure() call to throw an unhelpful exception
		MediaFormat videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, videoWidth, videoHeight);
		//videoFormat.setInteger();
		videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
		videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
		videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);

		// create a MediaCodec encoder, and configure it with our format; then get a Surface we can use for input and wrap it
		// with a class that handles the EGL work
		mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
		mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mInputSurface = new MP4CodecInputSurface(mVideoEncoder.createInputSurface());
		mVideoEncoder.start();

		if (audioSampleRate > 0) {
			mAudioBufferInfo = new MediaCodec.BufferInfo();
			mAudioTrackInfo = new TrackInfo();

			// audio output format
			MediaFormat audioFormat = new MediaFormat();
			audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
			audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
			audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, audioSampleRate);
			audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1); // TODO: *always* mono?
			audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, (int) (audioSampleRate *
					(128000 / 44100f))); // TODO: will this always be okay (i.e., scaling bitrate based on typical 44.1kHz rate?
			audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384); // TODO: *always* 16kB?

			mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
			mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			mAudioEncoder.start();
		}

		// create a MediaMuxer to combine video and audio
		// note: we can't add the tracks and start() the muxer here, because our MediaFormat doesn't have the Magic Goodies,
		// which can only be obtained from the encoder after it has started processing data
		String outputPath = outputFile.getAbsolutePath();
		Log.i(LOG_TAG, "Setting MP4 output path to " + outputPath);
		mMuxerWrapper = new MediaMuxerWrapper(OUTPUT_FORMAT, outputPath, audioSampleRate > 0);

		mVideoTrackInfo.mIndex = -1;
		mVideoTrackInfo.mMuxerWrapper = mMuxerWrapper;

		if (audioSampleRate > 0) {
			mAudioTrackInfo.mIndex = -1;
			mAudioTrackInfo.mMuxerWrapper = mMuxerWrapper;
		}
	}

	private void initialiseDrawSurface(Resources resources, int canvasWidth, int canvasHeight, Map<Integer, Object> settings) {
		mDrawSurface = new MP4DrawSurface(resources, canvasWidth, canvasHeight, settings);
		mDrawSurface.setCoords(-1, 1, -1, -1, 1, -1, 1, 1); // TODO: use for zoom / Ken Burns?

		GLES20.glViewport(0, 0, canvasWidth, canvasHeight);

		float ratio = (float) canvasWidth / canvasHeight;
		final float[] mProjectionMatrix = new float[16];
		final float[] mViewMatrix = new float[16];

		// set camera position (projection and view matrices)
		Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7); // TODO: are near/far okay?
		Matrix.setLookAtM(mViewMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

		// combine projection and view transformation to use when drawing video frames
		Matrix.multiplyMM(mModelViewProjectionMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
	}

	/**
	 * Releases encoder objects/resources/surface
	 */
	private void releaseRecordingResources() {
		if (VERBOSE) {
			Log.d(LOG_TAG, "Releasing encoder objects");
		}
		stopAndReleaseVideoEncoder();
		stopAndReleaseAudioEncoder();
		if (mMuxerWrapper != null) {
			synchronized (mMuxerWrapper.mSync) {
				mMuxerWrapper.stop();
				mMuxerWrapper = null;
			}
		}
		if (mInputSurface != null) {
			mInputSurface.release();
			mInputSurface = null;
		}
	}

	private void stopAndReleaseVideoEncoder() {
		if (mVideoEncoder != null) {
			mVideoEncoder.stop();
			mVideoEncoder.release();
			mVideoEncoder = null;
		}
	}

	private void stopAndReleaseAudioEncoder() {
		mLastEncodedAudioTimeStamp = 0;
		if (mAudioEncoder != null) {
			mAudioEncoder.stop();
			mAudioEncoder.release();
			mAudioEncoder = null;
		}
	}

	/**
	 * Extracts all pending data from the encoder and forwards it to the Muxer.
	 * <p>
	 * If endOfStream is not set, this returns when there is no more data to drain. If it is set, we send EOS to the encoder,
	 * and then iterate until we see EOS on the output. Calling this with endOfStream set should be done once, right before
	 * stopping the Muxer.
	 */
	private void drainEncoder(MediaCodec encoder, MediaCodec.BufferInfo bufferInfo, TrackInfo trackInfo, boolean endOfStream) {
		final int TIMEOUT_USEC = 100;

		if (endOfStream && encoder == mVideoEncoder) {
			if (VERBOSE) {
				Log.d(LOG_TAG, "Sending EOS to video encoder");
			}
			encoder.signalEndOfInputStream();
		}

		MediaMuxerWrapper muxerWrapper = trackInfo.mMuxerWrapper;
		ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();

		while (true) {
			if (VERBOSE) {
				Log.d(LOG_TAG, "drain" + ((encoder == mVideoEncoder) ? "Video" : "Audio") + "Encoder(" + endOfStream + ")");
			}

			int encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
			if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) { // no output available yet
				if (!endOfStream) {
					if (VERBOSE) {
						Log.d(LOG_TAG, "Breaking as no " + ((encoder == mVideoEncoder) ? "video" : "audio") +
								" output available (aborting drain)");
					}
					break; // out of while
				} else {
					if (VERBOSE) {
						Log.d(LOG_TAG, "No " + ((encoder == mVideoEncoder) ? "video" : "audio") +
								" output available; spinning to await EOS");
					}
				}

			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) { // testing - not expected for an encoder
				encoderOutputBuffers = encoder.getOutputBuffers();

			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				// should happen before receiving buffers, and should only happen once

				if (muxerWrapper.mStarted) {
					Log.e(LOG_TAG, "Format changed after muxer start"); // TODO: can we ignore? (throw RuntimeException?)

				} else {
					MediaFormat newFormat = encoder.getOutputFormat();
					if (VERBOSE) {
						Log.d(LOG_TAG, ((encoder == mVideoEncoder) ? "Video" : "Audio") + " output format: " + newFormat);
					}

					// now that we have the Magic Goodies, start the muxer
					trackInfo.mIndex = muxerWrapper.addTrack(newFormat);
					if (!muxerWrapper.allTracksAdded()) {
						if (VERBOSE) {
							Log.d(LOG_TAG, "Breaking to wait for all encoders to send output formats");
						}
						break; // allow all encoders to send output format changed before attempting to write samples
					}
				}

			} else if (encoderStatus < 0) { // unknown status update - let's ignore it
				Log.w(LOG_TAG, "Unexpected result from " + ((encoder == mVideoEncoder) ? "video" : "audio") +
						" encoder.dequeueOutputBuffer: " + encoderStatus);

			} else {
				ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
				if (encodedData == null) {
					throw new RuntimeException(
							((encoder == mVideoEncoder) ? "Video" : "Audio") + " encoderOutputBuffer " + encoderStatus +
									" was null");
				}

				if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					// codec config data was fed to the muxer when we got INFO_OUTPUT_FORMAT_CHANGED status; ignore this time
					if (VERBOSE) {
						Log.d(LOG_TAG, "Ignoring additional " + ((encoder == mVideoEncoder) ? "video" : "audio") +
								" BUFFER_FLAG_CODEC_CONFIG");
					}
					bufferInfo.size = 0;
				}

				if (bufferInfo.size != 0) {
					if (!trackInfo.mMuxerWrapper.mStarted) {
						// TODO: this does still happen, albeit very rarely, but especially on shorter narratives - why?
						Log.e(LOG_TAG, "Muxer not started; dropping " + ((encoder == mVideoEncoder) ? "video" : "audio") + " " +
								"frames");
						// disabled for now as this happens far too often (tradeoff of slightly out of sync tracks is worth it
						// until we can find a way to ensure all tracks are started before data is received)
						// throw new RuntimeException("Frames dropped before starting muxer");
					} else {
						// adjust the ByteBuffer values to match BufferInfo
						// (only needed before v21 - see note in MediaCodec.getOutputBuffers)
						encodedData.position(bufferInfo.offset);
						encodedData.limit(bufferInfo.offset + bufferInfo.size);
						if (encoder == mAudioEncoder) {
							if (bufferInfo.presentationTimeUs < mLastEncodedAudioTimeStamp) {
								bufferInfo.presentationTimeUs = mLastEncodedAudioTimeStamp += 23219; // magical AAC encoded frame
							}
							mLastEncodedAudioTimeStamp = bufferInfo.presentationTimeUs;
						}

						if (bufferInfo.presentationTimeUs < 0) {
							Log.w(LOG_TAG, "Zero presentation time: " + bufferInfo.presentationTimeUs);
							bufferInfo.presentationTimeUs = 0;
						}
						muxerWrapper.mMuxer.writeSampleData(trackInfo.mIndex, encodedData, bufferInfo);

						if (VERBOSE) {
							Log.d(LOG_TAG, "Sent " + bufferInfo.size + ((encoder == mVideoEncoder) ? " video" : " audio") +
									" bytes to muxer with presentation time " + bufferInfo.presentationTimeUs + " " +
									"microseconds");
						}

					}
				}

				encoder.releaseOutputBuffer(encoderStatus, false);

				if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					if (!endOfStream) {
						Log.e(LOG_TAG,
								"Reached end of " + ((encoder == mVideoEncoder) ? "video" : "audio") + " stream unexpectedly");
						mEndOfOutputReached = true;
					} else {
						muxerWrapper.finishTrack();
						if (VERBOSE) {
							Log.d(LOG_TAG, "End of " + ((encoder == mVideoEncoder) ? "video" : "audio") + " stream reached");
						}
						if (encoder == mVideoEncoder) {
							Log.i(LOG_TAG, "Stopping and releasing video encoder");
							stopAndReleaseVideoEncoder();
						} else if (encoder == mAudioEncoder) {
							Log.i(LOG_TAG, "Stopping and releasing audio encoder");
							stopAndReleaseAudioEncoder();
						}
					}
					if (VERBOSE) {
						Log.d(LOG_TAG, "Breaking at end of stream");
					}
					break; // out of while
				}
			}
		}
	}
}
