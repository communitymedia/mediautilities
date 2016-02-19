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
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlSerializer;

import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.IOUtilities;
import ac.robinson.util.ImageCacheUtilities;
import ac.robinson.util.StringUtilities;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Xml;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

public class SMILUtilities {

	public static final String SMIL_MILLISECOND_STRING = "ms";
	public static final String SMIL_BACK_IMAGE_STRING = "back-";
	public static final String SMIL_FRONT_IMAGE_STRING = "front-";

	private static final String COMPONENT_FILE_NAME_WITH_ID = "%s-%d-%d.%s";
	private static final String COMPONENT_FILE_NAME_WITHOUT_ID = "%s-%d.%s";

	/**
	 * Get a list of the additional files that are part of a SMIL narrative file
	 * 
	 * @param smilFile The SMIL file to parse
	 * @param includeNonMediaElements Whether to include those files that are not actual content (i.e. background
	 *            images, etc.)
	 * @return The files used in the SMIL narrative
	 */
	public static ArrayList<String> getSimpleSMILFileList(File smilFile, boolean includeNonMediaElements) {

		ArrayList<String> smilContents = new ArrayList<String>();

		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		try {

			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document smilDocument = documentBuilder.parse(smilFile);

			Element documentElement = smilDocument.getDocumentElement();

			// get a nodelist of elements
			NodeList nodeList = documentElement.getElementsByTagName("par");
			for (int i = 0, n = nodeList.getLength(); i < n; i++) {

				Element parElement = (Element) nodeList.item(i);
				NodeList mediaElements = parElement.getChildNodes();
				for (int j = 0, o = mediaElements.getLength(); j < o; j++) {

					if (mediaElements.item(j).getNodeType() == Node.ELEMENT_NODE) {
						Element mediaElement = (Element) mediaElements.item(j);
						String sourceFile = mediaElement.getAttribute("src");

						// some items are just meta data and should be ignored
						if ("false".equals(mediaElement.getAttribute("is-media"))
								|| "blank".equals(parElement.getAttribute("id"))) {
							if (includeNonMediaElements) {
								if (!smilContents.contains(sourceFile)) {
									smilContents.add(sourceFile);
								}
							}
						} else if ("text-media".equals(mediaElements.item(j).getNodeName())) {
							// do we want to do anything with text content?
						} else {
							if (!smilContents.contains(sourceFile)) {
								smilContents.add(sourceFile);
							}
						}
					}
				}
			}

		} catch (ParserConfigurationException e) {
			return null;
		} catch (SAXException e) {
			return null;
		} catch (IOException e) {
			return null;
		} catch (NullPointerException e) {
			return null;
		}

		return smilContents;
	}

	public static ArrayList<FrameMediaContainer> getSMILFrameList(File smilFile, int sequenceIncrement,
			boolean deleteNonMediaElements) {
		return getSMILFrameList(smilFile, sequenceIncrement, deleteNonMediaElements, 0, true);
	}

	/**
	 * Get a list of the frames in a SMIL narrative file. Each frame is returned in a FrameMediaContainer, and non-media
	 * element files are deleted from the file system
	 * 
	 * @param smilFile The SMIL file to parse
	 * @return The frames of the SMIL narrative
	 */
	public static ArrayList<FrameMediaContainer> getSMILFrameList(File smilFile, int sequenceIncrement,
			boolean deleteNonMediaElements, int frameLimit, boolean validateAudioLengths) {

		ArrayList<FrameMediaContainer> smilContents = new ArrayList<FrameMediaContainer>();
		ArrayList<File> ignoredFiles = new ArrayList<File>();

		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		try {

			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document smilDocument = documentBuilder.parse(smilFile);

			Element documentElement = smilDocument.getDocumentElement();

			// get a nodelist of elements
			NodeList nodeList = documentElement.getElementsByTagName("par");
			int frameSequenceId = 0;
			int n = nodeList.getLength();
			frameLimit = frameLimit <= 0 ? n : frameLimit;
			for (int i = 0; i < n && i < frameLimit; i++) {

				Element parElement = (Element) nodeList.item(i);
				FrameMediaContainer currentFrame = new FrameMediaContainer(parElement.getAttribute("id"),
						frameSequenceId);

				NodeList mediaElements = parElement.getChildNodes();
				for (int j = 0, o = mediaElements.getLength(); j < o; j++) {

					if (mediaElements.item(j).getNodeType() == Node.ELEMENT_NODE) {
						Element mediaElement = (Element) mediaElements.item(j);
						String elementId = mediaElement.getAttribute("id");
						String elementRegion = mediaElement.getAttribute("region");
						String elementSrc = mediaElement.getAttribute("src");
						int elementDuration = getDurationFromString(mediaElement.getAttribute("dur"));

						if ("text-media".equals(mediaElements.item(j).getNodeName())) {
							currentFrame.addTextFromSMIL(mediaElement.getTextContent(), elementId, elementDuration);

						} else if (!TextUtils.isEmpty(elementSrc)) {
							File sourceFile = new File(smilFile.getParent(), elementSrc);

							// some items are just meta data and should be ignored
							// "blank" is a blank frame at the end of the narrative
							if ("false".equals(mediaElement.getAttribute("is-media"))
									|| "blank".equals(parElement.getAttribute("id"))) {
								if (!smilContents.contains(sourceFile)) {
									ignoredFiles.add(sourceFile);
								}
								currentFrame.updateFrameMaxDuration(elementDuration);

							} else {
								currentFrame.addMediaFromSMIL(mediaElements.item(j).getNodeName(), sourceFile,
										elementId, elementDuration, elementRegion, validateAudioLengths);
							}
						}
					}
				}

				if (!"blank".equals(currentFrame.mFrameId)) {
					smilContents.add(currentFrame);
					frameSequenceId += sequenceIncrement;
				}
			}

		} catch (ParserConfigurationException e) {
			return null;
		} catch (SAXException e) {
			return null;
		} catch (IOException e) {
			return null;
		} catch (NullPointerException e) {
			return null;
		}

		if (deleteNonMediaElements) {
			for (File ignoredFile : ignoredFiles) {
				ignoredFile.delete();
			}
		}
		return smilContents;
	}

	public static int getDurationFromString(String mediaDuration) {
		if (!TextUtils.isEmpty(mediaDuration)) {
			if (mediaDuration.endsWith(SMILUtilities.SMIL_MILLISECOND_STRING)) {
				mediaDuration = mediaDuration.substring(0, mediaDuration.length()
						- SMILUtilities.SMIL_MILLISECOND_STRING.length());
				int newMediaDuration = StringUtilities.safeStringToInteger(mediaDuration);
				return newMediaDuration;
			}
		}
		return 0;
	}

	/**
	 * Output files are put in the same directory as outputFile
	 * 
	 * @param res
	 * @param outputFile
	 * @param framesToSend
	 * @return
	 */
	public static ArrayList<Uri> generateNarrativeSMIL(Resources res, File outputFile,
			ArrayList<FrameMediaContainer> framesToSend, Map<Integer, Object> settings) {

		ArrayList<Uri> filesToSend = new ArrayList<Uri>();
		if (framesToSend == null || framesToSend.size() <= 0) {
			return filesToSend;
		}
		boolean fileError = false;

		// should really do proper checking on these
		final int outputWidth = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_WIDTH);
		final int outputHeight = (Integer) settings.get(MediaUtilities.KEY_OUTPUT_HEIGHT);
		final int playerBarAdjustment = (Integer) settings.get(MediaUtilities.KEY_PLAYER_BAR_ADJUSTMENT);
		final int backgroundColour = (Integer) settings.get(MediaUtilities.KEY_BACKGROUND_COLOUR);
		final int textColourWithImage = (Integer) settings.get(MediaUtilities.KEY_TEXT_COLOUR_WITH_IMAGE);
		final int textColourNoImage = (Integer) settings.get(MediaUtilities.KEY_TEXT_COLOUR_NO_IMAGE);
		final int textBackgroundColour = (Integer) settings.get(MediaUtilities.KEY_TEXT_BACKGROUND_COLOUR);
		final int textSpacing = (Integer) settings.get(MediaUtilities.KEY_TEXT_SPACING);
		final int textCornerRadius = (Integer) settings.get(MediaUtilities.KEY_TEXT_CORNER_RADIUS);
		final boolean textBackgroundSpanWidth = (Boolean) settings.get(MediaUtilities.KEY_TEXT_BACKGROUND_SPAN_WIDTH);
		final int textMaxFontSize = (Integer) settings.get(MediaUtilities.KEY_MAX_TEXT_FONT_SIZE);
		final int textMaxCharsPerLine = (Integer) settings.get(MediaUtilities.KEY_MAX_TEXT_CHARACTERS_PER_LINE);
		final int textMaxHeightWithImage = (Integer) settings.get(MediaUtilities.KEY_MAX_TEXT_HEIGHT_WITH_IMAGE);
		final int audioResourceId = (Integer) settings.get(MediaUtilities.KEY_AUDIO_RESOURCE_ID);

		// start the XML (before adding so we know if there's a problem)
		BufferedWriter smilFileWriter = null;
		try {
			smilFileWriter = new BufferedWriter(new FileWriter(outputFile));
		} catch (Throwable t) {
			IOUtilities.closeStream(smilFileWriter);
			return filesToSend; // error - can't write
		}

		// add the smil file first so that the receiving application can parse it before receiving the other files
		filesToSend.add(Uri.fromFile(outputFile));
		File outputDirectory = outputFile.getParentFile();

		String narrativeName = outputFile.getName();
		narrativeName = narrativeName
				.substring(0, narrativeName.length() - MediaUtilities.SMIL_FILE_EXTENSION.length());

		// add a duplicate of the player because some devices have a pointless whitelist of incoming file extensions (!)
		File syncFile = new File(outputDirectory, String.format("%s%s", narrativeName,
				MediaUtilities.SYNC_FILE_EXTENSION));
		filesToSend.add(Uri.fromFile(syncFile));

		final File storyPlayerFile = new File(outputDirectory, String.format("play-%s.html", narrativeName));

		// copy the audio and background images
		int audioBitmapSize = Math.min(outputWidth, outputHeight);
		int audioBitmapLeft = Math.round((outputWidth - audioBitmapSize) / 2f);
		int audioBitmapTop = Math.round((outputHeight - audioBitmapSize) / 2f);
		File tempAudioIconFile = new File(outputDirectory, String.format("%s-audio.png", narrativeName));
		Bitmap audioIconBitmap = Bitmap.createBitmap(audioBitmapSize, audioBitmapSize,
				ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);

		// would be better to use data:image/png URI, but this breaks Quicktime playback
		File tempBackgroundFile = new File(outputDirectory, String.format("%s-background.png", narrativeName));
		Bitmap backgroundBitmap = Bitmap
				.createBitmap(2, 2, ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		backgroundBitmap.eraseColor(backgroundColour);
		if (BitmapUtilities.saveBitmap(backgroundBitmap, Bitmap.CompressFormat.PNG, 100, tempBackgroundFile)) {
			filesToSend.add(Uri.fromFile(tempBackgroundFile));
		} // if this fails, backgrounds will bleed through from other frames

		// create the SMIL file
		try {
			// see: http://service.real.com/help/library/guides/production/htmfiles/smil.htm
			XmlSerializer smilSerializer = Xml.newSerializer();
			String rootNamespace = "http://www.w3.org/2001/SMIL20/Language";
			String tagNamespace = null;
			smilSerializer.setOutput(smilFileWriter);
			smilSerializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
			// these don't work (Java bug)
			// smilSerializer.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-indentation", "\t");
			// smilSerializer.setFeature("http://xmlpull.org/v1/doc/features.html#serializer-attvalue-use-apostrophe",
			// false);
			smilSerializer.startDocument("utf-8", null);
			smilSerializer.setPrefix("", rootNamespace);
			smilSerializer.startTag(rootNamespace, "smil");
			smilSerializer.attribute(tagNamespace, "xmlns:qt",
					"http://www.apple.com/quicktime/resources/smilextensions");
			smilSerializer.attribute(tagNamespace, "qt:autoplay", "true");
			smilSerializer.attribute(tagNamespace, "qt:time-slider", "true");

			// at some point, should probably check header with transitions/clipping/size etc to sort out aspect ratios
			smilSerializer.startTag(tagNamespace, "head");
			smilSerializer.startTag(tagNamespace, "layout");

			smilSerializer.startTag(tagNamespace, "root-layout");
			smilSerializer.attribute(tagNamespace, "id", "slideshow");
			smilSerializer.attribute(tagNamespace, "width", Integer.toString(outputWidth));
			smilSerializer.attribute(tagNamespace, "height", Integer.toString(outputHeight));
			smilSerializer.attribute(tagNamespace, "background-color", String.format("#%x", backgroundColour));
			smilSerializer.endTag(tagNamespace, "root-layout");

			// first run is to get image dimension regions
			for (FrameMediaContainer frame : framesToSend) {
				if (frame.mImagePath != null && new File(frame.mImagePath).exists()) {

					// scale the image dimensions for coping with smil "meet" scaling
					BitmapFactory.Options imageDimensions = BitmapUtilities.getImageDimensions(frame.mImagePath);
					int maxDimension = Math.max(imageDimensions.outWidth, imageDimensions.outHeight);
					Point imageSize = new Point(Math.round((outputWidth / (float) maxDimension)
							* imageDimensions.outWidth), Math.round((outputHeight / (float) maxDimension)
							* imageDimensions.outHeight));

					addRegion(smilSerializer, tagNamespace, getImageRegion(frame),
							Integer.toString(Math.round((outputWidth - imageSize.x) / 2f)),
							Integer.toString(Math.round((outputHeight - imageSize.y) / 2f)), "100%", "100%", "meet");
				}
			}

			addRegion(smilSerializer, tagNamespace, "fill-area", "0", "0", "100%", "100%", "fill");
			addRegion(smilSerializer, tagNamespace, "audio-icon",
					Integer.toString((outputWidth - audioIconBitmap.getWidth()) / 2),
					Integer.toString((outputHeight - audioIconBitmap.getHeight()) / 2),
					Integer.toString(audioIconBitmap.getWidth()), Integer.toString(audioIconBitmap.getHeight()), "fill");
			// TODO: top of 540 into attrs
			addRegion(smilSerializer, tagNamespace, "text-subtitles", "0", "540", "100%", "100%", "meet");

			smilSerializer.endTag(tagNamespace, "layout");
			smilSerializer.endTag(tagNamespace, "head");

			smilSerializer.startTag(tagNamespace, "body");
			smilSerializer.startTag(tagNamespace, "seq");
			smilSerializer.attribute(tagNamespace, "id", framesToSend.get(0).mParentId == null ? UUID.randomUUID()
					.toString() : framesToSend.get(0).mParentId);

			// find all the story components
			boolean imageLoaded;
			boolean textLoaded;
			int audioId;
			String displayMedia;
			String displayMediaRegion;
			boolean narrativeHasAudio = false;
			File savedFile = null;
			for (FrameMediaContainer frame : framesToSend) {

				imageLoaded = false;
				textLoaded = false;

				smilSerializer.startTag(tagNamespace, "par");
				smilSerializer.attribute(tagNamespace, "id", frame.mFrameId);

				displayMedia = tempAudioIconFile.getName();
				displayMediaRegion = "audio-icon";

				if (frame.mImagePath != null && new File(frame.mImagePath).exists()) {
					// output files must be in a public directory for sending (/data/ directory will *not* work)
					if (IOUtilities.isInternalPath(frame.mImagePath)) { // so we can send private files
						savedFile = copySmilFileToOutput(frame.mImagePath, outputDirectory, narrativeName,
								frame.mFrameSequenceId, 0, IOUtilities.getFileExtension(frame.mImagePath));
					} else {
						savedFile = new File(frame.mImagePath);
					}
					if (savedFile != null) {
						filesToSend.add(Uri.fromFile(savedFile));
						displayMedia = savedFile.getName();
						displayMediaRegion = getImageRegion(frame);
						imageLoaded = true;
					}
				}

				audioId = 0;
				for (String audioPath : frame.mAudioPaths) {
					if (audioPath != null && new File(audioPath).exists()) {
						// output files must be in a public directory for sending (/data/ directory will *not* work)
						if (IOUtilities.isInternalPath(audioPath)) { // so we can send private files
							savedFile = copySmilFileToOutput(audioPath, outputDirectory, narrativeName,
									frame.mFrameSequenceId, audioId + 1, IOUtilities.getFileExtension(audioPath));
						} else {
							savedFile = new File(audioPath);
						}
						if (savedFile != null) {
							filesToSend.add(Uri.fromFile(savedFile));
							addSmilTag(smilSerializer, tagNamespace, "audio", savedFile.getName(),
									frame.mAudioDurations.get(audioId), null, true);
							audioId += 1;
							narrativeHasAudio = true;
						}
					}
				}

				if (!TextUtils.isEmpty(frame.mTextContent)) {
					Bitmap textBitmap = Bitmap.createBitmap(outputWidth, outputHeight,
							ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
					Canvas textBitmapCanvas = new Canvas(textBitmap);
					Paint textBitmapPaint = BitmapUtilities.getPaint(textColourNoImage, 1);
					textBitmapPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

					// TODO: the text background isn't really necessary here, as transparency breaks SMIL so all text
					// background is black by default... remove? (but bear in mind height is only calculated properly
					// when there's a background to draw)
					int textHeight = BitmapUtilities.drawScaledText(frame.mTextContent, textBitmapCanvas,
							textBitmapPaint, (imageLoaded ? textColourWithImage : textColourNoImage),
							(imageLoaded ? textBackgroundColour : 0), textSpacing, textCornerRadius, imageLoaded, 0,
							textBackgroundSpanWidth, textMaxHeightWithImage, textMaxFontSize, textMaxCharsPerLine);

					// crop to the actual size of the text to show as much as possible of the image
					Bitmap textBitmapCropped;
					if (imageLoaded) {
						textBitmapCropped = Bitmap.createBitmap(textBitmap, 0, outputHeight - textHeight - 1,
								outputWidth, textHeight, null, false);
					} else {
						textBitmapCropped = textBitmap;
					}

					// savedFile = new File(outputDirectory, getFormattedFileName(narrativeName, frame.mFrameSequenceId,
					// 0, "png"));
					savedFile = new File(outputDirectory, frame.mFrameId + ".png");
					BitmapUtilities.saveBitmap(textBitmapCropped, Bitmap.CompressFormat.PNG, 100, savedFile);
					textBitmapCanvas = null;

					filesToSend.add(Uri.fromFile(savedFile));
					textLoaded = true;
				}

				// clear the background - could use a SMIL brush, but the Quicktime plugin doesn't recognise these
				addSmilTag(smilSerializer, tagNamespace, "img", tempBackgroundFile.getName(), frame.mFrameMaxDuration,
						"fill-area", false);

				if (imageLoaded) {
					addSmilTag(smilSerializer, tagNamespace, "img", displayMedia, frame.mFrameMaxDuration,
							displayMediaRegion, true);
					if (textLoaded) {
						addSmilTag(smilSerializer, tagNamespace, "img", savedFile.getName(), frame.mFrameMaxDuration,
								"text-subtitles", false);
						addTextTag(smilSerializer, tagNamespace, frame.mTextContent);
					}
				} else {
					if (textLoaded) {
						addSmilTag(smilSerializer, tagNamespace, "img", savedFile.getName(), frame.mFrameMaxDuration,
								"fill-area", false);
						addTextTag(smilSerializer, tagNamespace, frame.mTextContent);
					} else {
						addSmilTag(smilSerializer, tagNamespace, "img", displayMedia, frame.mFrameMaxDuration,
								displayMediaRegion, false);
					}
				}

				smilSerializer.endTag(tagNamespace, "par");
			}

			// blank frame to clear the screen, and also useful for syncing, so we always know all sent files
			smilSerializer.startTag(tagNamespace, "par");
			smilSerializer.attribute(tagNamespace, "id", "blank");
			if (narrativeHasAudio) {
				SVG audioSVG = SVGParser.getSVGFromResource(res, audioResourceId);
				Canvas audioIconCanvas = new Canvas(audioIconBitmap);
				audioIconCanvas.drawPicture(audioSVG.getPicture(), new RectF(audioBitmapLeft, audioBitmapTop,
						audioBitmapLeft + audioBitmapSize, audioBitmapTop + audioBitmapSize));
				if (BitmapUtilities.saveBitmap(audioIconBitmap, Bitmap.CompressFormat.PNG, 100, tempAudioIconFile)) {
					addSmilTag(smilSerializer, tagNamespace, "meta-data", tempAudioIconFile.getName(), 2, "fill-area",
							false);
					filesToSend.add(Uri.fromFile(tempAudioIconFile));
				} // if this fails, audio playback won't have an icon
				audioIconCanvas = null;
			}
			addSmilTag(smilSerializer, tagNamespace, "meta-data", storyPlayerFile.getName(), 2, "fill-area", false);
			addSmilTag(smilSerializer, tagNamespace, "meta-data", syncFile.getName(), 2, "fill-area", false);
			addSmilTag(smilSerializer, tagNamespace, "img", tempBackgroundFile.getName(), 2, "fill-area", false);
			smilSerializer.endTag(tagNamespace, "par");
			smilSerializer.endTag(tagNamespace, "seq");
			smilSerializer.endTag(tagNamespace, "body");
			smilSerializer.endDocument();

			smilFileWriter.flush();

		} catch (IOException e) {
			fileError = true; // these are the only places where errors really matter
		} catch (Throwable t) {
			fileError = true; // these are the only places where errors really matter
		} finally {
			IOUtilities.closeStream(smilFileWriter);
		}

		// copy the sync file
		try {
			IOUtilities.copyFile(outputFile, syncFile);
		} catch (Throwable t) {
			// nothing we can do (syncing to some devices will fail, but playback will be fine)
		}

		// add a player wrapper (HTML)
		InputStream playerFileStream = res.openRawResource(R.raw.smil_player);
		BufferedReader playerFileReader = new BufferedReader(new InputStreamReader(playerFileStream));
		BufferedWriter playerFileWriter = null;
		String readLine = null;
		try {
			playerFileWriter = new BufferedWriter(new FileWriter(storyPlayerFile));
			while ((readLine = playerFileReader.readLine()) != null) {
				readLine = readLine.replace("[SMIL-ID]", outputFile.getName());
				readLine = readLine.replace("[WIDTH]", Integer.toString(outputWidth));
				readLine = readLine.replace("[HEIGHT]", Integer.toString(outputHeight + playerBarAdjustment));
				readLine = readLine.replace("[HALF-WIDTH]", Integer.toString(outputWidth / 2));
				readLine = readLine
						.replace("[HALF-HEIGHT]", Integer.toString((outputHeight + playerBarAdjustment) / 2));
				playerFileWriter.write(readLine + '\n');
			}
			filesToSend.add(Uri.fromFile(storyPlayerFile));
		} catch (Throwable t) {
		} finally {
			// can still export the smil, even if the player fails
			IOUtilities.closeStream(playerFileWriter);
			IOUtilities.closeStream(playerFileReader);
			IOUtilities.closeStream(playerFileStream);
		}

		if (!fileError) {
			return filesToSend;
		}
		filesToSend.clear();
		return filesToSend;
	}

	private static String getFormattedFileName(String narrativeName, int frameId, int mediaId, String fileExtension) {
		String componentNameFormat;
		if (mediaId > 0) {
			componentNameFormat = COMPONENT_FILE_NAME_WITH_ID;
			return String.format(componentNameFormat, narrativeName, frameId, mediaId, fileExtension);
		} else {
			componentNameFormat = COMPONENT_FILE_NAME_WITHOUT_ID;
			return String.format(componentNameFormat, narrativeName, frameId, fileExtension);
		}
	}

	// only used for when we're installed internally TODO: detect automatically?
	private static File copySmilFileToOutput(String sourceFilePath, File outputDirectory, String narrativeName,
			int frameId, int mediaId, String fileExtension) {

		File outputFile = new File(outputDirectory,
				getFormattedFileName(narrativeName, frameId, mediaId, fileExtension));
		try {
			IOUtilities.copyFile(new File(sourceFilePath), outputFile);
			return outputFile;
		} catch (Throwable t) {
		}
		return null;
	}

	private static String getImageRegion(FrameMediaContainer frame) {
		String imageRegion = SMIL_BACK_IMAGE_STRING;
		if (frame.mImageIsFrontCamera) {
			imageRegion = SMIL_BACK_IMAGE_STRING;
		}
		return imageRegion + "image-" + frame.mFrameSequenceId;
	}

	private static void addRegion(XmlSerializer smilSerializer, String tagNamespace, String regionId,
			String regionLeft, String regionTop, String regionWidth, String regionHeight, String regionFit)
			throws IOException {
		smilSerializer.startTag(tagNamespace, "region");
		smilSerializer.attribute(tagNamespace, "id", regionId);
		smilSerializer.attribute(tagNamespace, "top", regionTop);
		smilSerializer.attribute(tagNamespace, "left", regionLeft);
		smilSerializer.attribute(tagNamespace, "width", regionWidth);
		smilSerializer.attribute(tagNamespace, "height", regionHeight);
		smilSerializer.attribute(tagNamespace, "fit", regionFit);
		smilSerializer.endTag(tagNamespace, "region");
	}

	private static void addTextTag(XmlSerializer smilSerializer, String tagNamespace, String textString)
			throws IOException {
		smilSerializer.startTag(tagNamespace, "text-media");
		smilSerializer.text(textString);
		smilSerializer.endTag(tagNamespace, "text-media");
	}

	private static void addSmilTag(XmlSerializer smilSerializer, String tagNamespace, String tagName, String fileName,
			int duration, String region, boolean isMedia) throws IOException {
		smilSerializer.startTag(tagNamespace, tagName);
		smilSerializer.attribute(tagNamespace, "src", fileName);
		smilSerializer.attribute(tagNamespace, "dur", duration + SMIL_MILLISECOND_STRING);
		if (region != null) {
			smilSerializer.attribute(tagNamespace, "region", region);
		}
		smilSerializer.attribute(tagNamespace, "is-media", Boolean.toString(isMedia));
		// plugin has strange flashing issues if this is enabled
		// smilSerializer.attribute(tagNamespace, "qt:composite-mode", "alpha");
		smilSerializer.endTag(tagNamespace, tagName);
	}
}
