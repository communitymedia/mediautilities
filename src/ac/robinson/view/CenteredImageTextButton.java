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

import ac.robinson.util.AndroidUtilities;
import ac.robinson.util.UIUtilities;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.Button;

public class CenteredImageTextButton extends Button {

	public CenteredImageTextButton(Context context) {
		super(context);
		// TODO: initialise
	}

	public CenteredImageTextButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		setStyles(context, attrs);
	}

	public CenteredImageTextButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setStyles(context, attrs);
	}

	// see: http://kevindion.com/2011/01/custom-xml-attributes-for-android-widgets/
	private void setStyles(Context context, AttributeSet attrs) {

		// TODO: temporarily using https://gist.github.com/1105281
		// when SDK tools R17 is released, use methods from: http://code.google.com/p/android/issues/detail?id=9656
		// and: http://devmaze.wordpress.com/2011/05/22/the-case-of-android-libraries-and-custom-xml-attributes-part-2/
		// xmlns:custom="http://schemas.android.com/apk/res-auto"
		// TypedArray attributes = context.obtainStyledAttributes(attrs,
		// R.styleable.CenteredImageTextButton);
		//
		// int colourNormal = -1;
		// int colourTouched = -1;
		//
		// for (int i = 0, n = attributes.getIndexCount(); i < n; ++i) {
		// int attr = attributes.getIndex(i);
		// switch (attr) {
		// case R.styleable.CenteredImageTextButton_filterColorNormal:
		// colourNormal = attributes.getColor(attr, -1);
		// break;
		// case R.styleable.CenteredImageTextButton_filterColorTouched:
		// colourTouched = attributes.getColor(attr, -1);
		// break;
		// }
		// }
		// attributes.recycle();
		//
		// TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.CenteredImageTextButton);
		// attrs.getAttributeValue(AndroidUtilities.XMLNS, "filterColorDefault");

		// for no effect as default (when no value is set in XML)
		int defaultColor = 0xffffffff;
		int touchedColor = 0xffffffff;

		int noColour = getResources().getColor(android.R.color.transparent);
		int resColor = attrs.getAttributeResourceValue(AndroidUtilities.XMLNS, "filterColorDefault", noColour);
		if (resColor != noColour) {
			defaultColor = getResources().getColor(resColor);
		}

		resColor = attrs.getAttributeResourceValue(AndroidUtilities.XMLNS, "filterColorTouched", noColour);
		if (resColor != noColour) {
			touchedColor = getResources().getColor(resColor);
		}

		if (!isInEditMode()) { // so the Eclipse visual editor can load this component
			UIUtilities.setButtonColorFilters(this, defaultColor, touchedColor);
		}
	}

	private void redrawButton() {

		Rect bounds = new Rect();
		String buttonText = getText().toString().trim();
		getPaint().getTextBounds(buttonText, 0, buttonText.length(), bounds);

		Drawable buttonDrawable = getCompoundDrawables()[1]; // top
		int newPadding;
		if (buttonDrawable != null) {
			newPadding = (int) ((getHeight() - bounds.height() - getCompoundDrawablePadding() - buttonDrawable
					.getIntrinsicHeight()) / 2);
			setGravity(Gravity.CENTER | Gravity.TOP);
			setPadding(getPaddingLeft(), (newPadding > 0 ? newPadding : 0), getPaddingRight(), 0);
			return;
		}

		buttonDrawable = getCompoundDrawables()[0]; // left
		if (buttonDrawable != null) {
			newPadding = (int) ((getWidth() - bounds.width() - getCompoundDrawablePadding() - buttonDrawable
					.getIntrinsicWidth()) / 2);
			setGravity(Gravity.LEFT | Gravity.CENTER);
			setPadding((newPadding > 0 ? newPadding : 0), getPaddingTop(), 0, getPaddingBottom());
		}
	}

	@Override
	public void onSizeChanged(int w, int h, int oldW, int oldH) {
		super.onSizeChanged(w, h, oldW, oldH);
		redrawButton();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		redrawButton();
	}
}
