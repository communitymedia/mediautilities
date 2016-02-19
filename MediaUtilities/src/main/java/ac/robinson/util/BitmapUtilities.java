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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.text.TextUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Class containing static utility methods for bitmap decoding and scaling
 * 
 * @author Andreas Agvard (andreas.agvard@sonyericsson.com)
 */
public class BitmapUtilities {

	// if a bitmap's width (height) is more than this many times its height (width) then the ScalingLogic.CROP option
	// will be ignored and the bitmap will scaled to fit into the destination box to save memory
	public static final int MAX_SAMPLE_WIDTH_HEIGHT_RATIO = 12;

	public static final float DOWNSCALE_RATIO = 6; // if using DOWNSCALE, will multiply the sample ratio by this value

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

		Matrix imageMatrix = null;
		if (rotateImage) {
			imageMatrix = new Matrix();
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
		} catch (IOException e) {
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

	public static boolean rotateImage(String imagePath, boolean antiClockwise) {
		if (imagePath == null) {
			return false;
		}
		// can only rotate jpeg via EXIF - must manually rotate other types of image
		if (imagePath.endsWith(".jpg") || imagePath.endsWith(".jpeg")) {
			try {
				ExifInterface exif = new ExifInterface(imagePath);
				int exifRotation = exif
						.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
				int newRotation = ExifInterface.ORIENTATION_UNDEFINED;
				switch (exifRotation) {
					case ExifInterface.ORIENTATION_UNDEFINED: // usually means that the current rotation is normal
					case ExifInterface.ORIENTATION_NORMAL:
						newRotation = (antiClockwise ? ExifInterface.ORIENTATION_ROTATE_270
								: ExifInterface.ORIENTATION_ROTATE_90);
						break;
					case ExifInterface.ORIENTATION_ROTATE_90:
						newRotation = (antiClockwise ? ExifInterface.ORIENTATION_NORMAL
								: ExifInterface.ORIENTATION_ROTATE_180);
						break;
					case ExifInterface.ORIENTATION_ROTATE_180:
						newRotation = (antiClockwise ? ExifInterface.ORIENTATION_ROTATE_90
								: ExifInterface.ORIENTATION_ROTATE_270);
						break;
					case ExifInterface.ORIENTATION_ROTATE_270:
						newRotation = (antiClockwise ? ExifInterface.ORIENTATION_ROTATE_180
								: ExifInterface.ORIENTATION_NORMAL);
						break;
				}
				exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(newRotation));
				exif.saveAttributes();
				return true;
			} catch (IOException e) {
				return false; // couldn't read the file, or failed to set the exif attributes
			}

		} else {
			// TODO: is there any way to catch potential out of memory problems here?
			try {
				Matrix rotationMatrix = new Matrix();
				rotationMatrix.preRotate(antiClockwise ? -90 : 90);
				Bitmap originalImage = BitmapFactory.decodeFile(imagePath);

				if (originalImage != null) {
					Bitmap rotatedImage = Bitmap.createBitmap(originalImage, 0, 0, originalImage.getWidth(),
							originalImage.getHeight(), rotationMatrix, true);
					originalImage.recycle();

					boolean success = saveBitmap(rotatedImage, Bitmap.CompressFormat.PNG, 100, new File(imagePath));
					rotatedImage.recycle();
					return success;
				}
			} catch (Throwable t) {
			}
			return false;
		}
	}

	public static Options getImageDimensions(String imagePath) {
		Options options = new Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(imagePath, options);
		return options;
	}

	public static Options getImageDimensions(Resources resources, int imageId) {
		Options options = new Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeResource(resources, imageId, options);
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
		// options.inPurgeable = true; // ignored for resources: http://stackoverflow.com/a/7068403
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
		options.inPurgeable = true; // TODO: is this appropriate for image cache? is it actually used at all?
		options.inInputShareable = true; // as above
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
			options.inPurgeable = true; // TODO: is this appropriate for image cache? is it actually used at all?
			options.inInputShareable = true; // as above
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
		if (unscaledBitmap == null) {
			return null;
		}
		if (imageMatrix == null) {
			unscaledBitmap = Bitmap.createBitmap(unscaledBitmap, 0, 0, unscaledBitmap.getWidth(),
					unscaledBitmap.getHeight());
		} else {
			unscaledBitmap = Bitmap.createBitmap(unscaledBitmap, 0, 0, unscaledBitmap.getWidth(),
					unscaledBitmap.getHeight(), imageMatrix, true);
		}
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
	 * 
	 * DOWNSCALE: Like FIT, but will downscale the image by multiplying its normal sample rate by DOWNSCALE_RATIO.
	 */
	public static enum ScalingLogic {
		CROP, FIT, DOWNSCALE
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
		final float srcAspect = (float) srcWidth / (float) srcHeight;
		if (scalingLogic == ScalingLogic.FIT) {
			final float dstAspect = (float) dstWidth / (float) dstHeight;
			if (srcAspect > dstAspect) {
				return Math.round((float) srcWidth / (float) dstWidth);
			} else {
				return Math.round((float) srcHeight / (float) dstHeight);
			}
		} else if (scalingLogic == ScalingLogic.DOWNSCALE) {
			final float dstAspect = (float) dstWidth / (float) dstHeight;
			if (srcAspect > dstAspect) {
				return Math.round(((float) srcWidth / (float) dstWidth) * DOWNSCALE_RATIO);
			} else {
				return Math.round(((float) srcHeight / (float) dstHeight) * DOWNSCALE_RATIO);
			}
		} else {
			boolean tooTall = (float) srcHeight / (float) srcWidth > MAX_SAMPLE_WIDTH_HEIGHT_RATIO;
			if ((srcWidth > srcHeight && srcAspect < MAX_SAMPLE_WIDTH_HEIGHT_RATIO) || tooTall) {
				return Math.round((float) srcHeight / (float) dstHeight);
			} else {
				return Math.round((float) srcWidth / (float) dstWidth);
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
		if (scalingLogic == ScalingLogic.FIT || scalingLogic == ScalingLogic.DOWNSCALE) {
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
			if (flipHorizontally) { // need to flip front camera images horizontally as the camera is reversed
				try {
					Bitmap newImage = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
					if (newImage != null) {
						Matrix flipMatrix = new Matrix();
						flipMatrix.postScale(-1.0f, 1.0f);
						newImage = Bitmap.createBitmap(newImage, 0, 0, newImage.getWidth(), newImage.getHeight(),
								flipMatrix, true);
						byteOutputStream = new BufferedOutputStream(fileOutputStream);
						newImage.compress(CompressFormat.JPEG, quality, byteOutputStream);
						byteOutputStream.flush();
						byteOutputStream.close();
						imageSaved = true;
					} else {
						return false;
					}
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
		YuvImage yuvImg = null;
		try {
			yuvImg = new YuvImage(imageData, format, width, height, null);

			ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream(imageData.length);
			yuvImg.compressToJpeg(new Rect(0, 0, width - 1, height - 1), 90, jpegOutput); // TODO: extract 90 constant
			Bitmap yuvBitmap = BitmapFactory.decodeByteArray(jpegOutput.toByteArray(), 0, jpegOutput.size());

			if (yuvBitmap != null) {
				Matrix imageMatrix = new Matrix();
				if (rotation != 0) {
					imageMatrix.postRotate(rotation);
				}
				if (flipHorizontally) {
					// imageMatrix.postScale(-1.0f, 1.0f);
				}

				yuvBitmap = Bitmap.createBitmap(yuvBitmap, 0, 0, yuvBitmap.getWidth(), yuvBitmap.getHeight(),
						imageMatrix, true);
				BitmapUtilities.saveBitmap(yuvBitmap, Bitmap.CompressFormat.JPEG, quality, saveTo);
				return true;
			}
			return false;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Save a JPEG to a FileOutputStream.
	 */
	public static boolean saveJPEGToJPEG(byte[] imageData, File saveTo, boolean flipHorizontally) {
		FileOutputStream fileOutputStream = null;
		try {
			try {
				fileOutputStream = new FileOutputStream(saveTo);
			} catch (FileNotFoundException e) {
				if (saveTo.exists()) { // if we've previously failed, may have corrupted file - delete
					saveTo.delete();
				}
				try {
					fileOutputStream = new FileOutputStream(saveTo);
				} catch (FileNotFoundException e2) {
					return false;
				}
			}
			fileOutputStream.write(imageData);
		} catch (IOException e) {
			return false;
		} finally {
			IOUtilities.closeStream(fileOutputStream);
		}

		// try {
		// if (flipHorizontally) {
		// ExifInterface exif = new ExifInterface(saveTo.getAbsolutePath());
		// int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
		// ExifInterface.ORIENTATION_UNDEFINED);
		// exifOrientation |= ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
		// exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(exifOrientation));
		// exif.saveAttributes();
		// }
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
			return true;
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		} finally {
			IOUtilities.closeStream(bufferedOutputStream);
		}
	}

	public static Bitmap drawableToBitmap(Drawable drawable) {
		if (drawable instanceof BitmapDrawable) {
			return ((BitmapDrawable) drawable).getBitmap();
		}

		int width = drawable.getIntrinsicWidth();
		width = width > 0 ? width : 1;
		int height = drawable.getIntrinsicHeight();
		height = height > 0 ? height : 1;

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		drawable.draw(canvas);

		return bitmap;
	}

	/**
	 * Note: the paint's textSize and alignment will be changed...
	 * 
	 * @param textString
	 * @param textCanvas
	 * @param textPaint
	 * @param textColour
	 * @param backgroundColour
	 * @param backgroundPadding
	 * @param backgroundRadius
	 * @param alignBottom
	 * @param leftOffset
	 * @param backgroundSpanWidth
	 * @param maxHeight
	 * @param maxTextSize
	 * @param maxCharactersPerLine
	 * @return The height of the drawn text, including padding
	 */
	public static int drawScaledText(String textString, Canvas textCanvas, Paint textPaint, int textColour,
			int backgroundColour, int backgroundPadding, int backgroundRadius, boolean alignBottom, float leftOffset,
			boolean backgroundSpanWidth, float maxHeight, int maxTextSize, int maxCharactersPerLine) {

		if (TextUtils.isEmpty(textString) || "".equals(textString.trim())) {
			return 0;
		}

		float maxWidth = (int) Math.floor(textCanvas.getWidth() - leftOffset);

		// split the text into lines
		// TODO: respect user-created formatting more closely? (e.g., linebreaks and spaces)
		int textLength = textString.length();
		StringBuilder formattedString = new StringBuilder(textLength);
		String[] stringLines = textString.split("\n");
		int numLines = Integer.MAX_VALUE;
		int maxLines = (int) Math.ceil(Math.sqrt(textLength / (float) maxCharactersPerLine));
		if (!alignBottom) {
			maxLines *= Math.ceil(textCanvas.getHeight() / maxHeight);
		}
		maxLines = Math.max(maxLines, stringLines.length) + 1;
		int maxLineLength = 0;
		while (numLines > maxLines) {
			formattedString.setLength(0); // clears
			numLines = 0;
			maxLineLength = 0;
			int lineLength = 0;
			for (String line : stringLines) {
				String[] lineFragments = line.split(" ");
				for (String fragment : lineFragments) {
					if (lineLength + fragment.length() > maxCharactersPerLine) {
						formattedString.append("\n");
						numLines += 1;
						lineLength = 0;
					}

					formattedString.append(fragment);
					lineLength += fragment.length();
					if (lineLength > maxLineLength) {
						maxLineLength = lineLength;
					}
					formattedString.append(" ");
					lineLength += 1;
				}
				formattedString.append("\n");
				numLines += 1;
				lineLength = 0;
			}
			if (numLines > maxLines) {
				// so we *always* increase the character count (and don't get stuck)
				maxCharactersPerLine = (int) (maxCharactersPerLine * 1.05) + 1;
			}
		}
		textString = formattedString.toString().replace(" \n", "\n"); // remove extra spaces
		String[] textsToDraw = textString.split("\n");

		// scale the text size appropriately (padding intentionally ignored here)
		textPaint = adjustTextSize(textPaint, maxLineLength, textsToDraw.length, maxWidth, maxHeight, maxTextSize);
		backgroundPadding = (int) Math.ceil(backgroundPadding / (maxTextSize / textPaint.getTextSize())) + 1;

		// set up location for drawing
		int lineHeight = (int) Math.ceil(Math.abs(textPaint.ascent()) + textPaint.descent());
		float drawingX = 0;
		float drawingY;
		if (alignBottom) {
			drawingY = textCanvas.getHeight() - getActualDescentSize(textsToDraw[textsToDraw.length - 1], textPaint)
					- backgroundPadding;
		} else {
			drawingY = ((textCanvas.getHeight() + (lineHeight * (textsToDraw.length - 1))) / 2) + textPaint.descent();
		}
		float initialX = (maxWidth / 2) + leftOffset;
		float initialY = drawingY;
		float finalHeight = 0;

		// draw the background box
		if (backgroundColour != 0) {
			String firstText = textsToDraw[0].trim();
			Rect textBounds = new Rect();
			textPaint.getTextBounds(firstText, 0, firstText.length(), textBounds);
			float boxTop = drawingY - (lineHeight * (textsToDraw.length - 1)) - textBounds.height()
					+ getActualDescentSize(firstText, textPaint) - backgroundPadding;
			float boxLeft = backgroundSpanWidth ? 0 : initialX;
			float boxRight = backgroundSpanWidth ? textCanvas.getWidth() : initialX;
			int totalPadding = 2 * backgroundPadding;
			RectF outerTextBounds = new RectF(boxLeft, boxTop, boxRight, textCanvas.getHeight());
			for (String text : textsToDraw) {
				float newWidth = textPaint.measureText(text.trim()) + totalPadding;
				float currentWidth = outerTextBounds.width();
				if (newWidth > currentWidth) {
					outerTextBounds.inset(-(newWidth - currentWidth) / 2, 0);
				}
			}
			finalHeight = outerTextBounds.height();
			textPaint.setColor(backgroundColour);
			textCanvas.drawRoundRect(outerTextBounds, backgroundRadius, backgroundRadius, textPaint);
		} else {
			finalHeight = lineHeight * (textsToDraw.length);
		}

		// draw the text
		drawingX = initialX;
		drawingY = initialY;
		textPaint.setTextAlign(Align.CENTER);
		textPaint.setColor(textColour);
		for (int i = textsToDraw.length - 1; i >= 0; i--) {
			textCanvas.drawText(textsToDraw[i].trim(), drawingX, drawingY, textPaint);
			drawingY -= lineHeight;
		}

		return (int) Math.round(finalHeight);
	}

	public static float getActualDescentSize(String text, Paint textPaint) {
		// find the text baseline (there seems to be no proper way to calculate *actual* (not general) descent height)
		Rect descentBounds = new Rect();
		textPaint.getTextBounds(text, 0, text.length(), descentBounds);
		int textHeightNormal = descentBounds.height();
		textPaint.getTextBounds("," + text, 0, text.length() + 1, descentBounds);
		return (textHeightNormal < descentBounds.height()) ? 0 : textPaint.descent();
	}

	public static Paint adjustTextSize(Paint paint, int maxCharactersPerLine, int numLines, float maxWidth,
			float maxHeight, int maxTextSize) {

		// make sure text is small enough for its width (most common scenario)
		String exampleText = "WMÑ1by}.acI"; // for measuring width - hacky!
		float width = paint.measureText(exampleText) * maxCharactersPerLine / exampleText.length();
		float newTextSize = Math.round((maxWidth / width) * paint.getTextSize());
		paint.setTextSize(newTextSize);

		// do the same for height
		float textHeight = Math.abs(paint.ascent() - paint.descent()) * numLines;
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
