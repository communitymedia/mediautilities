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
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.webkit.MimeTypeMap;

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

public class HTMLUtilities {

	public static ArrayList<FrameMediaContainer> getHTMLFrameList(File htmlFile, int sequenceIncrement) {
		// TODO: this
		return new ArrayList<>();
	}

	public static ArrayList<Uri> generateNarrativeHTML(Resources res, File outputFile,
													   ArrayList<FrameMediaContainer> framesToSend,
													   Map<Integer, Object> settings) {

		ArrayList<Uri> filesToSend = new ArrayList<>();
		if (framesToSend == null || framesToSend.isEmpty()) {
			return filesToSend;
		}
		boolean fileError = false;

		final String partsIdentifier = "[PARTS]";
		final String audioIconIdentifier = "[AUDIO-ICON]";
		// final String widthIdentifier = "[WIDTH]";
		// final String heightIdentifier = "[HEIGHT]";

		// should really do proper checking on these (no-longer needed as output is fully responsive)
		// final int outputWidth = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_WIDTH);
		// final int outputHeight = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_HEIGHT);

		String audioIcon = IOUtilities.getFileContents(res.openRawResource(R.raw.ic_audio_playback));
		audioIcon = audioIcon.replace("\n", "").replace('"', '\'').replace("#", "%23");

		InputStream playerFileTemplateStream = res.openRawResource(R.raw.html_player);
		BufferedReader playerFileTemplateReader = new BufferedReader(new InputStreamReader(playerFileTemplateStream));
		BufferedWriter playerOutputFileWriter = null;
		String readLine;
		try {
			playerOutputFileWriter = new BufferedWriter(new FileWriter(outputFile));
			while ((readLine = playerFileTemplateReader.readLine()) != null) {
				if (readLine.contains(partsIdentifier)) {

					// find all the story components
					// TODO: currently we repeatedly add spanning media, rather than truly spanning - is it worth fixing this?
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
							BitmapFactory.Options imageDimensions = BitmapUtilities.getImageDimensions(frame.mImagePath);
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

							String fileExtension = IOUtilities.getFileExtension(frame.mImagePath);
							String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
							if (TextUtils.isEmpty(mimeType)) {
								mimeType = "image/jpeg";
							}
							playerOutputFileWriter.write(" src=\"data:" + mimeType + ";base64,");
							playerOutputFileWriter.write(
									Base64.encodeToString(IOUtilities.readFileToByteArray(frame.mImagePath), 0));
							playerOutputFileWriter.write(
									"\" alt=\"" + frame.mFrameId + "\" data-frame-duration=\"" + frame.mFrameMaxDuration +
											"\">\n");
							imageLoaded = true;
						}

						if (!TextUtils.isEmpty(frame.mTextContent)) {
							String textPositioning = "";
							if (!imageLoaded) {
								textPositioning = " class=\"full-screen\"";
							}

							playerOutputFileWriter.write(
									"<p" + textPositioning + " data-frame-duration=\"" + frame.mFrameMaxDuration + "\">");
							playerOutputFileWriter.write(frame.mTextContent.replace("\n", "<br>"));
							playerOutputFileWriter.write("</p>\n");
							textLoaded = true;
						}

						int audioIndex = 0;
						for (String audioPath : frame.mAudioPaths) {
							if (!audioLoaded && !imageLoaded && !textLoaded) {
								// must be before audio TODO: does this affect text?
								playerOutputFileWriter.write(
										"<img class=\"audio-icon\" src=\"data:image/svg+xml,<svg xmlns='http://www.w3" +
												".org/2000/svg'/>\">\n");
							}

							String fileExtension = IOUtilities.getFileExtension(audioPath);
							String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
							if (TextUtils.isEmpty(mimeType)) {
								mimeType = "audio/mpeg";
							} else {
								// MimeTypeMap sometimes wrongly assumes video; all of our content here is audio
								mimeType = mimeType.replace("video/", "audio/");
							}
							playerOutputFileWriter.write("<audio src=\"data:" + mimeType + ";base64,");
							playerOutputFileWriter.write(Base64.encodeToString(IOUtilities.readFileToByteArray(audioPath), 0));
							playerOutputFileWriter.write("\" data-frame-duration=\"" + frame.mFrameMaxDuration + "\"" +
									(frame.mSpanningAudioIndex == audioIndex ?
											(" data-audio-start=\"" + frame.mSpanningAudioStart + "\"") : "") + "></audio" +
									">\n");
							audioLoaded = true;
							audioIndex += 1;
						}

						playerOutputFileWriter.write("</span>\n");

					}

				} else {
					// readLine = readLine.replace(widthIdentifier, Integer.toString(outputWidth));
					// readLine = readLine.replace(heightIdentifier, Integer.toString(outputHeight));
					readLine = readLine.replace(audioIconIdentifier, audioIcon);
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

		//noinspection RedundantOperationOnEmptyContainer
		filesToSend.clear();
		return filesToSend;
	}
}
