/*
 * @(#)JPEGMovWriter.java
 *
 * $Date: 2012-03-14 21:56:33 +0000 (Wed, 14 Mar 2012) $
 *
 * Copyright (c) 2012 by Jeremy Wood.
 * All rights reserved.
 *
 * The copyright of this software is owned by Jeremy Wood. 
 * You may not use, copy or modify this software, except in  
 * accordance with the license agreement you entered into with  
 * Jeremy Wood. For details see accompanying license terms.
 * 
 * This software is probably, but not necessarily, discussed here:
 * http://javagraphics.java.net/
 * 
 * That site should also contain the most recent official version
 * of this software.  (See the SVN repository for more details.)
 */
package ac.robinson.mov;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Map;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;

import com.bric.qt.io.VideoSampleDescriptionEntry;

/**
 * A MovWriter that encodes frames as a series of JPEG images.
 */
public class JPEGMovWriter extends MovWriter {

	private static final float DEFAULT_JPG_QUALITY = .85f;

	/**
	 * This property is used to determine the JPG image quality. It is a float between [0, 1], where 1 is a lossless
	 * image. This value should be the key in a key/value pair in the Map passed to <code>addFrame(..)</code>.
	 */
	public static final String PROPERTY_QUALITY = "jpeg-quality";

	float defaultQuality;

	public JPEGMovWriter(File file) throws IOException {
		this(file, DEFAULT_JPG_QUALITY);
	}

	/**
	 * 
	 * @param file the destination file to write to.
	 * @param defaultQuality the default JPEG quality (from [0,1]) to use if a frame is added without otherwise
	 *            specifying this value.
	 * @throws IOException
	 */
	public JPEGMovWriter(File file, float defaultQuality) throws IOException {
		super(file);
		this.defaultQuality = defaultQuality;
	}

	@Override
	protected VideoSampleDescriptionEntry getVideoSampleDescriptionEntry() {
		return VideoSampleDescriptionEntry.createJPEGDescription(videoTrack.w, videoTrack.h);
	}

	/**
	 * Add an image to this animation using a specific jpeg compression quality.
	 * 
	 * @param duration the duration (in seconds) of this frame
	 * @param bi the image to add
	 * @param jpegQuality a value from [0,1] indicating the quality of this image. A value of 1 represents a losslessly
	 *            encoded image.
	 * @throws IOException
	 */
	@SuppressLint("UseValueOf")
	public synchronized void addFrame(float duration, Bitmap bi, float jpegQuality) throws IOException {
		Map<String, Object> settings = new Hashtable<String, Object>(1);
		settings.put(PROPERTY_QUALITY, new Float(jpegQuality));
		super.addFrame(duration, bi, settings);
	}

	@Override
	protected void writeFrame(OutputStream out, Bitmap image, Map<String, Object> settings) throws IOException {
		// if (image.getType() == BufferedImage.TYPE_INT_ARGB || image.getType() == BufferedImage.TYPE_INT_ARGB_PRE)
		// Log.d(TAG, "Warning: type TYPE_INT_ARGB may produce unexpected output. Recommended type: TYPE_INT_RGB.");
		float quality;
		if (settings != null && settings.get(PROPERTY_QUALITY) instanceof Number) {
			quality = ((Number) settings.get(PROPERTY_QUALITY)).floatValue();
		} else if (settings != null && settings.get(PROPERTY_QUALITY) instanceof String) {
			quality = Float.parseFloat((String) settings.get(PROPERTY_QUALITY));
		} else {
			quality = defaultQuality;
		}

		image.compress(Bitmap.CompressFormat.JPEG, (int) quality, out);
	}
}
