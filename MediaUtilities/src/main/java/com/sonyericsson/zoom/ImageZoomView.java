package com.sonyericsson.zoom;

/*
 * Copyright (c) 2010, Sony Ericsson Mobile Communication AB. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright notice, this 
 *      list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright notice,
 *      this list of conditions and the following disclaimer in the documentation
 *      and/or other materials provided with the distribution.
 *    * Neither the name of the Sony Ericsson Mobile Communication AB nor the names
 *      of its contributors may be used to endorse or promote products derived from
 *      this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.util.Observable;
import java.util.Observer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

/**
 * View capable of drawing an image at different zoom state levels
 */
public class ImageZoomView extends ImageView implements Observer {

	/** Rectangle used (and re-used) for cropping source image. */
	private final Rect mRectSrc = new Rect();

	/** Rectangle used (and re-used) for specifying drawing area on canvas. */
	private final Rect mRectDst = new Rect();

	/** Rectangle used (and re-used) for specifying drawable bounds. */
	private final Rect mRectBounds = new Rect();

	/** Object holding aspect quotient */
	private final AspectQuotient mAspectQuotient = new AspectQuotient();

	/** The drawable that we're zooming in, and drawing on the screen. */
	private Drawable mDrawable;

	/** State of the zoom. */
	private ZoomState mState;

	// Public methods

	/**
	 * Constructor
	 */
	public ImageZoomView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void onDetachedFromWindow() {
		setOnTouchListener(null);
		clearNormalDrawable();
		clearFullSizeDrawable();
		super.onDetachedFromWindow();
	}

	@Override
	public void setImageDrawable(Drawable drawable) {
		setOnTouchListener(null); // remove the zoom listener
		clearNormalDrawable();
		clearFullSizeDrawable();
		super.setImageDrawable(drawable);
	}

	private void clearNormalDrawable() {
		Drawable drawable = getDrawable();
		if (drawable != null) {
			if (drawable instanceof BitmapDrawable) {
				((BitmapDrawable) drawable).getBitmap().recycle();
			}
			super.setImageDrawable(null);
		}
		drawable = null;
	}

	public void clearFullSizeDrawable() {
		if (mDrawable != null) {
			if (mDrawable instanceof BitmapDrawable) {
				((BitmapDrawable) mDrawable).getBitmap().recycle();
			}
		}
		mDrawable = null;
	}

	public boolean hasFullSizeDrawable() {
		return mDrawable != null;
	}

	/**
	 * Set image drawable
	 * 
	 * @param drawable The drawable to view and zoom into
	 */
	public void setFullSizeImageDrawable(Drawable drawable) {
		clearNormalDrawable();
		clearFullSizeDrawable();
		mDrawable = drawable;

		if (mDrawable != null) {
			mDrawable.setFilterBitmap(true);

			final int width = mDrawable.getIntrinsicWidth();
			final int height = mDrawable.getIntrinsicHeight();
			mDrawable.setBounds(new Rect(0, 0, width, height));

			mAspectQuotient.updateAspectQuotient(getWidth(), getHeight(), width, height);
			mAspectQuotient.notifyObservers();
		}

		invalidate();
	}

	public void setFullSizeImagePath(String imagePath) {
		if (imagePath != null) {
			// done like this (rather than just using setFullSizeImageDrawable directly) so we can clear before loading
			clearNormalDrawable();
			clearFullSizeDrawable();
			setFullSizeImageDrawable(Drawable.createFromPath(imagePath));
		} else {
			invalidate();
		}
	}

	/**
	 * Set object holding the zoom state that should be used
	 * 
	 * @param state The zoom state
	 */
	public void setZoomState(ZoomState state) {
		if (mState != null) {
			mState.deleteObserver(this);
		}

		mState = state;
		mState.addObserver(this);

		invalidate();
	}

	/**
	 * Gets reference to object holding aspect quotient
	 * 
	 * @return Object holding aspect quotient
	 */
	public AspectQuotient getAspectQuotient() {
		return mAspectQuotient;
	}

	// Superclass overrides

	@Override
	protected void onDraw(Canvas canvas) {
		if (mDrawable != null && mState != null) {
			Log.d("blah", "drawing zoom");
			final float aspectQuotient = mAspectQuotient.get();

			final int viewWidth = getWidth();
			final int viewHeight = getHeight();
			final int bitmapWidth = mDrawable.getIntrinsicWidth();
			final int bitmapHeight = mDrawable.getIntrinsicHeight();

			final float panX = mState.getPanX();
			final float panY = mState.getPanY();
			final float zoomX = mState.getZoomX(aspectQuotient) * viewWidth / bitmapWidth;
			final float zoomY = mState.getZoomY(aspectQuotient) * viewHeight / bitmapHeight;

			// Setup source and destination rectangles
			mRectSrc.left = (int) (panX * bitmapWidth - viewWidth / (zoomX * 2));
			mRectSrc.top = (int) (panY * bitmapHeight - viewHeight / (zoomY * 2));
			mRectSrc.right = (int) (mRectSrc.left + viewWidth / zoomX);
			mRectSrc.bottom = (int) (mRectSrc.top + viewHeight / zoomY);
			mRectDst.left = getLeft();
			mRectDst.top = getTop();
			mRectDst.right = getRight();
			mRectDst.bottom = getBottom();

			// Adjust source rectangle so that it fits within the source image.
			if (mRectSrc.left < 0) {
				mRectDst.left += -mRectSrc.left * zoomX;
				mRectSrc.left = 0;
			}
			if (mRectSrc.right > bitmapWidth) {
				mRectDst.right -= (mRectSrc.right - bitmapWidth) * zoomX;
				mRectSrc.right = bitmapWidth;
			}
			if (mRectSrc.top < 0) {
				mRectDst.top += -mRectSrc.top * zoomY;
				mRectSrc.top = 0;
			}
			if (mRectSrc.bottom > bitmapHeight) {
				mRectDst.bottom -= (mRectSrc.bottom - bitmapHeight) * zoomY;
				mRectSrc.bottom = bitmapHeight;
			}

			// rather than drawing a loaded bitmap, we use a drawable and set its bounds to show the correct area
			// - better as we can load the original image (memory size managed by Drawable) for high quality zooming
			mRectBounds.left = (int) (-mRectSrc.left * zoomX) + mRectDst.left;
			mRectBounds.top = (int) (-mRectSrc.top * zoomY) + mRectDst.top;
			mRectBounds.right = (int) ((bitmapWidth - mRectSrc.right) * zoomX) + mRectDst.right;
			mRectBounds.bottom = (int) ((bitmapHeight - mRectSrc.bottom) * zoomY) + mRectDst.bottom;
			mDrawable.setBounds(mRectBounds);
			mDrawable.draw(canvas);

			// original method - drawing a subsection of a bitmap
			// canvas.drawBitmap(((BitmapDrawable)mBitmap).getBitmap(), mRectSrc, mRectDst, mPaint);
		} else {
			Log.d("blah", "drawing normal " + (mDrawable == null) + "," + (mState == null));
			Drawable drawable = getDrawable();
			if (drawable != null) {
				if (drawable instanceof BitmapDrawable) {
					if (!((BitmapDrawable) drawable).getBitmap().isRecycled()) {
						super.onDraw(canvas);
					}
				} else {
					super.onDraw(canvas);
				}
			}
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		if (mDrawable != null) {
			mAspectQuotient.updateAspectQuotient(right - left, bottom - top, mDrawable.getIntrinsicWidth(),
					mDrawable.getIntrinsicHeight());
			mAspectQuotient.notifyObservers();
		}
	}

	// implements Observer
	public void update(Observable observable, Object data) {
		invalidate();
	}

}
