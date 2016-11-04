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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import ac.robinson.mediautilities.R;

public class AutoResizeTextView extends TextView {

	// max height and min/max text size are loaded from XML attrs on create; these values are defaults
	private int mMaxTextHeight = 0; // the maximum allowed height for the text in this view (not including padding)
	private float mMinTextSize = 18; // lower bound for the text size used (i.e. font size)
	private float mMaxTextSize = 48; // upper bound for the text size used (i.e. font size)

	private boolean mEllipsize = true; // whether to use ellipsizing
	private String mEllipsis = "…"; // the ellipsis string to add (loaded from R.string.textview_ellipsis on create)

	// these values are cached to deal with devices applying their own line spacing (which would break our auto-sizing)
	private float mSpacingMult = 1f; // the line spacing multiplier
	private float mSpacingAdd = 0f; // the additional line spacing

	public AutoResizeTextView(Context context) {
		this(context, null);
	}

	public AutoResizeTextView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public AutoResizeTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mEllipsis = context.getString(R.string.textview_ellipsis);
		if (attrs != null) {
			TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.AutoResizeTextView);
			mMinTextSize = attributes.getDimensionPixelSize(R.styleable.AutoResizeTextView_minTextSize,
					(int) mMinTextSize);
			mMaxTextSize = attributes.getDimensionPixelSize(R.styleable.AutoResizeTextView_maxTextSize,
					(int) mMaxTextSize);
			mMaxTextHeight = attributes.getDimensionPixelSize(R.styleable.AutoResizeTextView_maxTextHeight,
					mMaxTextHeight);
			mEllipsize = attributes.getBoolean(R.styleable.AutoResizeTextView_ellipsize, mEllipsize);
			attributes.recycle();
		}
	}

	public void setMinTextSize(float minTextSize) {
		mMinTextSize = minTextSize;
		requestLayout();
	}

	public void setMaxTextSize(float maxTextSize) {
		mMaxTextSize = maxTextSize;
		requestLayout();
	}

	public void setMaxTextHeight(int maxTextHeight) {
		mMaxTextHeight = maxTextHeight;
		requestLayout();
	}

	public void setEllipsize(boolean ellipsize) {
		mEllipsize = ellipsize;
	}

	@Override
	public void setEllipsize(TruncateAt where) {
		super.setEllipsize(null); // we don't want to use the parent's ellipsizing
	}

	@Override
	public void setLineSpacing(float add, float mult) {
		// need to update our cached mult/add values (to deal with devices that perform auto spacing)
		super.setLineSpacing(add, mult);
		mSpacingMult = mult;
		mSpacingAdd = add;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// set to the maximum size on rotation so we can force a resize of the text
		setTextSize(TypedValue.COMPLEX_UNIT_PX, mMaxTextSize);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int requestedWidth = MeasureSpec.getSize(widthMeasureSpec);
		int requestedHeight = MeasureSpec.getSize(heightMeasureSpec);
		int horizontalPadding = getCompoundPaddingLeft() + getCompoundPaddingRight();
		int verticalPadding = getCompoundPaddingTop() + getCompoundPaddingBottom();
		boolean useResizedDimensions = mMaxTextHeight > 0;
		Point fittedTextSize = resizeTextToFit(requestedWidth - horizontalPadding,
				useResizedDimensions ? Math.min(requestedHeight - verticalPadding, mMaxTextHeight - verticalPadding)
						: requestedHeight - verticalPadding);
		setMeasuredDimension(useResizedDimensions ? fittedTextSize.x + horizontalPadding : requestedWidth,
				useResizedDimensions ? fittedTextSize.y + verticalPadding : requestedHeight);
	}

	/**
	 * Resize this view's text to be no larger than the specified width and height
	 * 
	 * @param width the maximum allowed width
	 * @param height the maximum allowed height
	 * @return a Point containing the new minimum width (x) and height (y) of the text view
	 */
	private Point resizeTextToFit(int width, int height) {
		CharSequence text = getText();
		Point fittedTextSize = new Point(width, height);
		if (text == null || text.length() <= 0 || height <= 0 || width <= 0) {
			return fittedTextSize;
		}

		// try smaller sizes until we either fit within the view or have reached the minimum text size
		float currentTextSize = getTextSize();
		float newTextSize = mMaxTextSize;
		TextPaint textPaint = new TextPaint(getPaint()); // need a TextPaint copy (getPaint() says don't edit original)
		do {
			newTextSize = Math.max(newTextSize - 1, mMinTextSize);
			textPaint.setTextSize(newTextSize);
			getFittedTextSize(text, textPaint, width, fittedTextSize);
		} while (fittedTextSize.y > height && newTextSize > mMinTextSize);

		// if we've reached our minimum text size and the text still doesn't fit, append an ellipsis
		if (mEllipsize && newTextSize <= mMinTextSize && fittedTextSize.y > height) {
			StaticLayout layout = new StaticLayout(text, textPaint, width, Alignment.ALIGN_NORMAL, mSpacingMult,
					mSpacingAdd, false);
			if (layout.getLineCount() > 0) {
				// the line at the vertical position nearest to height would overflow, so trim up to the previous line
				int lastLine = layout.getLineForVertical(height) - 1;
				if (lastLine < 0) {
					setText(mEllipsis); // no text at all will fit - just show the ellipsis
				} else {
					// trim to the last visible line and add an ellipsis
					int start = layout.getLineStart(lastLine);
					int end = layout.getLineEnd(lastLine);

					float lineWidth = layout.getLineWidth(lastLine);
					float ellipseWidth = textPaint.measureText(mEllipsis);
					while (width < lineWidth + ellipseWidth) {
						lineWidth = textPaint.measureText(text.subSequence(start, end).toString());
						end -= 1;
					}

					setText(text.subSequence(0, end) + mEllipsis);
				}
			}
		}

		if (currentTextSize - newTextSize != -1) {
			// some devices try to auto adjust line spacing, so here we force the default line spacing that was cached
			// earlier - like setTextSize, this causes another layout invalidation as a side effect, which is why we
			// check for different values, so we don't end up doing these loops forever, switching between two slightly
			// different sizes and never actually pausing long enough to let the view be resized; we don't use
			// Math.abs() as we always want to resize to a smaller size (so all text shows), just not up to a larger one
			setTextSize(TypedValue.COMPLEX_UNIT_PX, newTextSize);
			setLineSpacing(mSpacingAdd, mSpacingMult);
		}

		return fittedTextSize;
	}

	// use a static layout to render text off screen before measuring its width and height
	private void getFittedTextSize(CharSequence source, TextPaint paint, int maxWidth, Point fittedTextSize) {
		StaticLayout layout = new StaticLayout(source, paint, maxWidth, Alignment.ALIGN_NORMAL, mSpacingMult,
				mSpacingAdd, true);
		float layoutWidth = layout.getWidth();
		int layoutLines = layout.getLineCount();
		if (layoutLines > 0) {
			layoutWidth = 0;
			for (int i = 0; i < layoutLines; i++) {
				layoutWidth = Math.max(layoutWidth, layout.getLineWidth(i));
			}
		}
		fittedTextSize.set((int) Math.ceil(layoutWidth), layout.getHeight());
	}
}
