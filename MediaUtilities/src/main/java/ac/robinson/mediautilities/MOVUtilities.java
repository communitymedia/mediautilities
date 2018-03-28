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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import ac.robinson.mov.AMRtoPCMConverter;
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
import vavi.sound.pcm.resampling.ssrc.SSRC;

// mov export: http://java.net/projects/javagraphics/sources/svn/show/trunk/src/com/bric
// m4a import: http://jaadec.sourceforge.net/ - for an alternative, see: http://www.randelshofer.ch/monte/index.html
public class MOVUtilities {

	private static final String LOG_TAG = "MOVUtilities";

	private enum AudioType {NONE, M4A, MP3, WAV, AMR}

	public static ArrayList<Uri> generateNarrativeMOV(Resources res, File outputFile, ArrayList<FrameMediaContainer>
			framesToSend, Map<Integer, Object> settings) {

		ArrayList<Uri> filesToSend = new ArrayList<Uri>();
		if (framesToSend == null || framesToSend.size() <= 0) {
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
		final int textSpacing = (Integer) settings.get(MediaUtilities.KEY_TEXT_SPACING);
		final int textCornerRadius = (Integer) settings.get(MediaUtilities.KEY_TEXT_CORNER_RADIUS);
		final boolean textBackgroundSpanWidth = (Boolean) settings.get(MediaUtilities.KEY_TEXT_BACKGROUND_SPAN_WIDTH);
		final int textMaxFontSize = (Integer) settings.get(MediaUtilities.KEY_MAX_TEXT_FONT_SIZE);
		final int textMaxCharsPerLine = (Integer) settings.get(MediaUtilities.KEY_MAX_TEXT_CHARACTERS_PER_LINE);
		final int audioResourceId = (Integer) settings.get(MediaUtilities.KEY_AUDIO_RESOURCE_ID);
		final int audioResamplingRate = (Integer) settings.get(MediaUtilities.KEY_RESAMPLE_AUDIO);

		// all frames *must* be the same dimensions, so we work from a base bitmap for everything
		Bitmap baseBitmap = Bitmap.createBitmap(outputWidth, outputHeight, ImageCacheUtilities.mBitmapFactoryOptions
				.inPreferredConfig);
		Canvas baseCanvas = new Canvas(baseBitmap);
		Paint basePaint = BitmapUtilities.getPaint(textColourNoImage, 1);
		basePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

		int audioBitmapSize = Math.min(outputWidth, outputHeight);
		int audioBitmapLeft = Math.round((outputWidth - audioBitmapSize) / 2f);
		int audioBitmapTop = Math.round((outputHeight - audioBitmapSize) / 2f);

		Bitmap imageBitmap = null;
		SVG audioSVG = null;
		JPEGMovWriter outputFileWriter = null;
		ArrayList<File> filesToDelete = new ArrayList<File>();
		try {
			outputFileWriter = new JPEGMovWriter(outputFile);

			// find all the story audio - *all* audio must be added before any frames (takes a *long* time)
			// resampling audio is slowest, but most compatible with external players other than QuickTime
			// segmented audio means we combine audio into as few tracks as possible, which increases playback
			// compatibility (but also increases the time required to export the movie)
			// individual tracks is similar in speed to segmented tracks, but typically only works with QuickTime
			// Player (hence this is not a user editable preference)
			if (audioResamplingRate > 0) {
				ArrayList<File> combinedFiles = addNarrativeAudioAsCombinedTrack(framesToSend, audioResamplingRate,
						outputFile
						.getParentFile(), outputFileWriter);
				filesToDelete.addAll(combinedFiles);
			} else {
				if (MediaUtilities.MOV_USE_SEGMENTED_AUDIO) {
					ArrayList<File> segmentFiles = addNarrativeAudioAsSegmentedTrack(framesToSend, outputFile
							.getParentFile(), outputFileWriter);
					filesToDelete.addAll(segmentFiles);
				} else {
					ArrayList<File> individualFiles = addNarrativeAudioAsIndividualTracks(framesToSend, outputFile
							.getParentFile(), outputFileWriter);
					filesToDelete.addAll(individualFiles);
				}
			}

			// add the visual content
			boolean imageLoaded;
			int imageBitmapLeft;
			int imageBitmapTop;
			for (FrameMediaContainer frame : framesToSend) {

				imageLoaded = false;
				baseCanvas.drawColor(backgroundColour);

				if (frame.mImagePath != null) {
					// scale image size to make sure it is small enough to fit in the container
					imageBitmap = BitmapUtilities.loadAndCreateScaledBitmap(frame.mImagePath, outputWidth,
							outputHeight, BitmapUtilities.ScalingLogic.FIT, true);

					if (imageBitmap != null) {
						imageBitmapLeft = Math.round((outputWidth - imageBitmap.getWidth()) / 2f);
						imageBitmapTop = Math.round((outputHeight - imageBitmap.getHeight()) / 2f);
						baseCanvas.drawBitmap(imageBitmap, imageBitmapLeft, imageBitmapTop, basePaint);

						imageLoaded = true;
					}
				}

				if (!TextUtils.isEmpty(frame.mTextContent)) {
					BitmapUtilities.drawScaledText(frame.mTextContent, baseCanvas, basePaint, (imageLoaded ?
							textColourWithImage : textColourNoImage), (imageLoaded ? textBackgroundColour : 0),
							textSpacing, textCornerRadius, imageLoaded, 0, textBackgroundSpanWidth, baseBitmap
							.getHeight(), textMaxFontSize, textMaxCharsPerLine);

				} else if (!imageLoaded) {
					// quicker to do this than load the SVG for narratives that have no audio
					if (audioSVG == null) {
						audioSVG = SVGParser.getSVGFromResource(res, audioResourceId);
					}
					if (audioSVG != null) {
						// we can't use PNG compression reliably in the MOV file, so convert to JPEG
						baseCanvas.drawPicture(audioSVG.getPicture(), new RectF(audioBitmapLeft, audioBitmapTop,
								audioBitmapLeft + audioBitmapSize, audioBitmapTop + audioBitmapSize));
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
			try {
				outputFileWriter.close(!fileError);
			} catch (IOException e) {
			}
		}

		// deletion must be *after* creation because audio and video are interleaved in the MOV output
		for (File file : filesToDelete) {
			if (file != null && file.exists()) {
				file.delete();
			}
		}

		if (!fileError) {
			filesToSend.add(Uri.fromFile(outputFile));
			return filesToSend;
		}
		filesToSend.clear();
		return filesToSend;
	}

	private static ArrayList<File> addNarrativeAudioAsIndividualTracks(ArrayList<FrameMediaContainer> framesToSend,
																	   File tempDirectory, JPEGMovWriter
																			   outputFileWriter) {

		Log.d(LOG_TAG, "Exporting individual track MOV audio");

		// the list of files to be deleted after they've been written to the movie
		ArrayList<File> filesToDelete = new ArrayList<File>();

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
				File outputPCMFile = null;
				BufferedOutputStream outputPCMStream = null;
				AudioInputStream pcmAudioStream = null;
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
							pcmConverter.convertFile(outputPCMStream);

							// get the format of the audio - PCM output is mono signed little-endian integers
							audioFormat = new AudioFormat(pcmConverter.getSampleRate(), pcmConverter.getSampleSize(),
									1, true, false);
							Log.d(LOG_TAG, "Outputting M4A: " + pcmConverter.getSampleRate() + ", " +
									pcmConverter.getSampleSize() + ", 1, signed, little endian");

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual M4A audio track - IOException: " +
									e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual M4A audio track - general " + "Exception");
						} finally {
							IOUtilities.closeStream(inputRandomAccessFile);
						}

					} else if (AndroidUtilities.arrayContains(MediaUtilities.MP3_FILE_EXTENSIONS,
							audioFileExtension)) {
						try {

							// first we need to extract PCM audio from the MP3 file
							MP3Configuration mp3Config = new MP3Configuration();
							MP3toPCMConverter.convertFile(inputAudioFile, outputPCMStream, mp3Config);

							// get the format of the audio - PCM output is mono signed 16-bit little-endian integers
							audioFormat = new AudioFormat(mp3Config.sampleFrequency, mp3Config.sampleSize, mp3Config
									.numberOfChannels, true, false);
							Log.d(LOG_TAG,
									"Outputting MP3: " + mp3Config.sampleFrequency + ", " + mp3Config.sampleSize +
											", " + mp3Config.numberOfChannels + ", signed, little endian");

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual MP3 audio track - IOException: " +
									e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual MP3 audio track - general " + "Exception");
						}

					} else if (AndroidUtilities.arrayContains(MediaUtilities.AMR_FILE_EXTENSIONS,
							audioFileExtension)) {
						try {

							// first we need to extract PCM audio from the AMR file
							AMRtoPCMConverter.convertFile(inputAudioFile, outputPCMStream);

							// get the audio format - output is mono signed 16-bit little-endian integers, 8000Hz
							audioFormat = new AudioFormat(8000, 16, 1, true, false);
							Log.d(LOG_TAG, "Outputting AMR: 8000, 16, 1, signed, little endian");

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual AMR audio track - IOException: " +
									e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual AMR audio track - general " + "Exception");
						}

					} else if (AndroidUtilities.arrayContains(MediaUtilities.WAV_FILE_EXTENSIONS,
							audioFileExtension)) {
						try {

							// first we need to extract PCM audio from the WAV file
							WAVConfiguration wavConfig = new WAVConfiguration();
							WAVtoPCMConverter.convertFile(inputAudioFile, outputPCMStream, wavConfig);

							// get the format of the audio - output is mono signed little-endian integers
							audioFormat = new AudioFormat(wavConfig.sampleFrequency, wavConfig.sampleSize, wavConfig
									.numberOfChannels, true, false);
							Log.d(LOG_TAG,
									"Outputting WAV: " + wavConfig.sampleFrequency + ", " + wavConfig.sampleSize +
											", " + wavConfig.numberOfChannels + ", signed, little endian");

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual WAV audio track - IOException: " +
									e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating individual WAV audio track - general Exception");
						}
					}

					// then add to the MOV output file if successful (pcmAudioStream is closed in MovWriter)
					if (!decodingError) {
						pcmAudioStream = new AudioInputStream(new FileInputStream(outputPCMFile), audioFormat, (int) (
								(audioFormat.getSampleRate() * audioDuration) / 1000f));
						outputFileWriter.addAudioTrack(pcmAudioStream,
								frameStartTime / 1000f, (frameStartTime + audioDuration) / 1000f);
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

	private static ArrayList<File> addNarrativeAudioAsSegmentedTrack(ArrayList<FrameMediaContainer> framesToSend, File
			tempDirectory, JPEGMovWriter outputFileWriter) {

		Log.d(LOG_TAG, "Exporting segmented MOV audio");

		// the list of files to be deleted after they've been written to the movie
		ArrayList<File> filesToDelete = new ArrayList<File>();

		// see how many tracks we need to create - one per stream, but need to separate formats
		ArrayList<String> fileTypes = new ArrayList<String>();
		ArrayList<Integer> fileCounts = new ArrayList<Integer>();
		ArrayList<String> frameTypes = new ArrayList<String>();
		ArrayList<Integer> frameCounts = new ArrayList<Integer>();
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
				} else if (AndroidUtilities.arrayContains(MediaUtilities.AMR_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.AMR_FILE_EXTENSIONS[0];
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
		ArrayList<String> fileTracks = new ArrayList<String>();
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
			File inputAudioFile = null;
			File outputPCMFile = null;
			BufferedOutputStream outputPCMStream = null;
			boolean audioWritten = false;
			File currentPCMFile = null;
			BufferedOutputStream currentPCMStream = null;
			AudioFormat audioFormat = null;

			long audioTotalDuration = 0;
			ArrayList<Float> audioOffsetsList = new ArrayList<Float>();
			ArrayList<Float> audioStartsList = new ArrayList<Float>();
			ArrayList<Float> audioLengthsList = new ArrayList<Float>();

			// get the available tracks of the right type from each frame, then remove when done
			long frameStartTime = 0;
			for (FrameMediaContainer frame : framesToSend) {

				boolean audioFound = false;
				boolean decodingError = false;
				AudioType currentAudioType = AudioType.NONE;

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
					if (!AndroidUtilities.arrayContains(MediaUtilities.MOV_AUDIO_FILE_EXTENSIONS,
							audioFileExtension)) {
						continue;
					}

					// only use tracks of the right extension TODO: pick the longest track instead of the first one?
					if (AndroidUtilities.arrayContains(MediaUtilities.M4A_FILE_EXTENSIONS, audioFileExtension) &&
							currentTrackType.equals(MediaUtilities.M4A_FILE_EXTENSIONS[0])) {
						currentAudioType = AudioType.M4A;
					} else if (AndroidUtilities.arrayContains(MediaUtilities.MP3_FILE_EXTENSIONS,
							audioFileExtension) &&
							currentTrackType.equals(MediaUtilities.MP3_FILE_EXTENSIONS[0])) {
						currentAudioType = AudioType.MP3;
					} else if (AndroidUtilities.arrayContains(MediaUtilities.AMR_FILE_EXTENSIONS,
							audioFileExtension) &&
							currentTrackType.equals(MediaUtilities.AMR_FILE_EXTENSIONS[0])) {
						currentAudioType = AudioType.AMR;
					} else if (AndroidUtilities.arrayContains(MediaUtilities.WAV_FILE_EXTENSIONS,
							audioFileExtension) &&
							currentTrackType.equals(MediaUtilities.WAV_FILE_EXTENSIONS[0])) {
						currentAudioType = AudioType.WAV;
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
							Log.d(LOG_TAG, "Error creating segmented MOV audio track - couldn't create base " +
									currentTrackType + " file");
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
						Log.d(LOG_TAG, "Error creating segmented MOV audio track - couldn't create individual " +
								currentTrackType + " file");
						continue;
					}

					// begin to convert the compressed audio
					// TODO: we assume that if we load, say, one MP3 at 44100Hz Joint-Stereo VBR then any other MP3s
					// TODO: will be in the same format - i.e., no resampling or other adjustments are taken. At the
					// TODO: moment, this means that loading multiple tracks of different formats leads to the first
					// TODO: one being output correctly, and others being, e.g., slowed down or sped up.
					if (currentAudioType == AudioType.M4A) {
						RandomAccessFile inputRandomAccessFile = null;
						try {

							// first we need to extract PCM audio from the M4A file
							inputRandomAccessFile = new RandomAccessFile(inputAudioFile, "r");
							MP4toPCMConverter pcmConverter = new MP4toPCMConverter(inputRandomAccessFile);
							pcmConverter.convertFile(currentPCMStream);

							// get the format - output from PCM converter is mono signed little-endian integers
							if (audioFormat == null) { // TODO: we assume all MP4 components are the same
								// format
								audioFormat = new AudioFormat(pcmConverter.getSampleRate(), pcmConverter.getSampleSize
										(), 1, true, false);
								Log.d(LOG_TAG, "Outputting M4A: " + pcmConverter.getSampleRate() + ", " +
										pcmConverter.getSampleSize() + ", 1, signed, little endian");
							}

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented M4A audio track - IOException: " +
									e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented M4A audio track - general Exception");
						} finally {
							IOUtilities.closeStream(inputRandomAccessFile);
						}

					} else if (currentAudioType == AudioType.MP3) {
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
										"Outputting MP3: " + mp3Config.sampleFrequency + ", " + mp3Config.sampleSize +
												", " + mp3Config.numberOfChannels + ", signed, little endian");
							}

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented MP3 audio track - IOException: " +
									e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented MP3 audio track - general Exception");
						}

					} else if (currentAudioType == AudioType.AMR) {
						try {

							// first we need to extract PCM audio from the AMR file
							AMRtoPCMConverter.convertFile(inputAudioFile, outputPCMStream);

							// get the audio format - output is mono signed 16-bit little-endian integers, 8000Hz
							if (audioFormat == null) { // TODO: we assume all AMR components are the same format
								audioFormat = new AudioFormat(8000, 16, 1, true, false);
								Log.d(LOG_TAG, "Outputting AMR: 8000, 16, 1, signed, little endian");
							}

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented AMR audio track - IOException: " +
									e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented AMR audio track - general Exception");
						}

					} else if (currentAudioType == AudioType.WAV) {
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
										"Outputting WAV: " + wavConfig.sampleFrequency + ", " + wavConfig.sampleSize +
												", " + wavConfig.numberOfChannels + ", signed, little endian");
							}

						} catch (IOException e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented WAV audio track - IOException: " +
									e.getLocalizedMessage());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating segmented WAV audio track - general Exception");
						}

					}

					// if successful, combine the streams and store locations
					IOUtilities.closeStream(currentPCMStream);
					if (!decodingError) {
						// copy from input file to pcmStream - don't use IOUtilities so we can keep the stream open
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
			if (audioWritten) { // only write if at least one part of the stream succeeded
				AudioInputStream pcmAudioStream;
				try {
					pcmAudioStream = new AudioInputStream(new FileInputStream(outputPCMFile), audioFormat, (int) (
							(audioFormat.getSampleRate() * audioTotalDuration) / 1000f));

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

	private static ArrayList<File> addNarrativeAudioAsCombinedTrack(ArrayList<FrameMediaContainer> framesToSend, int
			sampleRate, File tempDirectory, JPEGMovWriter outputFileWriter) {

		Log.d(LOG_TAG, "Exporting combined MOV audio (" + sampleRate + ")");

		// the list of files to be deleted after they've been written to the movie
		ArrayList<File> filesToDelete = new ArrayList<File>();

		// see how many tracks we need to create - find the maximum number of (compatible) audio items per frame
		int trackCount = 0;
		for (FrameMediaContainer frame : framesToSend) {
			int localCount = 0;
			for (String type : frame.mAudioPaths) {
				String actualFileExtension = IOUtilities.getFileExtension(type);
				final String fileExtension; // use the base file extension instead of the actual - combine same types
				if (AndroidUtilities.arrayContains(MediaUtilities.M4A_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.M4A_FILE_EXTENSIONS[0];
				} else if (AndroidUtilities.arrayContains(MediaUtilities.MP3_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.MP3_FILE_EXTENSIONS[0];
				} else if (AndroidUtilities.arrayContains(MediaUtilities.AMR_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.AMR_FILE_EXTENSIONS[0];
				} else if (AndroidUtilities.arrayContains(MediaUtilities.WAV_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.WAV_FILE_EXTENSIONS[0];
				} else {
					fileExtension = actualFileExtension;
				}
				if (AndroidUtilities.arrayContains(MediaUtilities.MOV_AUDIO_FILE_EXTENSIONS, fileExtension)) {
					localCount += 1;
				}
			}
			trackCount = Math.max(trackCount, localCount);
		}

		if (trackCount <= 0) {
			return filesToDelete; // no audio present - nothing to do
		}

		// all audio parts are combined into one track with these properties
		AudioFormat globalAudioFormat = new AudioFormat(sampleRate, 16, 1, true, false);

		// prepare the global audio track
		boolean globalAudioWritten = false;
		long globalAudioDuration = 0;
		File globalPCMFile;
		try {
			globalPCMFile = File.createTempFile("export", "all.pcm", tempDirectory);
		} catch (IOException e) {
			return filesToDelete; // not much else we can do
		}
		filesToDelete.add(globalPCMFile); // deleted after the rest of the movie has been written

		// create a separate temporary PCM file for each parallel audio track
		File[] pcmFiles = new File[trackCount];
		long[] pcmFileDurations = new long[trackCount];
		for (int i = 0; i < pcmFiles.length; i++) {
			BufferedOutputStream outputPCMStream = null;
			try {
				pcmFiles[i] = File.createTempFile("export-" + i, ".pcm", tempDirectory);
				outputPCMStream = new BufferedOutputStream(new FileOutputStream(pcmFiles[i]));
			} catch (Exception e) {
				IOUtilities.closeStream(outputPCMStream);
				if (pcmFiles[i] != null) {
					pcmFiles[i].delete();
				}
				pcmFiles[i] = null;
				Log.d(LOG_TAG, "Error creating segmented MOV audio track - couldn't create stream " + i + " PCM file");
				continue;
			}

			File inputAudioFile;
			boolean audioWritten = false;
			File currentPCMFile = null;
			BufferedOutputStream currentPCMStream = null;
			File temporaryPCMFile = null;
			BufferedInputStream temporaryPCMInputStream = null;
			BufferedOutputStream temporaryPCMOutputStream = null;

			// get the available tracks of the right type from each frame, then remove when done
			long frameStartTime = 0;
			for (FrameMediaContainer frame : framesToSend) {

				boolean audioFound = false;
				boolean decodingError = false;
				AudioType currentAudioType;

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
					if (!AndroidUtilities.arrayContains(MediaUtilities.MOV_AUDIO_FILE_EXTENSIONS,
							audioFileExtension)) {
						continue; // skip incompatible files
					}

					// only use tracks of the right extension TODO: pick the longest track instead of the first one?
					if (AndroidUtilities.arrayContains(MediaUtilities.M4A_FILE_EXTENSIONS, audioFileExtension)) {
						currentAudioType = AudioType.M4A;
					} else if (AndroidUtilities.arrayContains(MediaUtilities.MP3_FILE_EXTENSIONS,
							audioFileExtension)) {
						currentAudioType = AudioType.MP3;
					} else if (AndroidUtilities.arrayContains(MediaUtilities.AMR_FILE_EXTENSIONS,
							audioFileExtension)) {
						currentAudioType = AudioType.AMR;
					} else if (AndroidUtilities.arrayContains(MediaUtilities.WAV_FILE_EXTENSIONS,
							audioFileExtension)) {
						currentAudioType = AudioType.WAV;
					} else {
						continue;
					}

					Log.d(LOG_TAG, "Processing " + audioPath);

					// if we get here it's the right type of audio, so begin to get PCM from the compressed source
					inputAudioFile = new File(audioPath);
					audioFound = true;

					// create temporary files per-track, so that one corrupt track doesn't break everything
					try {
						currentPCMFile = File.createTempFile(inputAudioFile.getName(), ".pcm", tempDirectory);
						currentPCMStream = new BufferedOutputStream(new FileOutputStream(currentPCMFile));
					} catch (Exception e) {
						IOUtilities.closeStream(currentPCMStream);
						if (currentPCMFile != null) {
							currentPCMFile.delete();
						}
						Log.d(LOG_TAG, "Error creating combined MOV audio track - couldn't create individual " +
								audioFileExtension + " files");
						continue;
					}

					// begin to convert the compressed audio
					if (currentAudioType == AudioType.M4A) {
						RandomAccessFile inputRandomAccessFile = null;
						try {
							// first we need to extract PCM audio from the M4A file
							// output from PCM converter is mono signed little-endian integers
							inputRandomAccessFile = new RandomAccessFile(inputAudioFile, "r");
							MP4toPCMConverter pcmConverter = new MP4toPCMConverter(inputRandomAccessFile);
							pcmConverter.convertFile(currentPCMStream);

							// if the sample rate or sample size don't match our output, use SSRC to resample the audio
							if (pcmConverter.getSampleRate() != globalAudioFormat.getSampleRate() ||
									pcmConverter.getSampleSize() != globalAudioFormat.getSampleSizeInBits()) {

								Log.d(LOG_TAG, "Resampling M4A audio");
								try {
									temporaryPCMInputStream = new BufferedInputStream(new FileInputStream
											(currentPCMFile));
									temporaryPCMFile = File.createTempFile(inputAudioFile.getName(), ".temp.pcm",
											tempDirectory);
									temporaryPCMOutputStream = new BufferedOutputStream(new FileOutputStream
											(temporaryPCMFile));

									// use SSRC to resample PCM audio - note that two passes are required for accuracy
									new SSRC(tempDirectory, temporaryPCMInputStream, temporaryPCMOutputStream,
											ByteOrder.LITTLE_ENDIAN, pcmConverter
											.getSampleRate(), (int) globalAudioFormat.getSampleRate(), pcmConverter
											.getSampleSize(), globalAudioFormat
											.getSampleSizeInBits(), 1, (int) currentPCMFile.length(), 0, 0, 0, true,
											false, false, true);

									// this is now the PCM file to use
									if (currentPCMFile != null) {
										currentPCMFile.delete();
									}
									currentPCMFile = temporaryPCMFile;

								} catch (Exception e) {
									if (temporaryPCMFile != null) {
										temporaryPCMFile.delete();
									}
								} finally {
									IOUtilities.closeStream(temporaryPCMInputStream);
									IOUtilities.closeStream(temporaryPCMOutputStream);
								}
							}

							Log.d(LOG_TAG, "Outputting M4A: " + globalAudioFormat.getSampleRate() + ", " +
									globalAudioFormat.getSampleSizeInBits() + " from " + pcmConverter.getSampleRate() +
									"," + " " + pcmConverter.getSampleSize());
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating combined M4A audio track: " + e.getLocalizedMessage());
						} finally {
							IOUtilities.closeStream(inputRandomAccessFile);
						}

					} else if (currentAudioType == AudioType.MP3) {
						try {
							// first we need to extract PCM audio from the MP3 file
							// output from PCM converter is mono signed 16-bit little-endian integers
							MP3Configuration mp3Config = new MP3Configuration();
							MP3toPCMConverter.convertFile(inputAudioFile, currentPCMStream, mp3Config);

							// if the sample rate or sample size don't match our output, use SSRC to resample the audio
							if (mp3Config.sampleFrequency != globalAudioFormat.getSampleRate() ||
									mp3Config.sampleSize != globalAudioFormat.getSampleSizeInBits()) {

								Log.d(LOG_TAG, "Resampling MP3 audio");
								try {
									temporaryPCMInputStream = new BufferedInputStream(new FileInputStream
											(currentPCMFile));
									temporaryPCMFile = File.createTempFile(inputAudioFile.getName(), ".temp.pcm",
											tempDirectory);
									temporaryPCMOutputStream = new BufferedOutputStream(new FileOutputStream
											(temporaryPCMFile));

									// use SSRC to resample PCM audio - note that two passes are required for accuracy
									new SSRC(tempDirectory, temporaryPCMInputStream, temporaryPCMOutputStream,
											ByteOrder.LITTLE_ENDIAN, mp3Config.sampleFrequency, (int) globalAudioFormat
											.getSampleRate(), mp3Config.sampleSize, globalAudioFormat
											.getSampleSizeInBits(), 1, (int) currentPCMFile
											.length(), 0, 0, 0, true, false, false, true);

									// this is now the PCM file to use
									if (currentPCMFile != null) {
										currentPCMFile.delete();
									}
									currentPCMFile = temporaryPCMFile;

								} catch (Exception e) {
									if (temporaryPCMFile != null) {
										temporaryPCMFile.delete();
									}
								} finally {
									IOUtilities.closeStream(temporaryPCMInputStream);
									IOUtilities.closeStream(temporaryPCMOutputStream);
								}
							}

							Log.d(LOG_TAG, "Outputting MP3: " + globalAudioFormat.getSampleRate() + ", " +
									globalAudioFormat.getSampleSizeInBits() + " from " + mp3Config.sampleFrequency +
									", " + mp3Config.sampleSize);

						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating combined MP3 audio track: " + e.getLocalizedMessage());
						}

					} else if (currentAudioType == AudioType.AMR) {
						try {
							// first we need to extract PCM audio from the AMR file
							// output from PCM converter is mono signed 16-bit little-endian integers, always 8000Hz
							AMRtoPCMConverter.convertFile(inputAudioFile, currentPCMStream);
							AudioFormat amrFormat = new AudioFormat(8000, 16, 1, true, false);

							// if the sample rate or sample size don't match our output, use SSRC to resample the audio
							if (amrFormat.getSampleRate() != globalAudioFormat.getSampleRate() ||
									amrFormat.getSampleSizeInBits() != globalAudioFormat.getSampleSizeInBits()) {

								Log.d(LOG_TAG, "Resampling AMR audio");
								try {
									temporaryPCMInputStream = new BufferedInputStream(new FileInputStream
											(currentPCMFile));
									temporaryPCMFile = File.createTempFile(inputAudioFile.getName(), ".temp.pcm",
											tempDirectory);
									temporaryPCMOutputStream = new BufferedOutputStream(new FileOutputStream
											(temporaryPCMFile));

									// use SSRC to resample PCM audio - note that two passes are required for accuracy
									new SSRC(tempDirectory, temporaryPCMInputStream, temporaryPCMOutputStream,
											ByteOrder.LITTLE_ENDIAN, (int) amrFormat
											.getSampleRate(), (int) globalAudioFormat.getSampleRate(), amrFormat
											.getSampleSizeInBits(), globalAudioFormat
											.getSampleSizeInBits(), 1, (int) currentPCMFile.length(), 0, 0, 0, true,
											false, false, true);

									// this is now the PCM file to use
									if (currentPCMFile != null) {
										currentPCMFile.delete();
									}
									currentPCMFile = temporaryPCMFile;

								} catch (Exception e) {
									if (temporaryPCMFile != null) {
										temporaryPCMFile.delete();
									}
								} finally {
									IOUtilities.closeStream(temporaryPCMInputStream);
									IOUtilities.closeStream(temporaryPCMOutputStream);
								}
							}

							Log.d(LOG_TAG, "Outputting AMR: " + globalAudioFormat.getSampleRate() + ", " +
									globalAudioFormat.getSampleSizeInBits() + " from " + amrFormat.getSampleRate() +
									", " + amrFormat.getSampleSizeInBits());

						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating combined AMR audio track: " + e.getLocalizedMessage());
						}

					} else if (currentAudioType == AudioType.WAV) {
						try {
							// first we need to extract PCM audio from the WAV file
							// output from PCM converter is mono signed little-endian integers
							WAVConfiguration wavConfig = new WAVConfiguration();
							WAVtoPCMConverter.convertFile(inputAudioFile, currentPCMStream, wavConfig);

							// if the sample rate or sample size don't match our output, use SSRC to resample the audio
							if (wavConfig.sampleFrequency != globalAudioFormat.getSampleRate() ||
									wavConfig.sampleSize != globalAudioFormat.getSampleSizeInBits()) {

								Log.d(LOG_TAG, "Resampling WAV audio");
								try {
									temporaryPCMInputStream = new BufferedInputStream(new FileInputStream
											(currentPCMFile));
									temporaryPCMFile = File.createTempFile(inputAudioFile.getName(), ".temp.pcm",
											tempDirectory);
									temporaryPCMOutputStream = new BufferedOutputStream(new FileOutputStream
											(temporaryPCMFile));

									// use SSRC to resample PCM audio - note that two passes are required for accuracy
									new SSRC(tempDirectory, temporaryPCMInputStream, temporaryPCMOutputStream,
											ByteOrder.LITTLE_ENDIAN, wavConfig.sampleFrequency, (int) globalAudioFormat
											.getSampleRate(), wavConfig.sampleSize, globalAudioFormat
											.getSampleSizeInBits(), 1, (int) currentPCMFile
											.length(), 0, 0, 0, true, false, false, true);

									// this is now the PCM file to use
									if (currentPCMFile != null) {
										currentPCMFile.delete();
									}
									currentPCMFile = temporaryPCMFile;

								} catch (Exception e) {
									if (temporaryPCMFile != null) {
										temporaryPCMFile.delete();
									}
								} finally {
									IOUtilities.closeStream(temporaryPCMInputStream);
									IOUtilities.closeStream(temporaryPCMOutputStream);
								}
							}

							Log.d(LOG_TAG, "Outputting WAV: " + globalAudioFormat.getSampleRate() + ", " +
									globalAudioFormat.getSampleSizeInBits() + " from " + wavConfig.sampleFrequency +
									", " + wavConfig.sampleSize);

						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating combined WAV audio track: " + e.getLocalizedMessage());
						}
					}

					// if successful, combine the streams and store locations
					IOUtilities.closeStream(currentPCMStream);
					if (!decodingError) {

						// pad any gaps in audio (i.e., frames that don't have sound) with silence
						// TODO: will we ever overflow Long.MAX_VALUE with this?
						long silenceNeeded = ((long) globalAudioFormat.getSampleRate() *
								(long) (globalAudioFormat.getSampleSizeInBits() / 8) *
								(frameStartTime - pcmFileDurations[i])) / 1000;
						if (silenceNeeded % 2 != 0) {
							silenceNeeded += 1; // must be an even number: two bytes for each sample
						}
						Log.d(LOG_TAG, "Adding " + silenceNeeded + " samples of silence");

						// copy from input file to pcmStream - don't use IOUtilities so we can keep the stream open
						InputStream inputStream = null;
						try {
							// fill with silence to span the gap in audio files - first pad to multiple of buffer
							// (int cast is fine as long as IOUtilities.IO_BUFFER_SIZE is less than Integer.MAX_VALUE)
							int remainingSamples = (int) (silenceNeeded % IOUtilities.IO_BUFFER_SIZE);
							byte[] buffer = new byte[remainingSamples];
							outputPCMStream.write(buffer, 0, remainingSamples);
							buffer = new byte[IOUtilities.IO_BUFFER_SIZE];
							for (int s = 0; s < silenceNeeded / IOUtilities.IO_BUFFER_SIZE; s++) {
								outputPCMStream.write(buffer, 0, IOUtilities.IO_BUFFER_SIZE);
							}

							// now add the new audio data
							int numSamples;
							inputStream = new FileInputStream(currentPCMFile);
							while ((numSamples = inputStream.read(buffer)) > 0) {
								outputPCMStream.write(buffer, 0, numSamples);
							}

							audioWritten = true;
						} catch (IOException e) {
							Log.d(LOG_TAG, "Error creating combined MOV audio track - combining failed");
						} finally {
							IOUtilities.closeStream(inputStream);
						}

						pcmFileDurations[i] = frameStartTime + frame.mAudioDurations.get(audioId);
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

			IOUtilities.closeStream(outputPCMStream);

			globalAudioWritten |= audioWritten;
			globalAudioDuration = Math.max(pcmFileDurations[i], globalAudioDuration);
		}

		// finally, write the combined PCM stream to the MOV file
		if (globalAudioWritten) { // only write if at least one part of the stream succeeded

			// remove any streams that had errors
			File[] nonNullPCMFiles = new File[pcmFiles.length];
			int arrayIndex = 0;
			for (File file : pcmFiles) {
				if (file != null) {
					nonNullPCMFiles[arrayIndex] = file;
					arrayIndex += 1;
				}
			}
			if (arrayIndex < pcmFiles.length) {
				Log.d(LOG_TAG, "Error stream found - trimming length to " + arrayIndex);
				pcmFiles = Arrays.copyOf(nonNullPCMFiles, arrayIndex);
			}

			// if we have parallel audio, average the streams
			if (pcmFiles.length > 1) {

				// TODO: a better way might be to intelligently combine (e.g., pick one stream when others are blank)
				// pad files with silence so they are all the same length - this simplifies combining
				long totalBytes = 0;
				for (File file : pcmFiles) {
					totalBytes = Math.max(totalBytes, file.length());
				}

				boolean streamLengthsNormalised = true;
				for (int i = 0; i < pcmFiles.length; i++) {
					if (pcmFiles[i].length() < totalBytes) {
						// fill with silence to span the gap in audio files - first pad to multiple of buffer
						// (int cast is fine as long as IOUtilities.IO_BUFFER_SIZE is less than Integer.MAX_VALUE)
						long silenceNeeded = totalBytes - pcmFiles[i].length();
						Log.d(LOG_TAG, "Extending stream " + i + " with " + silenceNeeded + " samples of silence");
						BufferedOutputStream silencePCMStream = null;
						try {
							silencePCMStream = new BufferedOutputStream(new FileOutputStream(pcmFiles[i], true));
							int remainingSamples = (int) (silenceNeeded % IOUtilities.IO_BUFFER_SIZE);
							byte[] buffer = new byte[remainingSamples];
							silencePCMStream.write(buffer, 0, remainingSamples);
							buffer = new byte[IOUtilities.IO_BUFFER_SIZE];
							for (int s = 0; s < silenceNeeded / IOUtilities.IO_BUFFER_SIZE; s++) {
								silencePCMStream.write(buffer, 0, IOUtilities.IO_BUFFER_SIZE);
							}
						} catch (Exception e) {
							streamLengthsNormalised = false;
							globalPCMFile.delete(); // delete the existing global file, and fall back to track 1
							globalPCMFile = pcmFiles[0];
							Log.d(LOG_TAG, "Error creating segmented MOV audio track - couldn't normalise stream " +
									"lengths");
							break;
						} finally {
							IOUtilities.closeStream(silencePCMStream);
						}
					}
				}

				// average the 2 or 3 streams
				if (streamLengthsNormalised) {
					Log.d(LOG_TAG, "Stream lengths normalised; averaging " + pcmFiles.length + ": " + totalBytes);
					InputStream[] pcmStreams = new InputStream[pcmFiles.length];
					byte[][] streamBuffers = new byte[pcmFiles.length][];
					int[] streamOffsets = new int[pcmFiles.length];
					int[] streamBytesRead = new int[pcmFiles.length];
					short[] streamAverages = new short[pcmFiles.length];
					BufferedOutputStream outputPCMStream = null;
					try {
						for (int i = 0; i < pcmStreams.length; i++) {
							pcmStreams[i] = new FileInputStream(pcmFiles[i]);
							streamBuffers[i] = new byte[IOUtilities.IO_BUFFER_SIZE];
						}
						outputPCMStream = new BufferedOutputStream(new FileOutputStream(globalPCMFile));

						// read the same size buffer from each file, averaging over all 2 or 3 of them
						int totalBytesRead = 0;
						int bytesToWrite;
						short averageValue;
						while (totalBytesRead < totalBytes) {
							for (int i = 0; i < pcmStreams.length; i++) {
								// see: https://goo.gl/1k7cZ1
								while ((streamBytesRead[i] = pcmStreams[i].read(streamBuffers[i], streamOffsets[i],
										streamBuffers[i].length - streamOffsets[i])) != -1) {
									streamOffsets[i] += streamBytesRead[i];
									if (streamOffsets[i] >= streamBuffers[i].length) {
										break;
									}
								}
							}

							totalBytesRead += streamOffsets[0];
							bytesToWrite = Integer.MAX_VALUE;
							for (int i = 0; i < pcmStreams.length; i++) {
								bytesToWrite = Math.min(bytesToWrite, streamOffsets[i]);
							}

							// average the 2 or 3 buffers, then write to the global output stream
							for (int i = 0; i < bytesToWrite; i += 2) {
								for (int s = 0; s < pcmStreams.length; s++) {
									streamAverages[s] = (short) (((streamBuffers[s][i + 1] & 0xff) << 8) |
											(streamBuffers[s][i] & 0xff));
								}

								// TODO: if we ever make the number of streams flexible, this will need updating
								// see: https://stackoverflow.com/questions/3816446/
								if (pcmStreams.length == 3) {
									averageValue = (short) ((streamAverages[0] >> 1) + (streamAverages[1] >> 1) +
											(streamAverages[2] >> 1) +
											(streamAverages[0] & streamAverages[1] & streamAverages[2] & 0x1));
								} else {
									averageValue = (short) ((streamAverages[0] >> 1) + (streamAverages[1] >> 1) +
											(streamAverages[0] & streamAverages[1] & 0x1));
								}

								// our output is little-endian
								outputPCMStream.write(averageValue & 0xff);
								outputPCMStream.write((averageValue >> 8) & 0xff);
							}

							// shift any bytes between bytesToWrite and streamOffsets to start of buffer, then repeat
							for (int i = 0; i < pcmStreams.length; i++) {
								if (streamOffsets[i] > bytesToWrite) {
									Log.d(LOG_TAG, "Correcting offset read: " + (streamOffsets[i] - bytesToWrite));
									System.arraycopy(streamBuffers, bytesToWrite, streamBuffers, 0,
											streamOffsets[i] - bytesToWrite);
									streamOffsets[i] -= bytesToWrite;
								} else {
									streamOffsets[i] = 0;
								}
							}
						}

					} catch (Exception e) {
						globalPCMFile.delete(); // delete the existing global file, and fall back to track 1
						globalPCMFile = pcmFiles[0];
						Log.d(LOG_TAG, "Error creating segmented MOV audio track - couldn't create combined stream");
					} finally {
						for (InputStream pcmStream : pcmStreams) {
							IOUtilities.closeStream(pcmStream);
						}
						IOUtilities.closeStream(outputPCMStream);
					}
				}

			} else {
				globalPCMFile.delete(); // delete the existing global file, and just use our first stream
				globalPCMFile = pcmFiles[0]; // most of the time we have only one track
			}

			// TODO: VLC still fails to play even resampled and mixed audio streams - do we need a WAV header to fix?
			AudioInputStream pcmAudioStream;
			try {
				// output from converters and/or SSRC is mono signed 16-bit little-endian integers
				pcmAudioStream = new AudioInputStream(new FileInputStream(globalPCMFile), globalAudioFormat, (int) (
						(globalAudioFormat.getSampleRate() * globalAudioDuration) / 1000f));

				// new source: pumpernickel/pump-quicktime/src/main/java/com/pump/animation/quicktime/MovWriter.java
				outputFileWriter.addAudioTrack(pcmAudioStream, 0, globalAudioDuration / 1000f);

			} catch (Exception e) {
				Log.d(LOG_TAG, "Error creating combined MOV audio track - couldn't create final MOV track");
			}
		}

		return filesToDelete;
	}
}
