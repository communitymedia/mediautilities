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

package ac.robinson.view;

import ac.robinson.mediautilities.R;
import ac.robinson.util.UIUtilities;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Button;

public class CenteredImageTextButton extends Button {

	// for calculating the default padding
	private int mDrawableSize;
	private boolean mLayoutChanged;
	private DrawablePosition mDrawablePosition;

	private Rect mTextBounds = new Rect();

	// extra (optional) padding between the icon and the text
	private int mIconPadding;

	private enum DrawablePosition {
		NONE, LEFT, TOP, RIGHT, BOTTOM
	}

	public CenteredImageTextButton(Context context) {
		super(context);
		// can't initialise styles when not loading from XML
	}

	public CenteredImageTextButton(Context context, AttributeSet attrs) {
		// can't combine with defStyle version here; we need android.R.attr.buttonStyle but defStyle doesn't work for
		// custom views - see: https://code.google.com/p/android/issues/detail?id=12683
		super(context, attrs);
		setStyles(context, attrs);
	}

	public CenteredImageTextButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setStyles(context, attrs);
	}

	// see: http://kevindion.com/2011/01/custom-xml-attributes-for-android-widgets/
	// used to use https://gist.github.com/1105281 to deal with SDK tools bug; this was fixed in r17
	// see: http://code.google.com/p/android/issues/detail?id=9656 and http://devmaze.wordpress.com/2011/05/22/
	// xmlns:util="http://util.robinson.ac/schema" vs. xmlns:util="http://schemas.android.com/apk/res-auto"
	private void setStyles(Context context, AttributeSet attrs) {
		TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.CenteredImageTextButton);
		int colourDefault = attributes.getColor(R.styleable.CenteredImageTextButton_filterColorDefault, 0xffffffff);
		int colourTouched = attributes.getColor(R.styleable.CenteredImageTextButton_filterColorTouched, 0xffffffff);
		attributes.recycle();

		if (!isInEditMode()) { // so the Eclipse visual editor can load this component
			UIUtilities.setButtonColorFilters(this, colourDefault, colourTouched);
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (!changed && !mLayoutChanged) {
			return; // no need to re-layout
		}

		// get the text bounds
		CharSequence buttonText = getText();
		if (buttonText != null && buttonText.length() > 0) {
			getPaint().getTextBounds(buttonText.toString(), 0, buttonText.length(), mTextBounds);
		} else {
			mTextBounds.set(0, 0, 0, 0);
		}

		// get the text and window sizes, based on where the drawable is located
		int textSize = 0;
		int windowSize = 0;
		switch (mDrawablePosition) {
			case LEFT:
			case RIGHT:
				textSize = mTextBounds.width();
				windowSize = getWidth();
				break;
			case TOP:
			case BOTTOM:
				textSize = mTextBounds.height();
				windowSize = getHeight();
				break;
			default:
				break;
		}

		if (windowSize == 0) {
			return; // we don't have a size (probably mid-window resize or extracted keyboard) - wait until next update
		}

		// compound drawables aren't scaled automatically, so make sure the current drawable isn't too big for the view
		int maxSize = windowSize - (mIconPadding + textSize);
		if (mDrawableSize > maxSize) {
			Drawable[] drawables = getCompoundDrawables();
			Drawable drawable = null;
			switch (mDrawablePosition) {
				case LEFT:
					drawable = drawables[0];
					break;
				case TOP:
					drawable = drawables[1];
					break;
				case RIGHT:
					drawable = drawables[2];
					break;
				case BOTTOM:
					drawable = drawables[3];
					break;
				default:
					break;
			}

			if (drawable != null) {
				float scaleFactor = maxSize / (float) mDrawableSize;
				int newWidth = (int) Math.floor(drawable.getIntrinsicWidth() * scaleFactor);
				int newHeight = (int) Math.floor(drawable.getIntrinsicHeight() * scaleFactor);
				drawable.setBounds(0, 0, newWidth, newHeight);

				switch (mDrawablePosition) {
					case LEFT:
						mDrawableSize = newWidth;
						setCompoundDrawables(drawable, null, null, null);
						break;
					case TOP:
						mDrawableSize = newHeight;
						setCompoundDrawables(null, drawable, null, null);
						break;
					case RIGHT:
						mDrawableSize = newWidth;
						setCompoundDrawables(null, null, drawable, null);
						break;
					case BOTTOM:
						mDrawableSize = newHeight;
						setCompoundDrawables(null, null, null, drawable);
						break;
					default:
						break;
				}
			}
		}

		// finally, set the drawable and view padding centred within the available space
		int contentPadding = (int) ((windowSize - (mDrawableSize + mIconPadding + textSize)) / 2f);
		setCompoundDrawablePadding(-contentPadding + mIconPadding);

		switch (mDrawablePosition) {
			case LEFT:
				setPadding(contentPadding, getPaddingTop(), 0, getPaddingBottom());
				break;
			case TOP:
				setPadding(getPaddingLeft(), contentPadding, getPaddingRight(), 0);
				break;
			case RIGHT:
				setPadding(0, getPaddingTop(), contentPadding, getPaddingBottom());
				break;
			case BOTTOM:
				setPadding(getPaddingLeft(), 0, getPaddingRight(), contentPadding);
				break;
			default:
				setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom());
				break;
		}

		mLayoutChanged = false;
	}

	@Override
	public void setCompoundDrawablesWithIntrinsicBounds(Drawable left, Drawable top, Drawable right, Drawable bottom) {
		super.setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom);
		if (left != null) {
			mDrawablePosition = DrawablePosition.LEFT;
			mDrawableSize = left.getIntrinsicWidth();
		} else if (top != null) {
			mDrawablePosition = DrawablePosition.TOP;
			mDrawableSize = top.getIntrinsicHeight();
		} else if (right != null) {
			mDrawablePosition = DrawablePosition.RIGHT;
			mDrawableSize = right.getIntrinsicWidth();
		} else if (bottom != null) {
			mDrawablePosition = DrawablePosition.BOTTOM;
			mDrawableSize = bottom.getIntrinsicHeight();
		} else {
			mDrawablePosition = DrawablePosition.NONE;
			mDrawableSize = 0;
		}
		mLayoutChanged = true;
		requestLayout();
	}

	@Override
	protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
		super.onTextChanged(text, start, lengthBefore, lengthAfter);
		mLayoutChanged = true;
		requestLayout();
	}

	public void setIconPadding(int padding) {
		mIconPadding = padding;
		mLayoutChanged = true;
		requestLayout();
	}
}
