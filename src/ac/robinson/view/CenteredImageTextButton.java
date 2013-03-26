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
import android.view.Gravity;
import android.widget.Button;

public class CenteredImageTextButton extends Button {

	private int mCompoundDrawablePadding = 0;

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
		// used to use https://gist.github.com/1105281 to deal with SDK tools bug; this was fixed in r17
		// see: http://code.google.com/p/android/issues/detail?id=9656
		// and: http://devmaze.wordpress.com/2011/05/22/the-case-of-android-libraries-and-custom-xml-attributes-part-2/
		// xmlns:util="http://util.robinson.ac/schema" vs. xmlns:util="http://schemas.android.com/apk/res-auto"
		TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.CenteredImageTextButton);
		int colourDefault = attributes.getColor(R.styleable.CenteredImageTextButton_filterColorDefault, 0xffffffff);
		int colourTouched = attributes.getColor(R.styleable.CenteredImageTextButton_filterColorTouched, 0xffffffff);
		attributes.recycle();

		if (!isInEditMode()) { // so the Eclipse visual editor can load this component
			UIUtilities.setButtonColorFilters(this, colourDefault, colourTouched);
		}
	}

	private Rect getTextBounds() {
		Rect bounds = new Rect();
		String buttonText = getText().toString();
		getPaint().getTextBounds(buttonText, 0, buttonText.length(), bounds);
		return bounds;
	}

	private void alignContent() {
		if (getWidth() == 0 || getHeight() == 0) {
			return; // no point aligning if the view has no size
		}

		int newPadding;
		mCompoundDrawablePadding = getCompoundDrawablePadding();

		Drawable buttonDrawable = getCompoundDrawables()[1]; // top
		if (buttonDrawable != null) {
			newPadding = (int) ((getHeight() - getTextBounds().height() - mCompoundDrawablePadding - buttonDrawable
					.getIntrinsicHeight()) / 2);
			setGravity(Gravity.CENTER | Gravity.TOP);
			setPadding(getPaddingLeft(), (newPadding > 0 ? newPadding : 0), getPaddingRight(), 0);
			return;
		}

		buttonDrawable = getCompoundDrawables()[0]; // left
		if (buttonDrawable != null) {
			newPadding = (int) ((getWidth() - getTextBounds().width() - mCompoundDrawablePadding - buttonDrawable
					.getIntrinsicWidth()) / 2);
			setGravity(Gravity.LEFT | Gravity.CENTER);
			setPadding((newPadding > 0 ? newPadding : 0), getPaddingTop(), 0, getPaddingBottom());
			return;
		}

		// default to vertical centring when no image is present
		newPadding = (int) ((getHeight() - getTextBounds().height() - mCompoundDrawablePadding) / 2);
		setGravity(Gravity.CENTER | Gravity.TOP);
		setPadding(getPaddingLeft(), (newPadding > 0 ? newPadding : 0), getPaddingRight(), 0);
	}

	@Override
	public void onSizeChanged(int w, int h, int oldW, int oldH) {
		super.onSizeChanged(w, h, oldW, oldH);
		alignContent();
	}

	@Override
	protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
		super.onTextChanged(text, start, lengthBefore, lengthAfter);
		alignContent();
	}

	@Override
	public void setCompoundDrawablePadding(int pad) {
		super.setCompoundDrawablePadding(pad);
		// only re-align if padding actually changed (as this is called during setCompoundDrawblesWithIntrinsicBounds)
		if (pad != mCompoundDrawablePadding) {
			alignContent();
		}
	}

	@Override
	public void setCompoundDrawables(Drawable left, Drawable top, Drawable right, Drawable bottom) {
		super.setCompoundDrawables(left, top, right, bottom);
		// alignContent(); //no need - setCompoundDrawables calls setCompoundDrawablesWithIntrinsicBounds
	}

	@Override
	public void setCompoundDrawablesWithIntrinsicBounds(Drawable left, Drawable top, Drawable right, Drawable bottom) {
		super.setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom);
		alignContent();
	}

	@Override
	public void setCompoundDrawablesWithIntrinsicBounds(int left, int top, int right, int bottom) {
		super.setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom);
		alignContent();
	}
}
