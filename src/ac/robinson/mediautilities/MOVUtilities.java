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
			long frameStartTime = 0;

			// find all the story components - *all* audio must be added before any frames (takes a *long* time)
			int frameDuration;
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

					// first we need to extract PCM audio from the M4A file
					File inputAudioFile = new File(audioPath);
					File tempOutputAudioFile = File.createTempFile(inputAudioFile.getName(), ".pcm",
							outputFile.getParentFile());
					RandomAccessFile inputRandomAccessFile = null;
					AudioFormat audioFormat = null;
					BufferedOutputStream pcmStream = null;
					try {
						pcmStream = new BufferedOutputStream(new FileOutputStream(tempOutputAudioFile));
						inputRandomAccessFile = new RandomAccessFile(inputAudioFile, "r");
						MP4toPCMConverter pcmConverter = new MP4toPCMConverter(inputRandomAccessFile);
						audioFormat = new AudioFormat(pcmConverter.getSampleRate(), pcmConverter.getSampleSize(), 1,
								true, true); // output from PCM converter is signed 16-bit big-endian integers
						pcmConverter.convertFile(pcmStream);
					} catch (IOException e) {
						Log.d("MOVUtilities", "Error creating audio track - IOException");
					} catch (Exception e) {
						Log.d("MOVUtilities", "Error creating audio track - General Exception");
					} finally {
						IOUtilities.closeStream(inputRandomAccessFile);
						IOUtilities.closeStream(pcmStream);
					}

					// then add to the MOV output file (pcmAudioStream is closed in MovWriter)
					AudioInputStream pcmAudioStream = new AudioInputStream(new FileInputStream(tempOutputAudioFile),
							audioFormat, (int) ((audioFormat.getSampleRate() * audioDuration) / 1000f));
					outputFileWriter.addAudioTrack(pcmAudioStream, frameStartTime / 1000f,
							(frameStartTime + audioDuration) / 1000f);

					tempOutputAudioFile.delete();
					audioLengthIndex += 1;
				}

				frameStartTime += frameDuration;
			}

			// add the visual content
			boolean imageLoaded;
			int imageBitmapLeft;
			int imageBitmapTop;
			for (FrameMediaContainer frame : framesToSend) {

				imageLoaded = false;
				baseCanvas.drawColor(backgroundColour);

				// TODO: can only reliably use JPG images currently
				if (frame.mImagePath != null && frame.mImagePath.endsWith(MediaUtilities.MOV_IMAGE_FILE_EXTENSION)) {
					// scale image size to make sure it is small enough to fit in the container
					imageBitmap = BitmapUtilities.loadAndCreateScaledBitmap(frame.mImagePath, outputWidth,
							outputHeight, BitmapUtilities.ScalingLogic.FIT, true);

					imageBitmapLeft = Math.round((outputWidth - imageBitmap.getWidth()) / 2f);
					imageBitmapTop = Math.round((outputHeight - imageBitmap.getHeight()) / 2f);
					baseCanvas.drawBitmap(imageBitmap, imageBitmapLeft, imageBitmapTop, basePaint);

					imageLoaded = true;
				}

				if (!TextUtils.isEmpty(frame.mTextContent)) {
					BitmapUtilities.drawScaledText(frame.mTextContent, baseCanvas, basePaint,
							(imageLoaded ? textColourWithImage : textColourNoImage),
							(imageLoaded ? textBackgroundColour : 0), textSpacing, textCornerRadius, imageLoaded, 0,
							baseBitmap.getWidth(), baseBitmap.getHeight(), textMaxFontSize, textMaxCharsPerLine);

				} else if (!imageLoaded) {
					// quicker to do this than load the SVG for narratives that have no audio
					if (audioSVG == null) {
						audioSVG = SVGParser.getSVGFromResource(res, audioResourceId);
					}

					// we can't use PNG compression reliably in the MOV file, so convert to JPEG
					baseCanvas.drawPicture(audioSVG.getPicture(), new RectF(audioBitmapLeft, audioBitmapTop,
							audioBitmapLeft + audioBitmapSize, audioBitmapTop + audioBitmapSize));
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

		if (!fileError) {
			filesToSend.add(Uri.fromFile(outputFile));
			return filesToSend;
		}
		filesToSend.clear();
		return filesToSend;
	}
}
