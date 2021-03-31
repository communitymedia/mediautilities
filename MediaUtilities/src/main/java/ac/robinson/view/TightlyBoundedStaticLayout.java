/*
 *  Copyright (C) 2021 Simon Robinson
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

//
package ac.robinson.view;

import android.graphics.Rect;
import android.os.Build;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

/**
 * Inspired by https://stackoverflow.com/a/59525877/
 */
public class TightlyBoundedStaticLayout extends StaticLayout {
	private final Rect mLineBounds = new Rect();
	private final Rect mTextBounds = new Rect();

	public TightlyBoundedStaticLayout(CharSequence source, TextPaint paint, int width, Alignment align, float spacingmult,
									  float spacingadd, boolean includepad) {
		super(source, paint, width, align, spacingmult, spacingadd, includepad);
	}

	public TightlyBoundedStaticLayout(CharSequence source, int bufstart, int bufend, TextPaint paint, int outerwidth,
									  Alignment align, float spacingmult, float spacingadd, boolean includepad) {
		super(source, bufstart, bufend, paint, outerwidth, align, spacingmult, spacingadd, includepad);
	}

	public TightlyBoundedStaticLayout(CharSequence source, int bufstart, int bufend, TextPaint paint, int outerwidth,
									  Alignment align, float spacingmult, float spacingadd, boolean includepad,
									  TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
		super(source, bufstart, bufend, paint, outerwidth, align, spacingmult, spacingadd, includepad, ellipsize,
				ellipsizedWidth);
	}

	public Rect getTightBounds() {
		getTightLineBounds(0, mTextBounds); // for a single line we can just get bounds directly into the text Rect

		// expand the rect to include any additional lines
		if (getLineCount() > 1) {
			for (int line = 0; line < getLineCount(); line++) {
				getTightLineBounds(line, mLineBounds);
				mTextBounds.union(mLineBounds);
			}
		}

		return mTextBounds;
	}

	private void getTightLineBounds(int line, Rect bounds) {
		int firstCharOnLine = getLineStart(line);
		int lastCharOnLine = getLineVisibleEnd(line);
		CharSequence s = getText().subSequence(firstCharOnLine, lastCharOnLine);

		// measure the text - top and bottom are measured from the baseline; left and right are measured from 0
		// note: strings that consist only of space characters may return a zero-width box
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			getPaint().getTextBounds(s, 0, s.length(), bounds);
		} else {
			getPaint().getTextBounds(s.toString(), 0, s.length(), bounds);
		}
		int baseline = getLineBaseline(line);
		bounds.top = baseline + bounds.top;
		bounds.bottom = baseline + bounds.bottom;
	}
}
