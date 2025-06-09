/*
 *  Copyright (C) 2020 Simon Robinson
 *
 *  This file is part of Com-Me.
 *
 *  Com-Me is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  Com-Me is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with Com-Me.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package ac.robinson.mp4;

import android.content.res.Resources;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
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
import java.util.LinkedList;
import java.util.List;
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
 * Enormous thanks to Andrew McFadden for his MediaCodec examples!
 * Adapted from http://bigflake.com/mediacodec/CameraToMpegTest.java.txt
 * and https://github.com/OnlyInAmerica/HWEncoderExperiments/blob/master/HWEncoderExperiments/src/main/java/net/openwatch
 * /hwencoderexperiments/MP4Encoder.java
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
	private final float[] mModelViewProjectionMatrix = new float[16];

	// audio state
	private int mAudioBufferSize;
	private byte[] mAudioInputBuffer;
	private long mLastEncodedAudioTimeStamp = 0;
	private long mAudioPresentationTimeUs = 0;

	// recording state
	private boolean mAudioEnded;
	private boolean mEndOfOutputReached;

	// latest SDK requirements - see: https://source.android.com/compatibility/android-cdd.pdf section 5.2
	// SDK 21: https://source.android.com/compatibility/5.1/android-5.1-cdd.pdf (same as most recent, v31)
	// SDK 18 (our minimum): https://source.android.com/compatibility/4.3/android-4.3-cdd.pdf
	// see also: https://developer.android.com/guide/topics/media/media-formats#video-encoding
	private static final Point[] SAFE_VIDEO_RESOLUTIONS = new Point[]{
			new Point(1920, 1080), // [ currently not used ]
			new Point(1280, 720),  // our 'high' (device support recommended, but not required until recent SDK levels)
			new Point(1024, 576),  // our 'medium' - not detailed in spec, but often supported
			new Point(720, 480),   // our 'medium' (required for H.264/H.265 only)
			new Point(640, 360),   // our 'low' (required for VP8/VP9 only)
			new Point(480, 360)    // our 'low' (recommended on SDK v18-v20 only; H.264)
	};

	public boolean createMP4(Resources resources, File outputFile, ArrayList<FrameMediaContainer> videoFrames,
							 AudioUtilities.CombinedAudioTrack combinedAudioTrack, Map<Integer, Object> settings) {

		if (videoFrames == null || videoFrames.isEmpty()) {
			return false;
		}

		// should really do proper checking on these
		final int requestedOutputWidth = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_WIDTH);
		final int requestedOutputHeight = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_HEIGHT);

		boolean hasAudio = combinedAudioTrack.mCombinedPCMFile != null;
		int audioSampleRate = hasAudio ? (int) combinedAudioTrack.mCombinedPCMAudioFormat.getSampleRate() : 0;
		AudioInputStream audioInputStream = null;

		long totalDuration = 0;
		for (FrameMediaContainer frame : videoFrames) {
			totalDuration += frame.mFrameMaxDuration; // better fit I frames to image changes
		}
		int iFrameInterval = (int) ((totalDuration / videoFrames.size()) / 1000f);

		Log.d(LOG_TAG, "Creating " + VIDEO_MIME_TYPE + "+" + AUDIO_MIME_TYPE + " output, " + requestedOutputWidth + "x" +
				requestedOutputHeight + " at " + FRAME_RATE + " fps, " + BIT_RATE + "bps, i-frame interval " + iFrameInterval +
				", " + (hasAudio ? "with" : "no") + " audio");

		// in prepareEncoder we have two options to get an appropriate video encoder: createEncoderByType or findEncoderForFormat
		// however, these are not equivalent, and give different results on the same device - for example, findEncoderForFormat
		// can claim that a device doesn't support a particular resolution, but the same encoder sourced via createEncoderByType
		// may well succeed in creating the video regardless; so, we try both options, preferring createEncoderByType and
		// falling back to findEncoderForFormat, and further trying a selection of 'safe' resolutions (i.e., video resizing)
		// note: this can lead to the strange situation where choosing 'medium' quality for export leads to a higher actual
		// export resolution than 'high' if the medium quality option succeeds via createEncoderByType but the high quality
		// option fails here and only succeeds via findEncoderForFormat
		// TODO: handle this inconsistency by trying our own resolutions in descending order before using findEncoderForFormat?
		boolean allowResizingVideo = settings.containsKey(MediaUtilities.KEY_RESIZE_VIDEO);
		ArrayList<FrameMediaContainer> originalVideoFrames = new ArrayList<>(videoFrames); // need a copy as we remove() on use

		try {
			// initialise - throws IOException if either audio or video encoders couldn't be created
			Point actualOutputSize = prepareEncoder(outputFile, requestedOutputWidth, requestedOutputHeight, iFrameInterval,
					audioSampleRate, allowResizingVideo);
			if (actualOutputSize.x != requestedOutputWidth || actualOutputSize.y != requestedOutputHeight) {
				Log.d(LOG_TAG, "Unable to create video at requested size " + requestedOutputWidth + "x" + requestedOutputHeight +
						"; setting " + "size to " + actualOutputSize.x + "x" + actualOutputSize.y);
			}

			// set up video output
			mInputSurface.makeCurrent();
			initialiseDrawSurface(resources, actualOutputSize.x, actualOutputSize.y, settings);

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
						combinedAudioTrack.mCombinedPCMAudioFormat, combinedAudioTrack.mCombinedPCMFile.length() / 2);
			}

			long startTime = System.nanoTime();
			int videoFrameCount = 0;
			long videoPresentationTimeNs = 0;
			long videoPresentationTimeIncrementNs = (long) (1000000000L / (float) FRAME_RATE);

			FrameMediaContainer currentNarrativeFrame;
			do {
				currentNarrativeFrame = videoFrames.remove(0);
			} while (!videoFrames.isEmpty() && currentNarrativeFrame.mFrameMaxDuration <= 0); // skip zero-length items
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
					if (!videoFrames.isEmpty()) {
						currentNarrativeFrame = videoFrames.remove(0);
						if (currentNarrativeFrame.mFrameMaxDuration > 0) {
							videoPresentationTimeNs = currentFrameEndNs;
							currentFrameEndNs += currentNarrativeFrame.mFrameMaxDuration * 1000000L;
							if (VERBOSE) {
								Log.d(LOG_TAG,
										"New video time: " + videoPresentationTimeNs + "; frame end time " + currentFrameEndNs);
							}
						} else {
							if (VERBOSE) {
								Log.d(LOG_TAG, "End of video output reached (empty frame)");
							}
							mEndOfOutputReached = true;
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
						if (VERBOSE) {
							Log.d(LOG_TAG, "Setting last video frame's presentation time to " +
									(mAudioPresentationTimeUs * 1000));
						}
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
			if (settings.containsKey(MediaUtilities.KEY_RESIZE_VIDEO)) {
				Log.e(LOG_TAG, "MP4 encoding loop exception - aborting");
				return false; // we've already tried resizing once; just give up
			}

			// as outlined above, we allow resizing of the video output on our second attempt to try to ensure creation succeeds
			Log.e(LOG_TAG, "MP4 encoding loop exception - retrying with video resizing enabled");
			IOUtilities.closeStream(audioInputStream);
			releaseRecordingResources();
			settings.put(MediaUtilities.KEY_RESIZE_VIDEO, true);
			return createMP4(resources, outputFile, originalVideoFrames, combinedAudioTrack, settings);

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
				mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, mAudioPresentationTimeUs,
						endOfStream ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

				long inputTime = (long) (1000000 / (sampleRate / (float) inputLength) / 2f); // chunk length, microseconds
				mAudioPresentationTimeUs += inputTime;
			}
		} catch (Throwable t) {
			Log.e(LOG_TAG, "sendAudioToEncoder exception");
			t.printStackTrace();
		}
	}

	private static class TrackInfo {
		private int mIndex = 0;
		private MediaMuxerWrapper mMuxerWrapper;
		private final List<PendingSample> pendingSamples = new LinkedList<>();  // buffer for samples before muxer start
	}

	private static class PendingSample {
		ByteBuffer data;
		MediaCodec.BufferInfo info;
		PendingSample(ByteBuffer data, MediaCodec.BufferInfo info) {
			this.data = data;
			this.info = info;
		}
	}

	private static class MediaMuxerWrapper {
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
				Log.i(LOG_TAG, "All " + mNumTracksAdded + " tracks added, starting muxer");
				mMuxer.start();
				mStarted = true;
			}
			return trackIndex;
		}

		private void finishTrack() {
			mNumTracksFinished++;
			if (mNumTracksFinished == TOTAL_NUM_TRACKS) {
				Log.i(LOG_TAG, "All" + mNumTracksAdded + "tracks finished, stopping muxer");
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
				try {
					mMuxer.stop();
				} catch (IllegalStateException ignored) {
				}
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
	private Point prepareEncoder(File outputFile, int videoWidth, int videoHeight, int iFrameInterval, int audioSampleRate,
								 boolean resizeVideo) throws IOException {
		mVideoBufferInfo = new MediaCodec.BufferInfo();
		mVideoTrackInfo = new TrackInfo();

		// configure video output
		// failing to specify some of these properties can cause the MediaCodec configure() call to throw an unhelpful exception
		MediaFormat videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, videoWidth, videoHeight);
		videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE); // TODO: probably should be calculated for variable w/h
		videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
		videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);

		int resizedWidth = videoWidth;
		int resizedHeight = videoHeight;

		// create a MediaCodec encoder, and configure it with our format; then get a Surface we can use for input and wrap it
		// with a class that handles the EGL work
		if (!resizeVideo) {
			mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);

		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			// on a second attempt at video creation we do some (limited) validation of the requested video width/height and
			// check whether the encoder claims to support the given resolution, sticking to some known safe values
			if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
				// see documentation for findEncoderForFormat(android.media.MediaFormat) - SDK v21 cannot contain a frame rate
				videoFormat.setString(MediaFormat.KEY_FRAME_RATE, null);
			}

			String selectedEncoder = null;
			MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
			for (int i = 0; i < SAFE_VIDEO_RESOLUTIONS.length; i++) {
				// we allow flexible sizing of the exported video's smaller dimension to allow it to exactly match the device's
				// photo sizes; here we assume that if encoding of a given width is okay then the height used can be variable;
				// and, if a given size is okay in landscape then it is okay in portrait (both potentially big ifs...)
				int maxDimension = Math.max(resizedWidth, resizedHeight);
				if (maxDimension == SAFE_VIDEO_RESOLUTIONS[i].x) {
					videoFormat.setInteger(MediaFormat.KEY_WIDTH, resizedWidth);
					videoFormat.setInteger(MediaFormat.KEY_HEIGHT, resizedHeight);

					selectedEncoder = mediaCodecList.findEncoderForFormat(videoFormat);
					if (selectedEncoder != null) {
						break;
					}

					if (i + 1 < SAFE_VIDEO_RESOLUTIONS.length) {
						float scaleFactor = maxDimension / (float) SAFE_VIDEO_RESOLUTIONS[i + 1].x;
						resizedWidth = Math.round(resizedWidth / scaleFactor);
						resizedHeight = Math.round(resizedHeight / scaleFactor);
					}
				}
			}

			if (selectedEncoder != null) {
				mVideoEncoder = MediaCodec.createByCodecName(selectedEncoder);
			} else {
				throw new IOException("Video format unsupported");
			}

			if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
				videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE); // restore original frame rate
			}
		} else {
			// can't query supported formats pre-v21 - could somehow do retry-on-error, but probably not worth it for old SDKs?
			throw new IOException("Video format unsupported");
		}

		mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mInputSurface = new MP4CodecInputSurface(mVideoEncoder.createInputSurface());
		mVideoEncoder.start();

		if (audioSampleRate > 0) {
			// TODO: check support in a similar way to video encoding resolutions?
			mAudioBufferInfo = new MediaCodec.BufferInfo();
			mAudioTrackInfo = new TrackInfo();

			// audio output format
			MediaFormat audioFormat = new MediaFormat();
			audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
			audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
			audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, audioSampleRate);
			audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1); // TODO: currently always mono; add stereo?
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

		return new Point(resizedWidth, resizedHeight);
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
			try {
				mVideoEncoder.stop();
			} catch (IllegalStateException ignored) {
			}
			mVideoEncoder.release();
			mVideoEncoder = null;
		}
	}

	private void stopAndReleaseAudioEncoder() {
		mLastEncodedAudioTimeStamp = 0;
		if (mAudioEncoder != null) {
			try {
				mAudioEncoder.stop();
			} catch (IllegalStateException ignored) {
			}
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
				Log.d(LOG_TAG, "Drain" + ((encoder == mVideoEncoder) ? "Video" : "Audio") + "Encoder(" + endOfStream + ")");
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
					Log.d(LOG_TAG, ((encoder == mVideoEncoder) ? "Video" : "Audio") + " output format: " + newFormat);

					// now that we have the Magic Goodies, start the muxer
					trackInfo.mIndex = muxerWrapper.addTrack(newFormat);
					if (!muxerWrapper.allTracksAdded()) {
						Log.d(LOG_TAG, "Breaking to wait for all encoders to send output formats");
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
					MediaFormat newFormat = encoder.getOutputFormat();
					Log.d(LOG_TAG, "Ignoring additional " + ((encoder == mVideoEncoder) ? "video" : "audio") +
								" BUFFER_FLAG_CODEC_CONFIG with format " + newFormat);
					bufferInfo.size = 0;
				}

				if (bufferInfo.size != 0) {
					if (!trackInfo.mMuxerWrapper.mStarted) {
						Log.d(LOG_TAG, "Muxer not started; caching " + bufferInfo.size + " pending " +
								((encoder == mVideoEncoder) ? "video" : "audio") + " frames");
						ByteBuffer pendingData = ByteBuffer.allocate(bufferInfo.size);
						pendingData.put(encodedData);
						MediaCodec.BufferInfo pendingInfo = new MediaCodec.BufferInfo();
						pendingInfo.set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags);
						trackInfo.pendingSamples.add(new PendingSample(pendingData, pendingInfo));

					} else {
						// write any pending samples first
						while (!trackInfo.pendingSamples.isEmpty()) {
							PendingSample sample = trackInfo.pendingSamples.remove(0);
							sample.data.position(0);
							muxerWrapper.mMuxer.writeSampleData(trackInfo.mIndex, sample.data, sample.info);
						}

						// adjust the ByteBuffer values to match BufferInfo
						// (only needed before v21 - see note in MediaCodec.getOutputBuffers)
						encodedData.position(bufferInfo.offset);
						encodedData.limit(bufferInfo.offset + bufferInfo.size);
						if (encoder == mAudioEncoder) {
							if (bufferInfo.presentationTimeUs < mLastEncodedAudioTimeStamp) {
								bufferInfo.presentationTimeUs = mLastEncodedAudioTimeStamp + 23219; // magical AAC encoded frame
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
						Log.d(LOG_TAG, "End of " + ((encoder == mVideoEncoder) ? "video" : "audio") + " stream reached");
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
