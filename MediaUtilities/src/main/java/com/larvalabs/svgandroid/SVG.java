package com.larvalabs.svgandroid;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.PictureDrawable;

/*

 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 */
/**
 * Describes a vector Picture object, and optionally its bounds.
 * 
 * @author Larva Labs, LLC
 */
public class SVG {

	/**
	 * The parsed Picture object.
	 */
	private Picture picture;

	/**
	 * These are the bounds for the SVG specified as a hidden "bounds" layer in the SVG.
	 */
	private RectF bounds;

	/**
	 * These are the estimated bounds of the SVG computed from the SVG elements while parsing. Note that this could be
	 * null if there was a failure to compute limits (ie. an empty SVG).
	 */
	private RectF limits = null;

	/**
	 * Construct a new SVG.
	 * 
	 * @param picture the parsed picture object.
	 * @param bounds the bounds computed from the "bounds" layer in the SVG.
	 */
	SVG(Picture picture, RectF bounds) {
		this.picture = picture;
		this.bounds = bounds;
	}

	/**
	 * Set the limits of the SVG, which are the estimated bounds computed by the parser.
	 * 
	 * @param limits the bounds computed while parsing the SVG, may not be entirely accurate.
	 */
	void setLimits(RectF limits) {
		this.limits = limits;
	}

	/**
	 * Deprecated as it is incompatible with hardware acceleration. Use getBitmap to get a centred, scaled bitmap at the
	 * correct size.
	 * 
	 * Create a picture drawable from the SVG.
	 * 
	 * @return the PictureDrawable.
	 */
	@Deprecated
	public PictureDrawable createPictureDrawable() {
		return new PictureDrawable(picture);
	}

	/**
	 * <b>Caution: do not use on views/surfaces etc in onDraw when hardware acceleration is enabled</b>
	 * 
	 * Get the parsed SVG picture data.
	 * 
	 * @return the picture.
	 */
	public Picture getPicture() {
		return picture;
	}

	/**
	 * Get a Bitmap representation of this SVG picture data at a particular size. The SVG image will be centred and
	 * stretched to fill the bitmap.
	 * 
	 * This method is particularly useful for newer devices where hardware acceleration is on by default, and so
	 * drawPicture does not work in onDraw. See: https://gist.github.com/6ebe5b818652d5ccc27c
	 * 
	 * @return the bitmap.
	 */
	public Bitmap getBitmap(int bitmapWidth, int bitmapHeight) {
		Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Config.ARGB_8888);
		bitmap.setDensity(Bitmap.DENSITY_NONE);

		// centre and make sure to preserve scale factor
		int pictureWidth = picture.getWidth();
		int pictureHeight = picture.getHeight();
		float pictureAspect = (float) pictureWidth / (float) pictureHeight;
		float bitmapAspect = (float) bitmapWidth / (float) bitmapHeight;
		int newDimension = 0;
		int rectOffset = 0;
		Rect drawRect;
		if (pictureAspect > bitmapAspect) {
			newDimension = (int) (bitmapWidth / pictureAspect);
			rectOffset = (bitmapHeight - newDimension) / 2;
			drawRect = new Rect(0, rectOffset, bitmapWidth, newDimension + rectOffset);
		} else {
			newDimension = (int) (bitmapHeight * pictureAspect);
			rectOffset = (bitmapWidth - newDimension) / 2;
			drawRect = new Rect(rectOffset, 0, newDimension + rectOffset, bitmapHeight);
		}

		new Canvas(bitmap).drawPicture(picture, drawRect);
		return bitmap;
	}

	/**
	 * Gets the bounding rectangle for the SVG, if one was specified.
	 * 
	 * @return rectangle representing the bounds.
	 */
	public RectF getBounds() {
		return bounds;
	}

	/**
	 * Gets the bounding rectangle for the SVG that was computed upon parsing. It may not be entirely accurate for
	 * certain curves or transformations, but is often better than nothing.
	 * 
	 * @return rectangle representing the computed bounds.
	 */
	public RectF getLimits() {
		return limits;
	}
}
