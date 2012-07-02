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

package ac.robinson.util;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.ExifInterface;

/**
 * Class containing static utility methods for bitmap decoding and scaling
 * 
 * @author Andreas Agvard (andreas.agvard@sonyericsson.com)
 */
public class BitmapUtilities {

	public static class CacheTypeContainer {
		public Bitmap.CompressFormat type;

		public CacheTypeContainer(Bitmap.CompressFormat type) {
			this.type = type;
		}
	}

	// get screen size by: Display display =
	// ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
	// if using an ImageView etc, remember that the size is zero initially before inflation
	public static Bitmap loadAndCreateScaledBitmap(String imagePath, int dstWidth, int dstHeight,
			ScalingLogic scalingLogic, boolean rotateImage) {

		Matrix imageMatrix = new Matrix();
		if (rotateImage) {
			int rotationNeeded = getImageRotationDegrees(imagePath);
			if (rotationNeeded != 0) {
				imageMatrix.postRotate(rotationNeeded);
			}
		}

		Bitmap unscaledBitmap = decodeFile(imagePath, dstWidth, dstHeight, scalingLogic);
		return createScaledBitmap(unscaledBitmap, dstWidth, dstHeight, scalingLogic, imageMatrix);
	}

	public static Bitmap loadAndCreateScaledResource(Resources res, int resId, int dstWidth, int dstHeight,
			ScalingLogic scalingLogic) {

		Bitmap unscaledBitmap = decodeResource(res, resId, dstWidth, dstHeight, scalingLogic);
		return createScaledBitmap(unscaledBitmap, dstWidth, dstHeight, scalingLogic, new Matrix());
	}

	public static Bitmap loadAndCreateScaledStream(String streamPath, int dstWidth, int dstHeight,
			ScalingLogic scalingLogic) {

		Bitmap unscaledBitmap = decodeStream(streamPath, dstWidth, dstHeight, scalingLogic);
		return createScaledBitmap(unscaledBitmap, dstWidth, dstHeight, scalingLogic, new Matrix());
	}

	public static Bitmap scaleBitmap(Bitmap unscaledBitmap, int dstWidth, int dstHeight, ScalingLogic scalingLogic) {
		return createScaledBitmap(unscaledBitmap, dstWidth, dstHeight, scalingLogic, new Matrix());
	}

	public static int getImageOrientation(String imagePath) {
		ExifInterface exifInterface;
		try {
			exifInterface = new ExifInterface(imagePath);
		} catch (IOException err) {
			return ExifInterface.ORIENTATION_UNDEFINED;
		}
		return exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
	}

	public static int getImageRotationDegrees(String imagePath) {
		int currentRotation = getImageOrientation(imagePath);
		switch (currentRotation) {
			case ExifInterface.ORIENTATION_ROTATE_90:
				return 90;
			case ExifInterface.ORIENTATION_ROTATE_180:
				return 180;
			case ExifInterface.ORIENTATION_ROTATE_270:
				return 270;
		}
		return 0;
	}

	public static Options getImageDimensions(String imagePath) {
		Options options = new Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(imagePath, options);
		return options;
	}

	/**
	 * Utility function for decoding an image resource. The decoded bitmap will be optimized for further scaling to the
	 * requested destination dimensions and scaling logic.
	 * 
	 * @param res The resources object containing the image data
	 * @param resId The resource id of the image data
	 * @param dstWidth Width of destination area
	 * @param dstHeight Height of destination area
	 * @param scalingLogic Logic to use to avoid image stretching
	 * @return Decoded bitmap
	 */
	public static Bitmap decodeResource(Resources res, int resId, int dstWidth, int dstHeight, ScalingLogic scalingLogic) {
		if (dstWidth <= 0 || dstHeight <= 0)
			return null;
		Options options = new Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeResource(res, resId, options);
		options.inJustDecodeBounds = false;
		options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, dstWidth, dstHeight,
				scalingLogic);
		Bitmap unscaledBitmap = BitmapFactory.decodeResource(res, resId, options);

		return unscaledBitmap;
	}

	/**
	 * Utility function for decoding an image file. The decoded bitmap will be optimized for further scaling to the
	 * requested destination dimensions and scaling logic.
	 * 
	 * @param imagePath the file path of the image
	 * @param dstWidth Width of destination area
	 * @param dstHeight Height of destination area
	 * @param scalingLogic Logic to use to avoid image stretching
	 * @return Decoded bitmap
	 */
	public static Bitmap decodeFile(String imagePath, int dstWidth, int dstHeight, ScalingLogic scalingLogic) {
		if (dstWidth <= 0 || dstHeight <= 0)
			return null;
		Options options = new Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(imagePath, options);
		options.inJustDecodeBounds = false;
		options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, dstWidth, dstHeight,
				scalingLogic);
		Bitmap unscaledBitmap = BitmapFactory.decodeFile(imagePath, options);
		return unscaledBitmap;
	}

	/**
	 * Utility function for decoding an image stream. The decoded bitmap will be optimized for further scaling to the
	 * requested destination dimensions and scaling logic.
	 * 
	 * @param streamPath The path of the image stream to decode
	 * @param dstWidth Width of destination area
	 * @param dstHeight Height of destination area
	 * @param scalingLogic Logic to use to avoid image stretching
	 * @return Decoded bitmap, or null on error
	 */
	public static Bitmap decodeStream(String streamPath, int dstWidth, int dstHeight, ScalingLogic scalingLogic) {
		try {
			if (dstWidth <= 0 || dstHeight <= 0)
				return null;
			Options options = new Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(new FileInputStream(streamPath), null, options);
			options.inJustDecodeBounds = false;
			options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, dstWidth, dstHeight,
					scalingLogic);
			Bitmap unscaledBitmap = BitmapFactory.decodeStream(new FileInputStream(streamPath), null, options);

			return unscaledBitmap;
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	/**
	 * Utility function for creating a scaled version of an existing bitmap
	 * 
	 * @param unscaledBitmap Bitmap to scale
	 * @param dstWidth Wanted width of destination bitmap
	 * @param dstHeight Wanted height of destination bitmap
	 * @param scalingLogic Logic to use to avoid image stretching
	 * @return New scaled bitmap object
	 */
	public static Bitmap createScaledBitmap(Bitmap unscaledBitmap, int dstWidth, int dstHeight,
			ScalingLogic scalingLogic, Matrix imageMatrix) {
		if (unscaledBitmap == null)
			return null;
		unscaledBitmap = Bitmap.createBitmap(unscaledBitmap, 0, 0, unscaledBitmap.getWidth(),
				unscaledBitmap.getHeight(), imageMatrix, true);
		Rect srcRect = calculateSrcRect(unscaledBitmap.getWidth(), unscaledBitmap.getHeight(), dstWidth, dstHeight,
				scalingLogic);
		Rect dstRect = calculateDstRect(unscaledBitmap.getWidth(), unscaledBitmap.getHeight(), dstWidth, dstHeight,
				scalingLogic);
		Bitmap scaledBitmap = Bitmap.createBitmap(dstRect.width(), dstRect.height(),
				ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		Canvas canvas = new Canvas(scaledBitmap);
		canvas.drawBitmap(unscaledBitmap, srcRect, dstRect, new Paint(Paint.FILTER_BITMAP_FLAG));
		return scaledBitmap;
	}

	/**
	 * ScalingLogic defines how scaling should be carried out if source and destination image has different aspect
	 * ratio.
	 * 
	 * CROP: Scales the image the minimum amount while making sure that at least one of the two dimensions fit inside
	 * the requested destination area. Parts of the source image will be cropped to realize this.
	 * 
	 * FIT: Scales the image the minimum amount while making sure both dimensions fit inside the requested destination
	 * area. The resulting destination dimensions might be adjusted to a smaller size than requested.
	 */
	public static enum ScalingLogic {
		CROP, FIT
	}

	/**
	 * Calculate optimal down-sampling factor given the dimensions of a source image, the dimensions of a destination
	 * area and a scaling logic.
	 * 
	 * @param srcWidth Width of source image
	 * @param srcHeight Height of source image
	 * @param dstWidth Width of destination area
	 * @param dstHeight Height of destination area
	 * @param scalingLogic Logic to use to avoid image stretching
	 * @return Optimal down scaling sample size for decoding
	 */
	private static int calculateSampleSize(int srcWidth, int srcHeight, int dstWidth, int dstHeight,
			ScalingLogic scalingLogic) {
		if (scalingLogic == ScalingLogic.FIT) {
			final float srcAspect = (float) srcWidth / (float) srcHeight;
			final float dstAspect = (float) dstWidth / (float) dstHeight;

			if (srcAspect > dstAspect) {
				return srcWidth / dstWidth;
			} else {
				return srcHeight / dstHeight;
			}
		} else {
			final float srcAspect = (float) srcWidth / (float) srcHeight;
			final float dstAspect = (float) dstWidth / (float) dstHeight;

			if (srcAspect > dstAspect) {
				return srcHeight / dstHeight;
			} else {
				return srcWidth / dstWidth;
			}
		}
	}

	/**
	 * Calculates source rectangle for scaling bitmap
	 * 
	 * @param srcWidth Width of source image
	 * @param srcHeight Height of source image
	 * @param dstWidth Width of destination area
	 * @param dstHeight Height of destination area
	 * @param scalingLogic Logic to use to avoid image stretching
	 * @return Optimal source rectangle
	 */
	private static Rect calculateSrcRect(int srcWidth, int srcHeight, int dstWidth, int dstHeight,
			ScalingLogic scalingLogic) {
		if (scalingLogic == ScalingLogic.CROP) {
			final float srcAspect = (float) srcWidth / (float) srcHeight;
			final float dstAspect = (float) dstWidth / (float) dstHeight;

			if (srcAspect > dstAspect) {
				final int srcRectWidth = (int) (srcHeight * dstAspect);
				final int srcRectLeft = (srcWidth - srcRectWidth) / 2;
				return new Rect(srcRectLeft, 0, srcRectLeft + srcRectWidth, srcHeight);
			} else {
				final int srcRectHeight = (int) (srcWidth / dstAspect);
				final int scrRectTop = (int) (srcHeight - srcRectHeight) / 2;
				return new Rect(0, scrRectTop, srcWidth, scrRectTop + srcRectHeight);
			}
		} else {
			return new Rect(0, 0, srcWidth, srcHeight);
		}
	}

	/**
	 * Calculates destination rectangle for scaling bitmap
	 * 
	 * @param srcWidth Width of source image
	 * @param srcHeight Height of source image
	 * @param dstWidth Width of destination area
	 * @param dstHeight Height of destination area
	 * @param scalingLogic Logic to use to avoid image stretching
	 * @return Optimal destination rectangle
	 */
	private static Rect calculateDstRect(int srcWidth, int srcHeight, int dstWidth, int dstHeight,
			ScalingLogic scalingLogic) {
		if (scalingLogic == ScalingLogic.FIT) {
			final float srcAspect = (float) srcWidth / (float) srcHeight;
			final float dstAspect = (float) dstWidth / (float) dstHeight;

			if (srcAspect > dstAspect) {
				return new Rect(0, 0, dstWidth, (int) (dstWidth / srcAspect));
			} else {
				return new Rect(0, 0, (int) (dstHeight * srcAspect), dstHeight);
			}
		} else {
			return new Rect(0, 0, dstWidth, dstHeight);
		}
	}

	public static Bitmap getMutableBitmap(Bitmap bitmap) {
		if (bitmap.isMutable()) {
			return bitmap;
		}
		Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
				ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		Canvas canvas = new Canvas(newBitmap);
		canvas.drawBitmap(bitmap, 0, 0, null);
		canvas = null;
		return newBitmap;
	}

	public static Bitmap rotate(Bitmap bitmap, int orientationDegrees, float xPosition, float yPosition) {
		Bitmap rotated = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
				ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		Canvas canvas = new Canvas(rotated);
		canvas.rotate(orientationDegrees, xPosition, yPosition);
		canvas.drawBitmap(bitmap, 0, 0, null);
		canvas = null;
		return rotated;
	}

	public static boolean saveByteImage(byte[] imageData, File saveTo, int quality, boolean flipHorizontally) {
		FileOutputStream fileOutputStream = null;
		BufferedOutputStream byteOutputStream = null;
		try {
			fileOutputStream = new FileOutputStream(saveTo);
			boolean imageSaved = false;
			if (flipHorizontally) { // need to flip front camera images horizontally; only done for front as we'd be out
									// of memory for back camera
				try {
					Bitmap newImage = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
					Matrix flipMatrix = new Matrix();
					flipMatrix.postScale(-1.0f, 1.0f);
					newImage = Bitmap.createBitmap(newImage, 0, 0, newImage.getWidth(), newImage.getHeight(),
							flipMatrix, true);
					byteOutputStream = new BufferedOutputStream(fileOutputStream);
					newImage.compress(CompressFormat.JPEG, quality, byteOutputStream);
					byteOutputStream.flush();
					byteOutputStream.close();
					imageSaved = true;
				} catch (OutOfMemoryError e) {
				} // probably doesn't actually catch...
			}
			if (!flipHorizontally || !imageSaved) {
				fileOutputStream.write(imageData);
			}
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		} finally {
			IOUtilities.closeStream(byteOutputStream);
		}
		return true;
	}

	/**
	 * Save YUV image data (NV21 or YUV420sp) as JPEG to a FileOutputStream.
	 */
	public static boolean saveYUYToJPEG(byte[] imageData, File saveTo, int format, int quality, int width, int height,
			int rotation, boolean flipHorizontally) {
		FileOutputStream fileOutputStream = null;
		YuvImage yuvImg = null;
		try {
			fileOutputStream = new FileOutputStream(saveTo);

			yuvImg = new YuvImage(imageData, format, width, height, null);

			ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream(imageData.length);
			yuvImg.compressToJpeg(new Rect(0, 0, width - 1, height - 1), 90, jpegOutput);
			Bitmap yuvBitmap = BitmapFactory.decodeByteArray(jpegOutput.toByteArray(), 0, jpegOutput.size());

			Matrix imageMatrix = new Matrix();
			if (rotation != 0) {
				imageMatrix.postRotate(rotation);
			}
			if (flipHorizontally) {
				// imageMatrix.postScale(-1.0f, 1.0f);
			}

			yuvBitmap = Bitmap.createBitmap(yuvBitmap, 0, 0, yuvBitmap.getWidth(), yuvBitmap.getHeight(), imageMatrix,
					true);
			yuvBitmap.compress(CompressFormat.JPEG, quality, fileOutputStream);

		} catch (FileNotFoundException e) {
			return false;
		}
		return true;
	}

	/**
	 * Save YUV image data (NV21 or YUV420sp) as JPEG to a FileOutputStream.
	 */
	public static boolean saveJPEGToJPEG(byte[] imageData, File saveTo, boolean flipHorizontally) {
		FileOutputStream fileOutputStream = null;
		try {
			fileOutputStream = new FileOutputStream(saveTo);
			fileOutputStream.write(imageData);
			IOUtilities.closeStream(fileOutputStream);

		} catch (FileNotFoundException e) {
		} catch (IOException e) {
			return false;
		}

		// try {
		if (flipHorizontally) {
			// ExifInterface exif = new ExifInterface(saveTo.getAbsolutePath());
			// int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
			// ExifInterface.ORIENTATION_UNDEFINED);
			// exifOrientation |= ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
			// exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(exifOrientation));
			// exif.saveAttributes();
		}
		// } catch (IOException e) {
		// // don't return false - we saved fine, but just couldn't set the exif attributes
		// }
		return true;
	}

	public static boolean saveBitmap(Bitmap bitmap, CompressFormat format, int quality, File saveTo) {
		FileOutputStream fileOutputStream = null;
		BufferedOutputStream bufferedOutputStream = null;
		try {
			fileOutputStream = new FileOutputStream(saveTo);
			bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
			bitmap.compress(format, quality, bufferedOutputStream);
			bufferedOutputStream.flush();
			bufferedOutputStream.close();
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		} finally {
			IOUtilities.closeStream(bufferedOutputStream);
		}
		return true;
	}

	/**
	 * Note: the paint's textSize will be changed...
	 * 
	 * @param textString
	 * @param textCanvas
	 * @param textPaint
	 * @param alignBottom
	 * @param maxCanvasAreaWidth
	 * @param maxCanvasAreaHeight
	 * @param maxTextSize
	 * @param maxCharactersPerLine an approximate value - text will be split at the previous space character
	 */
	public static void drawScaledText(String textString, Canvas textCanvas, Paint textPaint, int textColour,
			int backgroundColour, int backgroundPadding, float backgroundRadius, boolean alignBottom,
			float maxCanvasAreaWidth, float maxCanvasAreaHeight, int maxTextSize, int maxCharactersPerLine) {

		// TODO: remove the need for maxCharactersPerLine
		StringBuilder formattedString = new StringBuilder(textString.length());
		int lineLength = 0;
		int maxLength = 0;
		String[] stringLines = textString.split("\n");
		for (String line : stringLines) {
			String[] lineFragments = line.split(" ");
			for (String fragment : lineFragments) {
				if (lineLength + fragment.length() > maxCharactersPerLine) {
					formattedString.append("\n");
					lineLength = 0;
				}

				formattedString.append(fragment);
				lineLength += fragment.length();
				if (lineLength > maxLength) {
					maxLength = lineLength;
				}
				formattedString.append(" ");
				lineLength += 1;
			}
			formattedString.append("\n");
			lineLength = 0;
		}
		textString = formattedString.toString().replace(" \n", "\n");
		String[] textsToDraw = textString.split("\n");

		textPaint = adjustTextSize(textPaint, maxLength, textsToDraw.length, maxCanvasAreaWidth, maxCanvasAreaHeight,
				maxTextSize);

		FontMetricsInt metrics = textPaint.getFontMetricsInt();
		int lineHeight = metrics.descent - metrics.ascent; // TODO: this is only an estimate

		float drawingX = 0;
		float drawingY = textCanvas.getHeight() - (lineHeight * textsToDraw.length) - backgroundPadding;
		if (!alignBottom) {
			drawingY /= 2;
		}
		drawingY += Math.abs(metrics.ascent);
		float initialY = drawingY;

		// draw the background box
		if (backgroundColour != 0) {
			RectF outerTextBounds = null;
			for (String text : textsToDraw) {
				text = text.trim();
				drawingX = (maxCanvasAreaWidth - textPaint.measureText(text)) / 2;
				Rect textBounds = new Rect();
				textPaint.getTextBounds(text, 0, text.length(), textBounds);
				if (outerTextBounds == null) {
					outerTextBounds = new RectF(drawingX + textBounds.left, drawingY + textBounds.top, drawingX
							+ textBounds.right, drawingY + textBounds.bottom);
				} else {
					outerTextBounds.left = Math.min(outerTextBounds.left, drawingX + textBounds.left);
					outerTextBounds.top = Math.min(outerTextBounds.top, drawingY + textBounds.right);
					outerTextBounds.right = Math.max(outerTextBounds.right, drawingX + textBounds.right);
					outerTextBounds.bottom = Math.max(outerTextBounds.bottom, drawingY + textBounds.bottom);
				}
				// textCanvas.drawText(text, drawingX, drawingY, textPaint);
				drawingY += lineHeight;
			}
			textPaint.setColor(backgroundColour);
			outerTextBounds.left -= backgroundPadding;
			outerTextBounds.top -= backgroundPadding;
			outerTextBounds.right += backgroundPadding;
			outerTextBounds.bottom += backgroundPadding;
			backgroundRadius *= outerTextBounds.height();
			textCanvas.drawRoundRect(outerTextBounds, backgroundRadius, backgroundRadius, textPaint);
		}

		// draw the text
		drawingY = initialY;
		textPaint.setColor(textColour);
		for (String text : textsToDraw) {
			text = text.trim();
			drawingX = (maxCanvasAreaWidth - textPaint.measureText(text)) / 2;
			textCanvas.drawText(text, drawingX, drawingY, textPaint);
			drawingY += lineHeight;
		}
	}

	public static Paint adjustTextSize(Paint paint, int maxCharactersPerLine, int numLines, float maxWidth,
			float maxHeight, int maxTextSize) {

		// make sure text is small enough for its width (most common scenario)
		String exampleText = "WMÑ1by}.acI"; // for measuring width - hacky!
		float width = paint.measureText(exampleText) * maxCharactersPerLine / exampleText.length();
		float newTextSize = Math.round((maxWidth / width) * paint.getTextSize());
		paint.setTextSize(newTextSize);

		// do the same for height
		FontMetricsInt metrics = paint.getFontMetricsInt();
		float textHeight = (metrics.descent - metrics.ascent) * numLines;
		if (textHeight > maxHeight) {
			newTextSize = Math.round(newTextSize * (maxHeight / textHeight));
			paint.setTextSize(newTextSize);
		}

		if (newTextSize > maxTextSize) {
			paint.setTextSize(maxTextSize);
		}

		return paint;
	}

	public static void addBorder(Canvas borderCanvas, Paint borderPaint, int borderWidth, int borderColor) {
		borderPaint.setColor(borderColor);
		borderPaint.setStyle(Paint.Style.STROKE);
		borderPaint.setStrokeWidth(borderWidth);
		borderWidth /= 2;
		Rect iconBorder = new Rect(borderWidth, borderWidth, borderCanvas.getWidth() - borderWidth,
				borderCanvas.getWidth() - borderWidth);
		borderCanvas.drawRect(iconBorder, borderPaint);
	}

	public static Paint getPaint(int color, int strokeWidth) {

		Paint bitmapPaint = new Paint(Paint.DEV_KERN_TEXT_FLAG);

		bitmapPaint.setDither(true);
		bitmapPaint.setAntiAlias(true);
		bitmapPaint.setFilterBitmap(true);
		bitmapPaint.setSubpixelText(true);

		bitmapPaint.setStrokeCap(Paint.Cap.ROUND);
		bitmapPaint.setStrokeJoin(Paint.Join.ROUND);
		bitmapPaint.setStrokeMiter(1);

		bitmapPaint.setColor(color);
		bitmapPaint.setStrokeWidth(strokeWidth);

		return bitmapPaint;
	}
}
