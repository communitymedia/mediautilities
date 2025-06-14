/*
 *  Copyright (C) 2012 Simon Robinson
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

package ac.robinson.mediautilities;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.bric.audio.AudioFormat;
import com.bric.audio.AudioInputStream;
import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Map;

import ac.robinson.mov.JPEGMovWriter;
import ac.robinson.mov.MP3toPCMConverter;
import ac.robinson.mov.MP3toPCMConverter.MP3Configuration;
import ac.robinson.mov.MP4toPCMConverter;
import ac.robinson.mov.WAVtoPCMConverter;
import ac.robinson.mov.WAVtoPCMConverter.WAVConfiguration;
import ac.robinson.util.AndroidUtilities;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.IOUtilities;
import ac.robinson.util.ImageCacheUtilities;

// mov export: http://java.net/projects/javagraphics/sources/svn/show/trunk/src/com/bric
// m4a import: http://jaadec.sourceforge.net/ - for an alternative, see: http://www.randelshofer.ch/monte/index.html
public class MOVUtilities {

	private static final String LOG_TAG = "MOVUtilities";

	public static ArrayList<Uri> generateNarrativeMOV(Resources res, File outputFile, ArrayList<FrameMediaContainer> framesToSend,
													  Map<Integer, Object> settings) {

		ArrayList<Uri> filesToSend = new ArrayList<>();
		if (framesToSend == null || framesToSend.isEmpty()) {
			return filesToSend;
		}
		boolean fileError = false;

		// should really do proper checking on these
		final int outputWidth = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_WIDTH);
		final int outputHeight = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_HEIGHT);
		final int imageQuality = (Integer) settings.get(MediaUtilities.KEY_IMAGE_QUALITY);

		final int backgroundColour = (Integer) settings.get(MediaUtilities.KEY_BACKGROUND_COLOUR);
		final int textColourWithImage = (Integer) settings.get(MediaUtilities.KEY_TEXT_COLOUR_WITH_IMAGE);
		final int textColourNoImage = (Integer) settings.get(MediaUtilities.KEY_TEXT_COLOUR_NO_IMAGE);
		final int textBackgroundColour = (Integer) settings.get(MediaUtilities.KEY_TEXT_BACKGROUND_COLOUR);

		final boolean textBackgroundSpanWidth = (Boolean) settings.get(MediaUtilities.KEY_TEXT_BACKGROUND_SPAN_WIDTH);
		final int textMaxFontSize = (Integer) settings.get(MediaUtilities.KEY_MAX_TEXT_FONT_SIZE);
		final int textMaxPercentageHeightWithImage = (Integer) settings.get(
				MediaUtilities.KEY_MAX_TEXT_PERCENTAGE_HEIGHT_WITH_IMAGE);
		final int textSpacing = (Integer) settings.get(MediaUtilities.KEY_TEXT_SPACING);
		final int textCornerRadius = (Integer) settings.get(MediaUtilities.KEY_TEXT_CORNER_RADIUS);

		final String typefacePath = (String) settings.get(MediaUtilities.KEY_TEXT_FONT_PATH);
		Typeface textTypeface = null;
		if (!TextUtils.isEmpty(typefacePath)) {
			File fontFile = new File(typefacePath);
			textTypeface = fontFile.exists() ? Typeface.createFromFile(fontFile) : null;
		}

		final int audioIconResourceId = (Integer) settings.get(MediaUtilities.KEY_AUDIO_RESOURCE_ID);
		final int audioResamplingRate = (Integer) settings.get(MediaUtilities.KEY_RESAMPLE_AUDIO);

		// all frames *must* be the same dimensions, so we work from a base bitmap for everything
		Bitmap baseBitmap = Bitmap.createBitmap(outputWidth, outputHeight,
				ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		Canvas baseCanvas = new Canvas(baseBitmap);
		Paint basePaint = BitmapUtilities.getPaint(textColourNoImage, 1);

		int audioBitmapSize = Math.min(outputWidth, outputHeight);
		int audioBitmapLeft = Math.round((outputWidth - audioBitmapSize) / 2f);
		int audioBitmapTop = Math.round((outputHeight - audioBitmapSize) / 2f);

		Bitmap imageBitmap;
		SVG audioSVG = null;
		JPEGMovWriter outputFileWriter = null;
		ArrayList<File> filesToDelete = new ArrayList<>();
		try {
			outputFileWriter = new JPEGMovWriter(outputFile);

			// find all the story audio - *all* audio must be added before any frames (takes a *long* time)
			// resampling audio is slowest, but most compatible with external players other than QuickTime
			// segmented audio means we combine audio into as few tracks as possible, which increases playback
			// compatibility (but also increases the time required to export the movie)
			// individual tracks is similar in speed to segmented tracks, but typically only works with QuickTime
			// Player (hence this is not a user editable preference)
			if (audioResamplingRate != 0) {
				AudioUtilities.CombinedAudioTrack resampledAudioTrack = AudioUtilities.createCombinedNarrativeAudioTrack(
						framesToSend, audioResamplingRate, outputFile.getParentFile());

				if (resampledAudioTrack.mCombinedPCMFile != null) {
					AudioInputStream pcmAudioStream;
					try {
						// output from converters and/or SSRC is mono signed 16-bit little-endian integers
						pcmAudioStream = new AudioInputStream(new FileInputStream(resampledAudioTrack.mCombinedPCMFile),
								resampledAudioTrack.mCombinedPCMAudioFormat,
								(long) (resampledAudioTrack.mCombinedPCMAudioFormat.getFrameRate() *
										(resampledAudioTrack.mCombinedPCMDurationMs / 1000f)));

						// new source: pumpernickel/pump-quicktime/src/main/java/com/pump/animation/quicktime/MovWriter.java
						outputFileWriter.addAudioTrack(pcmAudioStream, 0, resampledAudioTrack.mCombinedPCMDurationMs / 1000f);

					} catch (Exception e) {
						Log.d(LOG_TAG, "Error creating combined MOV audio track - couldn't create final MOV track");
					}
				}

				filesToDelete.addAll(resampledAudioTrack.mTemporaryFilesToDelete);

			} else {
				if (MediaUtilities.MOV_USE_SEGMENTED_AUDIO) {
					ArrayList<File> segmentFiles = addNarrativeAudioAsSegmentedTrack(framesToSend, outputFile.getParentFile(),
							outputFileWriter);
					filesToDelete.addAll(segmentFiles);
				} else {
					ArrayList<File> individualFiles = addNarrativeAudioAsIndividualTracks(framesToSend,
							outputFile.getParentFile(),
							outputFileWriter);
					filesToDelete.addAll(individualFiles);
				}
			}

			// add the visual content
			boolean imageLoaded;
			int imageBitmapLeft;
			int imageBitmapTop;
			for (FrameMediaContainer frame : framesToSend) {

				if (frame.mFrameMaxDuration <= 0) {
					continue;
				}

				imageLoaded = false;
				baseCanvas.drawColor(frame.mBackgroundColour < 0 ? frame.mBackgroundColour : backgroundColour);

				if (frame.mImagePath != null) {
					// scale image size to make sure it is small enough to fit in the container
					imageBitmap = BitmapUtilities.loadAndCreateScaledBitmap(frame.mImagePath, outputWidth, outputHeight,
							BitmapUtilities.ScalingLogic.FIT, true);

					if (imageBitmap != null) {
						imageBitmapLeft = Math.round((outputWidth - imageBitmap.getWidth()) / 2f);
						imageBitmapTop = Math.round((outputHeight - imageBitmap.getHeight()) / 2f);
						baseCanvas.drawBitmap(imageBitmap, imageBitmapLeft, imageBitmapTop, basePaint);

						imageLoaded = true;
					}
				}

				if (!TextUtils.isEmpty(frame.mTextContent)) {
					int textColour = frame.mForegroundColour < 0 ? frame.mForegroundColour :
							(imageLoaded ? textColourWithImage : textColourNoImage);
					int textMaxHeightWithImage = (outputHeight * textMaxPercentageHeightWithImage / 100);
					int textHeight =
							textMaxHeightWithImage <= 0 ? outputHeight : (imageLoaded ? textMaxHeightWithImage : outputHeight);
					BitmapUtilities.drawScaledText(frame.mTextContent, baseCanvas, basePaint, textColour,
							(imageLoaded ? textBackgroundColour : 0), textSpacing, textCornerRadius, imageLoaded, 0,
							textBackgroundSpanWidth, textHeight, textMaxFontSize, textTypeface);

				} else if (!imageLoaded) {
					// quicker to do this than load the SVG for narratives that have no audio
					if (audioIconResourceId != AndroidUtilities.NO_RESOURCE && audioSVG == null) {
						audioSVG = SVGParser.getSVGFromResource(res, audioIconResourceId); // only load icon if one is specified
					}
					if (audioSVG != null) {
						// we can't use PNG compression reliably in the MOV file, so convert to JPEG
						baseCanvas.drawPicture(audioSVG.getPicture(),
								new RectF(audioBitmapLeft, audioBitmapTop, audioBitmapLeft + audioBitmapSize,
										audioBitmapTop + audioBitmapSize));
					}
				}

				outputFileWriter.addFrame(frame.mFrameMaxDuration / 1000f, baseBitmap, imageQuality);
			}
		} catch (IOException e) {
			fileError = true; // these are the only places where errors really matter
			Log.d(LOG_TAG, "Error creating MOV file - IOException: " + e.getLocalizedMessage());
		} catch (Throwable t) {
			fileError = true; // these are the only places where errors really matter
			Log.d(LOG_TAG, "Error creating MOV file - Throwable: " + t.getLocalizedMessage());
		} finally {
			if (outputFileWriter != null) {
				try {
					outputFileWriter.close(!fileError);
				} catch (Exception ignored) {
				}
			}
		}

		// deletion must be *after* creation because audio and video are interleaved in the MOV output
		for (File file : filesToDelete) {
			if (file != null && file.exists()) {
				Log.d(LOG_TAG, "Deleting temporary file " + file.getAbsolutePath());
				file.delete();
			}
		}

		if (!fileError) {
			filesToSend.add(Uri.fromFile(outputFile));
			return filesToSend;
		}

		//noinspection RedundantOperationOnEmptyContainer
		filesToSend.clear();
		return filesToSend;
	}

	private static ArrayList<File> addNarrativeAudioAsIndividualTracks(ArrayList<FrameMediaContainer> framesToSend,
																	   File tempDirectory, JPEGMovWriter outputFileWriter) {

		Log.d(LOG_TAG, "Exporting individual track MOV audio");

		// the list of files to be deleted after they've been written to the movie
		ArrayList<File> filesToDelete = new ArrayList<>();

		int frameDuration;
		long frameStartTime = 0;
		for (FrameMediaContainer frame : framesToSend) {

			// we need these values in seconds, but store in milliseconds so we don't round incorrectly later
			frameDuration = frame.mFrameMaxDuration;
			int audioDuration;

			int audioId = -1;
			for (String audioPath : frame.mAudioPaths) {
				audioId += 1;

				// don't need to add inherited spanning audio items - they've already been processed
				if (frame.mSpanningAudioIndex == audioId && !frame.mSpanningAudioRoot) {
					continue;
				}

				String audioFileExtension = IOUtilities.getFileExtension(audioPath);
				if (!AndroidUtilities.arrayContains(MediaUtilities.MOV_AUDIO_FILE_EXTENSIONS, audioFileExtension)) {
					continue;
				}

				audioDuration = frame.mAudioDurations.get(audioId);

				File inputAudioFile = new File(audioPath);
				File outputPCMFile;
				BufferedOutputStream outputPCMStream = null;
				AudioInputStream pcmAudioStream;
				AudioFormat audioFormat = null;
				boolean decodingError;

				try {
					// all methods need a PCM file to write to
					outputPCMFile = File.createTempFile(inputAudioFile.getName(), ".pcm", tempDirectory);
					filesToDelete.add(outputPCMFile);
					outputPCMStream = new BufferedOutputStream(new FileOutputStream(outputPCMFile));

					decodingError = false;
					if (AndroidUtilities.arrayContains(MediaUtilities.M4A_FILE_EXTENSIONS, audioFileExtension)) {
						RandomAccessFile inputRandomAccessFile = null;
						try {

							// first we need to extract PCM audio from the M4A file
							inputRandomAccessFile = new RandomAccessFile(inputAudioFile, "r");
							MP4toPCMConverter pcmConverter = new MP4toPCMConverter(inputRandomAccessFile);
							pcmConverter.convertFile(outputPCMStream, true);

							// get the format of the audio - PCM output is mono signed little-endian integers
							audioFormat = new AudioFormat(pcmConverter.getSampleRate(), pcmConverter.getSampleSize(), 1, true,
									false);
							Log.d(LOG_TAG,
									"Outputting M4A: " + pcmConverter.getSampleRate() + ", " + pcmConverter.getSampleSize() +
											", 1, signed, little endian");

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual M4A audio track - IOException: " + e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual M4A audio track - general " + "Exception");
						} finally {
							IOUtilities.closeStream(inputRandomAccessFile);
						}

					} else if (AndroidUtilities.arrayContains(MediaUtilities.MP3_FILE_EXTENSIONS, audioFileExtension)) {
						try {

							// first we need to extract PCM audio from the MP3 file
							MP3Configuration mp3Config = new MP3Configuration();
							MP3toPCMConverter.convertFile(inputAudioFile, outputPCMStream, mp3Config);

							// get the format of the audio - PCM output is mono signed 16-bit little-endian integers
							audioFormat = new AudioFormat(mp3Config.sampleFrequency, mp3Config.sampleSize,
									mp3Config.numberOfChannels, true, false);
							Log.d(LOG_TAG, "Outputting MP3: " + mp3Config.sampleFrequency + ", " + mp3Config.sampleSize + ", " +
									mp3Config.numberOfChannels + ", signed, little endian");

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual MP3 audio track - IOException: " + e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual MP3 audio track - general " + "Exception");
						}

					} else if (AndroidUtilities.arrayContains(MediaUtilities.WAV_FILE_EXTENSIONS, audioFileExtension)) {
						try {

							// first we need to extract PCM audio from the WAV file
							WAVConfiguration wavConfig = new WAVConfiguration();
							WAVtoPCMConverter.convertFile(inputAudioFile, outputPCMStream, wavConfig);

							// get the format of the audio - output is mono signed little-endian integers
							audioFormat = new AudioFormat(wavConfig.sampleFrequency, wavConfig.sampleSize,
									wavConfig.numberOfChannels, true, false);
							Log.d(LOG_TAG, "Outputting WAV: " + wavConfig.sampleFrequency + ", " + wavConfig.sampleSize + ", " +
									wavConfig.numberOfChannels + ", signed, little endian");

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual WAV audio track - IOException: " + e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual WAV audio track - general Exception");
						}
					}

					// then add to the MOV output file if successful (pcmAudioStream is closed in MovWriter)
					if (!decodingError && audioFormat != null) {
						pcmAudioStream = new AudioInputStream(new FileInputStream(outputPCMFile), audioFormat,
								(long) (audioFormat.getFrameRate() * (audioDuration / 1000f)));
						outputFileWriter.addAudioTrack(pcmAudioStream, frameStartTime / 1000f,
								(frameStartTime + audioDuration) / 1000f);
					}

				} catch (Exception e) {
					Log.d(LOG_TAG, "Error creating individual MOV audio track - general Exception");
				} finally {
					IOUtilities.closeStream(outputPCMStream);
				}
			}

			// go to the next frame regardless
			frameStartTime += frameDuration;
		}

		return filesToDelete;
	}

	private static ArrayList<File> addNarrativeAudioAsSegmentedTrack(ArrayList<FrameMediaContainer> framesToSend,
																	 File tempDirectory, JPEGMovWriter outputFileWriter) {

		Log.d(LOG_TAG, "Exporting segmented MOV audio");

		// the list of files to be deleted after they've been written to the movie
		ArrayList<File> filesToDelete = new ArrayList<>();

		// see how many tracks we need to create - one per stream, but need to separate formats
		ArrayList<String> fileTypes = new ArrayList<>();
		ArrayList<Integer> fileCounts = new ArrayList<>();
		ArrayList<String> frameTypes = new ArrayList<>();
		ArrayList<Integer> frameCounts = new ArrayList<>();
		for (FrameMediaContainer frame : framesToSend) {
			// get the frame maximums
			frameTypes.clear();
			frameCounts.clear();
			for (String path : frame.mAudioPaths) {
				String actualFileExtension = IOUtilities.getFileExtension(path);
				final String fileExtension; // use the base file extension instead of the actual - combine same types
				if (AndroidUtilities.arrayContains(MediaUtilities.M4A_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.M4A_FILE_EXTENSIONS[0];
				} else if (AndroidUtilities.arrayContains(MediaUtilities.MP3_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.MP3_FILE_EXTENSIONS[0];
				} else if (AndroidUtilities.arrayContains(MediaUtilities.WAV_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.WAV_FILE_EXTENSIONS[0];
				} else {
					fileExtension = actualFileExtension;
				}
				int position = frameTypes.indexOf(fileExtension);
				if (position >= 0) {
					frameCounts.set(position, frameCounts.get(position) + 1);
				} else {
					frameTypes.add(fileExtension);
					frameCounts.add(1);
				}
			}

			// transfer to file maximums
			for (int i = 0, n = frameTypes.size(); i < n; i++) {
				final String fileExtension = frameTypes.get(i);
				int position = fileTypes.indexOf(fileExtension);
				if (position >= 0) {
					fileCounts.set(position, Math.max(fileCounts.get(position), frameCounts.get(i)));
				} else {
					fileTypes.add(fileExtension);
					fileCounts.add(frameCounts.get(i));
				}
			}
		}

		// check how many tracks we'll need to create, and create a list TODO: order by priority (e.g., MP3 before M4A)
		ArrayList<String> fileTracks = new ArrayList<>();
		for (int i = 0, n = fileCounts.size(); i < n; i++) {
			String type = fileTypes.get(i);
			if (!AndroidUtilities.arrayContains(MediaUtilities.MOV_AUDIO_FILE_EXTENSIONS, type)) {
				break; // don't add types we can't parse
			}
			for (int t = 0, nt = fileCounts.get(i); t < nt; t++) {
				fileTracks.add(type);
			}
		}

		// add the separate track types
		for (String currentTrackType : fileTracks) {
			File inputAudioFile;
			File outputPCMFile = null;
			BufferedOutputStream outputPCMStream = null;
			boolean audioWritten = false;
			File currentPCMFile = null;
			BufferedOutputStream currentPCMStream = null;
			AudioFormat audioFormat = null;

			long audioTotalDuration = 0;
			ArrayList<Float> audioOffsetsList = new ArrayList<>();
			ArrayList<Float> audioStartsList = new ArrayList<>();
			ArrayList<Float> audioLengthsList = new ArrayList<>();

			// get the available tracks of the right type from each frame, then remove when done
			long frameStartTime = 0;
			for (FrameMediaContainer frame : framesToSend) {

				boolean audioFound = false;
				boolean decodingError = false;
				AudioUtilities.AudioType currentAudioType = AudioUtilities.AudioType.NONE;

				int audioId = -1;
				for (String audioPath : frame.mAudioPaths) {
					audioId += 1;

					// don't need to add inherited spanning audio items - they've already been processed
					if (frame.mSpanningAudioIndex == audioId && !frame.mSpanningAudioRoot) {
						frame.mSpanningAudioIndex = -1; // this frame no longer has any spanning audio
						audioFound = true;
						break; // no more items in this frame can be added, as the spanning item overlaps them
					}

					String audioFileExtension = IOUtilities.getFileExtension(audioPath);
					if (!AndroidUtilities.arrayContains(MediaUtilities.MOV_AUDIO_FILE_EXTENSIONS, audioFileExtension)) {
						continue;
					}

					// only use tracks of the right extension TODO: pick the longest track instead of the first one?
					if (AndroidUtilities.arrayContains(MediaUtilities.M4A_FILE_EXTENSIONS, audioFileExtension) &&
							currentTrackType.equals(MediaUtilities.M4A_FILE_EXTENSIONS[0])) {
						currentAudioType = AudioUtilities.AudioType.M4A;
					} else if (AndroidUtilities.arrayContains(MediaUtilities.MP3_FILE_EXTENSIONS, audioFileExtension) &&
							currentTrackType.equals(MediaUtilities.MP3_FILE_EXTENSIONS[0])) {
						currentAudioType = AudioUtilities.AudioType.MP3;
					} else if (AndroidUtilities.arrayContains(MediaUtilities.WAV_FILE_EXTENSIONS, audioFileExtension) &&
							currentTrackType.equals(MediaUtilities.WAV_FILE_EXTENSIONS[0])) {
						currentAudioType = AudioUtilities.AudioType.WAV;
					} else {
						continue;
					}

					// if we get here it's the right type of audio, so begin to get pcm from the compressed source
					inputAudioFile = new File(audioPath);
					audioFound = true;

					// we combine all tracks to one pcm file
					if (outputPCMFile == null) {
						try {
							// only create one master file, shared between all audio files of the right type
							outputPCMFile = File.createTempFile(inputAudioFile.getName(), "all.pcm", tempDirectory);
							filesToDelete.add(outputPCMFile); // deleted after the rest of the movie has been written
							outputPCMStream = new BufferedOutputStream(new FileOutputStream(outputPCMFile));
						} catch (Exception e) {
							IOUtilities.closeStream(outputPCMStream);
							if (outputPCMFile != null) {
								outputPCMFile.delete();
							}
							outputPCMFile = null;
							Log.d(LOG_TAG, "Error creating segmented MOV audio track - couldn't create base " + currentTrackType +
									" file");
							continue;
						}
					}

					// ...but do it per-track, so that one corrupt track doesn't break everything
					try {
						currentPCMFile = File.createTempFile(inputAudioFile.getName(), ".pcm", tempDirectory);
						currentPCMStream = new BufferedOutputStream(new FileOutputStream(currentPCMFile));
					} catch (Exception e) {
						IOUtilities.closeStream(currentPCMStream);
						if (currentPCMFile != null) {
							currentPCMFile.delete();
						}
						Log.d(LOG_TAG,
								"Error creating segmented MOV audio track - couldn't create individual " + currentTrackType +
										" file");
						continue;
					}

					// begin to convert the compressed audio
					// TODO: we assume that if we load, say, one MP3 at 44100Hz Joint-Stereo VBR then any other MP3s
					// TODO: will be in the same format - i.e., no resampling or other adjustments are taken. At the
					// TODO: moment, this means that loading multiple tracks of different formats leads to the first
					// TODO: one being output correctly, and others being, e.g., slowed down or sped up.
					if (currentAudioType == AudioUtilities.AudioType.M4A) {
						RandomAccessFile inputRandomAccessFile = null;
						try {

							// first we need to extract PCM audio from the M4A file
							inputRandomAccessFile = new RandomAccessFile(inputAudioFile, "r");
							MP4toPCMConverter pcmConverter = new MP4toPCMConverter(inputRandomAccessFile);
							pcmConverter.convertFile(currentPCMStream, true);

							// get the format - output from PCM converter is mono signed little-endian integers
							if (audioFormat == null) { // TODO: we assume all MP4 components are the same
								// format
								audioFormat = new AudioFormat(pcmConverter.getSampleRate(), pcmConverter.getSampleSize(), 1, true,
										false);
								Log.d(LOG_TAG,
										"Outputting M4A: " + pcmConverter.getSampleRate() + ", " + pcmConverter.getSampleSize() +
												", 1, signed, little endian");
							}

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented M4A audio track - IOException: " + e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented M4A audio track - general Exception");
						} finally {
							IOUtilities.closeStream(inputRandomAccessFile);
						}

					} else if (currentAudioType == AudioUtilities.AudioType.MP3) {
						try {

							// first we need to extract PCM audio from the MP3 file
							MP3Configuration mp3Config = new MP3Configuration();
							MP3toPCMConverter.convertFile(inputAudioFile, currentPCMStream, mp3Config);

							// get the format - output from PCM converter is mono signed 16-bit little-endian integers
							if (audioFormat == null) { // TODO: we assume all MP3 components are the same
								// format
								audioFormat = new AudioFormat(mp3Config.sampleFrequency, mp3Config.sampleSize,
										mp3Config.numberOfChannels, true, false);
								Log.d(LOG_TAG,
										"Outputting MP3: " + mp3Config.sampleFrequency + ", " + mp3Config.sampleSize + ", " +
												mp3Config.numberOfChannels + ", signed, little endian");
							}

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented MP3 audio track - IOException: " + e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented MP3 audio track - general Exception");
						}

					} else if (currentAudioType == AudioUtilities.AudioType.WAV) {
						try {

							// first we need to extract PCM audio from the WAV file
							WAVConfiguration wavConfig = new WAVConfiguration();
							WAVtoPCMConverter.convertFile(inputAudioFile, currentPCMStream, wavConfig);

							// get the format - output is mono signed little-endian integers
							if (audioFormat == null) { // TODO: we assume all WAV components are the same
								// format
								audioFormat = new AudioFormat(wavConfig.sampleFrequency, wavConfig.sampleSize,
										wavConfig.numberOfChannels, true, false);
								Log.d(LOG_TAG,
										"Outputting WAV: " + wavConfig.sampleFrequency + ", " + wavConfig.sampleSize + ", " +
												wavConfig.numberOfChannels + ", signed, little endian");
							}

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented WAV audio track - IOException: " + e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented WAV audio track - general Exception");
						}

					}

					// if successful, combine the streams and store locations
					IOUtilities.closeStream(currentPCMStream);
					if (!decodingError) {
						// copy from input file to pcmStream - don't use IOUtilities so we can keep outputPCMStream open
						InputStream in = null;
						try {
							in = new FileInputStream(currentPCMFile);
							byte[] buf = new byte[IOUtilities.IO_BUFFER_SIZE];
							int len;
							while ((len = in.read(buf)) > 0) {
								outputPCMStream.write(buf, 0, len);
							}
							audioWritten = true;
						} catch (IOException e) {
							Log.d(LOG_TAG, "Error creating segmented MOV audio track - combining failed");
						} finally {
							IOUtilities.closeStream(in);
						}

						// store the positioning information
						int audioDuration = frame.mAudioDurations.get(audioId);
						audioOffsetsList.add(frameStartTime / 1000f);
						audioStartsList.add(audioTotalDuration / 1000f);
						audioLengthsList.add(audioDuration / 1000f);
						audioTotalDuration += audioDuration;
					}
					if (currentPCMFile != null) {
						currentPCMFile.delete();
					}

					break; // we're done with this frame - we only ever add one audio track to the stream per frame
				}

				// we've processed this file (any error that occurred is irrelevant now - remove track anyway)
				if (audioFound) {
					frame.mAudioPaths.remove(audioId);
					frame.mAudioDurations.remove(audioId);
				}

				// move on to the next frame's start time
				frameStartTime += frame.mFrameMaxDuration;
			}

			// finally, write the combined track to the MOV (pcmAudioStream is closed in MovWriter)
			IOUtilities.closeStream(outputPCMStream);
			if (audioWritten && audioFormat != null) { // only write if at least one part of the stream succeeded
				AudioInputStream pcmAudioStream;
				try {
					pcmAudioStream = new AudioInputStream(new FileInputStream(outputPCMFile), audioFormat,
							(long) (audioFormat.getFrameRate() * (audioTotalDuration / 1000f)));

					int arraySize = audioOffsetsList.size();
					float[] audioOffsets = new float[arraySize];
					float[] audioStarts = new float[arraySize];
					float[] audioLengths = new float[arraySize];
					for (int j = 0; j < arraySize; j++) {
						audioOffsets[j] = audioOffsetsList.get(j);
						audioStarts[j] = audioStartsList.get(j);
						audioLengths[j] = audioLengthsList.get(j);
					}

					outputFileWriter.addSegmentedAudioTrack(pcmAudioStream, audioOffsets, audioStarts, audioLengths);

				} catch (Exception e) {
					Log.d(LOG_TAG, "Error creating segmented MOV audio track - couldn't create final MOV track");
				}
			}
		}

		return filesToDelete;
	}
}
