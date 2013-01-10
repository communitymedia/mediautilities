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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Map;

import ac.robinson.mov.JPEGMovWriter;
import ac.robinson.mov.MP4toPCMConverter;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.IOUtilities;
import ac.robinson.util.ImageCacheUtilities;
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
import com.ringdroid.soundfile.CheapSoundFile;

// mov export: http://java.net/projects/javagraphics/sources/svn/show/trunk/src/com/bric
// m4a import: http://jaadec.sourceforge.net/ - for an alternative, see: http://www.randelshofer.ch/monte/index.html
public class MOVUtilities {

	public static ArrayList<Uri> generateNarrativeMOV(Resources res, File outputFile,
			ArrayList<FrameMediaContainer> framesToSend, Map<Integer, Object> settings) {

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
		final float textCornerRadius = (Float) settings.get(MediaUtilities.KEY_TEXT_CORNER_RADIUS);
		final boolean textBackgroundSpanWidth = (Boolean) settings.get(MediaUtilities.KEY_TEXT_BACKGROUND_SPAN_WIDTH);
		final int textMaxFontSize = (Integer) settings.get(MediaUtilities.KEY_MAX_TEXT_FONT_SIZE);
		final int textMaxCharsPerLine = (Integer) settings.get(MediaUtilities.KEY_MAX_TEXT_CHARACTERS_PER_LINE);
		final int audioResourceId = (Integer) settings.get(MediaUtilities.KEY_AUDIO_RESOURCE_ID);

		// all frames *must* be the same dimensions, so we work from a base bitmap for everything
		Bitmap baseBitmap = Bitmap.createBitmap(outputWidth, outputHeight,
				ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		Canvas baseCanvas = new Canvas(baseBitmap);
		Paint basePaint = BitmapUtilities.getPaint(textColourNoImage, 1);
		basePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

		int audioBitmapSize = Math.min(outputWidth, outputHeight);
		int audioBitmapLeft = Math.round((outputWidth - audioBitmapSize) / 2f);
		int audioBitmapTop = Math.round((outputHeight - audioBitmapSize) / 2f);

		Bitmap imageBitmap = null;
		SVG audioSVG = null;
		JPEGMovWriter outputFileWriter = null;
		try {
			outputFileWriter = new JPEGMovWriter(outputFile);

			// find all the story audio - *all* audio must be added before any frames (takes a *long* time)
			// segmented audio means we combine audio into as few tracks as possible, which increases playback
			// compatibility (but also increases the time required to export the movie)
			if (MediaUtilities.MOV_USE_SEGMENTED_AUDIO) {
				addNarrativeAudioAsSegmentedTrack(framesToSend, outputFile.getParentFile(), outputFileWriter);
			} else {
				addNarrativeAudioAsIndividualTracks(framesToSend, outputFile.getParentFile(), outputFileWriter);
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
					BitmapUtilities.drawScaledText(frame.mTextContent, baseCanvas, basePaint,
							(imageLoaded ? textColourWithImage : textColourNoImage),
							(imageLoaded ? textBackgroundColour : 0), textSpacing, textCornerRadius, imageLoaded, 0,
							textBackgroundSpanWidth, baseBitmap.getHeight(), textMaxFontSize, textMaxCharsPerLine);

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
		} catch (Throwable t) {
			fileError = true; // these are the only places where errors really matter
		} finally {
			try {
				outputFileWriter.close(!fileError);
			} catch (IOException e) {
			}
		}

		audioSVG = null;
		baseCanvas = null;
		if (!fileError) {
			filesToSend.add(Uri.fromFile(outputFile));
			return filesToSend;
		}
		filesToSend.clear();
		return filesToSend;
	}

	private static void addNarrativeAudioAsIndividualTracks(ArrayList<FrameMediaContainer> framesToSend,
			File tempDirectory, JPEGMovWriter outputFileWriter) {

		int frameDuration;
		long frameStartTime = 0;
		for (FrameMediaContainer frame : framesToSend) {

			// we need these values in seconds, but store in milliseconds so we don't round incorrectly later
			frameDuration = frame.mFrameMaxDuration;

			int audioLengthIndex = 0;
			int audioDuration;
			for (String audioPath : frame.mAudioPaths) {

				if (!audioPath.endsWith(MediaUtilities.MOV_AUDIO_FILE_EXTENSION)) {
					continue; // TODO: can only currently parse m4a audio
				}

				audioDuration = frame.mAudioDurations.get(audioLengthIndex);

				File inputAudioFile = new File(audioPath);
				RandomAccessFile inputRandomAccessFile = null;
				File outputPCMFile = null;
				AudioFormat audioFormat = null;
				BufferedOutputStream pcmStream = null;
				AudioInputStream pcmAudioStream = null;
				try {

					// first we need to extract PCM audio from the M4A file
					outputPCMFile = File.createTempFile(inputAudioFile.getName(), ".pcm", tempDirectory);
					pcmStream = new BufferedOutputStream(new FileOutputStream(outputPCMFile));
					inputRandomAccessFile = new RandomAccessFile(inputAudioFile, "r");
					MP4toPCMConverter pcmConverter = new MP4toPCMConverter(inputRandomAccessFile);
					audioFormat = new AudioFormat(pcmConverter.getSampleRate(), pcmConverter.getSampleSize(), 1, true,
							true); // output from PCM converter is signed 16-bit big-endian integers
					pcmConverter.convertFile(pcmStream);

					// then add to the MOV output file (pcmAudioStream is closed in MovWriter)
					pcmAudioStream = new AudioInputStream(new FileInputStream(outputPCMFile), audioFormat,
							(int) ((audioFormat.getSampleRate() * audioDuration) / 1000f));
					outputFileWriter.addAudioTrack(pcmAudioStream, frameStartTime / 1000f,
							(frameStartTime + audioDuration) / 1000f);

				} catch (IOException e) {
					Log.d("MOVUtilities", "Error creating audio track - IOException");
				} catch (Exception e) {
					Log.d("MOVUtilities", "Error creating audio track - General Exception");
				} finally {
					IOUtilities.closeStream(inputRandomAccessFile);
					IOUtilities.closeStream(pcmStream);
					if (outputPCMFile != null) {
						outputPCMFile.delete();
					}
				}

				audioLengthIndex += 1;
			}

			frameStartTime += frameDuration;
		}
	}

	private static void addNarrativeAudioAsSegmentedTrack(ArrayList<FrameMediaContainer> framesToSend,
			File tempDirectory, JPEGMovWriter outputFileWriter) {

		// see how many tracks we need to create
		int trackCount = 0;
		for (FrameMediaContainer frame : framesToSend) {
			trackCount = Math.max(frame.mAudioDurations.size(), trackCount);
		}

		for (int i = 0; i < trackCount; i++) {
			CheapSoundFile baseSoundFile = null;
			int frameDuration;
			long audioTotalDuration = 0;
			long frameStartTime = 0;
			ArrayList<Float> audioOffsetsList = new ArrayList<Float>();
			ArrayList<Float> audioStartsList = new ArrayList<Float>();
			ArrayList<Float> audioLengthsList = new ArrayList<Float>();
			for (FrameMediaContainer frame : framesToSend) {

				// we need these values in seconds, but store in milliseconds so we don't round incorrectly later
				frameDuration = frame.mFrameMaxDuration;

				if (frame.mAudioDurations.size() > i) { // TODO: pick the longest track instead?

					String audioPath = frame.mAudioPaths.get(i);

					// TODO: can only currently parse m4a audio
					if (audioPath.endsWith(MediaUtilities.MOV_AUDIO_FILE_EXTENSION)) {
						try {
							if (baseSoundFile == null) {
								baseSoundFile = CheapSoundFile.create(audioPath, null);
							} else {
								CheapSoundFile additionalSoundFile = CheapSoundFile.create(audioPath, null);
								baseSoundFile.addSoundFile(additionalSoundFile);
							}

							int audioDuration = frame.mAudioDurations.get(i);
							audioOffsetsList.add(frameStartTime / 1000f);
							audioStartsList.add(audioTotalDuration / 1000f);
							audioLengthsList.add(audioDuration / 1000f);
							audioTotalDuration += audioDuration;
						} catch (Exception e) {
							Log.d("MOVUtilities", "Error adding sound file - General Exception");
						}

					}
				}

				frameStartTime += frameDuration;
			}

			if (baseSoundFile != null) {
				File outputAACFile = null;
				RandomAccessFile inputRandomAccessFile = null;
				File outputPCMFile = null;
				AudioFormat audioFormat = null;
				BufferedOutputStream pcmStream = null;
				AudioInputStream pcmAudioStream = null;
				try {
					// first we need to write the combined M4A file - this now has all audio segments in this track
					outputAACFile = File.createTempFile(baseSoundFile.getFile().getName(), ".m4a", tempDirectory);
					baseSoundFile.writeFile(outputAACFile, 0, baseSoundFile.getNumFrames());

					// then we need to extract PCM audio from the M4A file
					outputPCMFile = File.createTempFile(outputAACFile.getName(), ".pcm", tempDirectory);
					pcmStream = new BufferedOutputStream(new FileOutputStream(outputPCMFile));
					inputRandomAccessFile = new RandomAccessFile(outputAACFile, "r");
					MP4toPCMConverter pcmConverter = new MP4toPCMConverter(inputRandomAccessFile);
					audioFormat = new AudioFormat(pcmConverter.getSampleRate(), pcmConverter.getSampleSize(), 1, true,
							true); // output from PCM converter is signed 16-bit big-endian integers
					pcmConverter.convertFile(pcmStream);

					// then add to the MOV output file (pcmAudioStream is closed in MovWriter)
					pcmAudioStream = new AudioInputStream(new FileInputStream(outputPCMFile), audioFormat,
							(int) ((audioFormat.getSampleRate() * audioTotalDuration) / 1000f));
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

				} catch (IOException e) {
					Log.d("MOVUtilities", "Error creating audio track - IOException");
				} catch (Exception e) {
					Log.d("MOVUtilities", "Error creating audio track - General Exception");
				} finally {
					IOUtilities.closeStream(inputRandomAccessFile);
					IOUtilities.closeStream(pcmStream);
					if (outputAACFile != null) {
						outputAACFile.delete();
					}
					if (outputPCMFile != null) {
						outputPCMFile.delete();
					}
				}
			}
		}
	}
}
