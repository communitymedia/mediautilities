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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;

import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.IOUtilities;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

public class HTMLUtilities {

	public static ArrayList<FrameMediaContainer> getHTMLFrameList(File htmlFile, int sequenceIncrement) {
		// TODO: this
		return new ArrayList<FrameMediaContainer>();
	}

	public static ArrayList<Uri> generateNarrativeHTML(Resources res, File outputFile,
			ArrayList<FrameMediaContainer> framesToSend, Map<Integer, Object> settings) {

		ArrayList<Uri> filesToSend = new ArrayList<Uri>();
		if (framesToSend == null || framesToSend.size() <= 0) {
			return filesToSend;
		}
		boolean fileError = false;

		final String partsIdentifier = "[PARTS]";
		final String widthIdentifier = "[WIDTH]";
		final String heightIdentifier = "[HEIGHT]";
		final String halfWidthIdentifier = "[HALF-WIDTH]";
		final String halfHeightIdentifier = "[HALF-HEIGHT]";
		final String playerBarHeightIdentifier = "[PLAYER-BAR-HEIGHT]";
		final String playButtonIconIdentifier = "[PLAY-BUTTON-ICON]";
		final String pauseButtonIconIdentifier = "[PAUSE-BUTTON-ICON]";

		// should really do proper checking on these
		final int outputWidth = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_WIDTH);
		final int outputHeight = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_HEIGHT);
		int playerBarAdjustment = 0; // loaded from first line of HTML input file
		String playButtonIcon = "";
		String pauseButtonIcon = "";

		InputStream playerFileTemplateStream = res.openRawResource(R.raw.html_player);
		BufferedReader playerFileTemplateReader = new BufferedReader(new InputStreamReader(playerFileTemplateStream));
		BufferedWriter playerOutputFileWriter = null;
		String readLine = null;
		try {
			playerOutputFileWriter = new BufferedWriter(new FileWriter(outputFile));
			while ((readLine = playerFileTemplateReader.readLine()) != null) {
				if (readLine.contains(playerBarHeightIdentifier) && playerBarAdjustment == 0) {
					try {
						playerBarAdjustment = Integer.parseInt(readLine.replace(playerBarHeightIdentifier, ""));
					} catch (Throwable t) {
					}
				} else if (readLine.contains(playButtonIconIdentifier) && "".equals(playButtonIcon)) {
					playButtonIcon = readLine.replace(playButtonIconIdentifier, "");
				} else if (readLine.contains(pauseButtonIconIdentifier) && "".equals(pauseButtonIcon)) {
					pauseButtonIcon = readLine.replace(pauseButtonIconIdentifier, "");
				} else if (readLine.contains(partsIdentifier)) {

					// find all the story components
					int frameId = 0;
					boolean imageLoaded;
					boolean textLoaded;
					boolean audioLoaded;
					for (FrameMediaContainer frame : framesToSend) {

						frameId += 1;
						imageLoaded = false;
						textLoaded = false;
						audioLoaded = false;

						playerOutputFileWriter.write("<span id=\"" + frameId + "\">\n");

						if (frame.mImagePath != null) {
							playerOutputFileWriter.write("<img class=\"");

							// TODO: remove region stuff and just fit images properly in the html using percentages
							String displayMediaRegion = "portrait";
							int orientation = BitmapUtilities.getImageRotationDegrees(frame.mImagePath);
							BitmapFactory.Options imageDimensions = BitmapUtilities
									.getImageDimensions(frame.mImagePath);
							if (orientation == 0) {
								if (imageDimensions.outWidth > imageDimensions.outHeight) {
									displayMediaRegion = "landscape";
								}
							} else if (orientation == 90 || orientation == 270) {
								displayMediaRegion = "landscape";
							}

							playerOutputFileWriter.write(displayMediaRegion + "\"");
							// for images larger than the html it's best to use max-width for scaling automatically
							// playerOutputFileWriter.write(" width=\"" + imageDimensions.outWidth + "\" height=\""
							// + imageDimensions.outHeight + "\"");
							playerOutputFileWriter.write(" src=\"data:image/jpeg;base64,");
							playerOutputFileWriter.write(Base64.encodeToString(
									IOUtilities.readFileToByteArray(frame.mImagePath), 0));
							playerOutputFileWriter.write("\" alt=\"" + frame.mFrameId + "\">\n");
							imageLoaded = true;
						}

						if (!TextUtils.isEmpty(frame.mTextContent)) {
							String textPositioning = "";
							if (!imageLoaded) {
								textPositioning = " class=\"full-screen\"";
							}

							playerOutputFileWriter.write("<p" + textPositioning + ">");
							playerOutputFileWriter.write(frame.mTextContent.replace("\n", "<br>"));
							playerOutputFileWriter.write("</p>\n");
							textLoaded = true;
						}

						// TODO: need to add durations for re-importing
						for (String audioPath : frame.mAudioPaths) {
							if (!audioLoaded && !imageLoaded && !textLoaded) {
								// must be before audio TODO: does this affect text?
								playerOutputFileWriter.write("<img class=\"audio-icon\" alt=\"audio-icon\">\n");
							}

							// TODO: use the correct type for other files (e.g. mp3)
							playerOutputFileWriter.write("<audio src=\"data:audio/mpeg;base64,");
							playerOutputFileWriter.write(Base64.encodeToString(
									IOUtilities.readFileToByteArray(audioPath), 0));
							playerOutputFileWriter.write("\"></audio>\n");
							audioLoaded = true;
						}

						playerOutputFileWriter.write("</span>\n");

					}

				} else {
					readLine = readLine.replace(widthIdentifier, Integer.toString(outputWidth));
					readLine = readLine.replace(heightIdentifier, Integer.toString(outputHeight));
					readLine = readLine.replace(halfWidthIdentifier, Integer.toString(outputWidth / 2));
					readLine = readLine.replace(halfHeightIdentifier,
							Integer.toString((outputHeight + playerBarAdjustment) / 2));
					readLine = readLine.replace(playButtonIconIdentifier, playButtonIcon);
					readLine = readLine.replace(pauseButtonIconIdentifier, pauseButtonIcon);
					playerOutputFileWriter.write(readLine + '\n');
				}
			}
		} catch (IOException e) {
			fileError = true; // these are the only places where errors really matter
		} catch (Throwable t) {
			fileError = true; // these are the only places where errors really matter
		} finally {
			IOUtilities.closeStream(playerOutputFileWriter);
			IOUtilities.closeStream(playerFileTemplateReader);
			IOUtilities.closeStream(playerFileTemplateStream);
		}

		if (!fileError) {
			filesToSend.add(Uri.fromFile(outputFile));
			return filesToSend;
		}
		filesToSend.clear();
		return filesToSend;
	}
}
