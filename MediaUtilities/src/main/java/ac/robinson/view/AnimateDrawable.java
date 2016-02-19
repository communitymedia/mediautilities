/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ac.robinson.view;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;

public class AnimateDrawable extends Drawable {

	private Drawable mDrawable;
	private boolean mMutated;

	private Animation mAnimation;
	private Transformation mTransformation = new Transformation();

	public AnimateDrawable(Drawable target) {
		mDrawable = target;
	}

	public AnimateDrawable(Drawable target, Animation animation) {
		mDrawable = target;
		mAnimation = animation;
	}

	public Animation getAnimation() {
		return mAnimation;
	}

	public void setAnimation(Animation anim) {
		mAnimation = anim;
	}

	public boolean hasStarted() {
		return mAnimation != null && mAnimation.hasStarted();
	}

	public boolean hasEnded() {
		return mAnimation == null || mAnimation.hasEnded();
	}

	@Override
	public void draw(Canvas canvas) {
		if (mDrawable != null) {
			int sc = canvas.save();
			Animation anim = mAnimation;
			if (anim != null) {
				anim.getTransformation(AnimationUtils.currentAnimationTimeMillis(), mTransformation);
				canvas.concat(mTransformation.getMatrix());
			}
			mDrawable.draw(canvas);
			canvas.restoreToCount(sc);
			if (!mAnimation.hasEnded()) {
				invalidateSelf();
			}
		}
	}

	@Override
	public int getIntrinsicWidth() {
		return mDrawable != null ? mDrawable.getIntrinsicWidth() : -1;
	}

	@Override
	public int getIntrinsicHeight() {
		return mDrawable != null ? mDrawable.getIntrinsicHeight() : -1;
	}

	@Override
	public int getOpacity() {
		return mDrawable != null ? mDrawable.getOpacity() : PixelFormat.TRANSPARENT;
	}

	@Override
	public void setFilterBitmap(boolean filter) {
		if (mDrawable != null) {
			mDrawable.setFilterBitmap(filter);
		}
	}

	@Override
	public void setDither(boolean dither) {
		if (mDrawable != null) {
			mDrawable.setDither(dither);
		}
	}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		if (mDrawable != null) {
			mDrawable.setColorFilter(colorFilter);
		}
	}

	@Override
	public void setAlpha(int alpha) {
		if (mDrawable != null) {
			mDrawable.setAlpha(alpha);
		}
	}

	@Override
	public Drawable mutate() {
		if (mDrawable != null && !mMutated && super.mutate() == this) {
			mDrawable.mutate();
			mMutated = true;
		}
		return this;
	}
}
