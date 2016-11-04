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
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

public class SVGView extends View {

	private static final int NO_RESOURCE = 0;

	int mResourceId;
	boolean mBitmapChanged;
	Paint mBackgroundPaint;
	Bitmap mBackgroundBitmap;

	public SVGView(Context context) {
		super(context);
		// TODO: initialise
	}

	public SVGView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public SVGView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	private void init(Context context, AttributeSet attrs) {
		mResourceId = NO_RESOURCE;
		mBitmapChanged = false;
		mBackgroundBitmap = null;
		mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SVGView);
		mResourceId = attributes.getResourceId(R.styleable.SVGView_resource, NO_RESOURCE);
		attributes.recycle();
	}

	public void setResource(int resourceId) {
		if (resourceId != NO_RESOURCE && resourceId != mResourceId) {
			mResourceId = resourceId;
			mBitmapChanged = true;
			postInvalidate();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (mBackgroundBitmap != null) {
			canvas.drawBitmap(mBackgroundBitmap, getPaddingLeft(), getPaddingTop(), mBackgroundPaint);
		}
	}

	@Override
	public void onLayout(boolean changed, int l, int t, int r, int b) {
		if ((changed || mBitmapChanged) && !isInEditMode()) { // isInEditMode so the Eclipse visual editor can load this
			mBitmapChanged = false;
			try {
				SVG svg = SVGParser.getSVGFromResource(getResources(), mResourceId);
				mBackgroundBitmap = svg.getBitmap(r - l - getPaddingLeft() - getPaddingRight(), b - t
						- getPaddingBottom() - getPaddingTop());
				svg = null;
			} catch (Throwable th) {
				mBackgroundBitmap = null; // out of memory...
			}
		}

		super.onLayout(changed, l, t, r, b);
	}
}
