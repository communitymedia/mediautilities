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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.List;

import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class UIUtilities {

	/**
	 * Enable pixel dithering for this window (but only in API < 17)
	 */
	public static void setPixelDithering(Window window) {
		if (window != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) { // deprecated from API 17+
			// better gradient drawables
			window.setFormat(PixelFormat.RGBA_8888);
			window.addFlags(WindowManager.LayoutParams.FLAG_DITHER);
		}
	}

	/**
	 * Get the current rotation of the screen, either 0, 90, 180 or 270 degrees
	 */
	public static int getScreenRotationDegrees(WindowManager windowManager) {
		int degrees = 0;
		switch (windowManager.getDefaultDisplay().getRotation()) {
			case Surface.ROTATION_0:
				degrees = 0;
				break;
			case Surface.ROTATION_90:
				degrees = 90;
				break;
			case Surface.ROTATION_180:
				degrees = 180;
				break;
			case Surface.ROTATION_270:
				degrees = 270;
				break;
			default:
				break;
		}
		return degrees;
	}

	/**
	 * Get the "natural" screen orientation - i.e. the orientation in which this device is designed to be used most
	 * often.
	 *
	 * @return either ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE or ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
	 */
	public static int getNaturalScreenOrientation(WindowManager windowManager) {
		Display display = windowManager.getDefaultDisplay();
		Point screenSize = getScreenSize(windowManager);
		int width = 0;
		int height = 0;
		switch (display.getRotation()) {
			case Surface.ROTATION_0:
			case Surface.ROTATION_180:
				width = screenSize.x;
				height = screenSize.y;
				break;
			case Surface.ROTATION_90:
			case Surface.ROTATION_270:
				//noinspection SuspiciousNameCombination
				width = screenSize.y;
				//noinspection SuspiciousNameCombination
				height = screenSize.x;
				break;
			default:
				break;
		}

		if (width > height) {
			return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
		}
		return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
	}

	/**
	 * Gets the size of the device's screen. On more recent devices (API >= 17) this is the <i>real</i> size of the screen. On
	 * earlier devices the returned value does not include non-hideable interface elements.
	 *
	 * @param windowManager A WindowManager instance (i.e., obtained via getWindowManager())
	 * @return A Point where x is the screen width and y is the screen height
	 */
	public static Point getScreenSize(WindowManager windowManager) {
		Point screenSize = new Point();
		Display display = windowManager.getDefaultDisplay();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			display.getRealSize(screenSize);
		} else {
			display.getSize(screenSize);
		}
		return screenSize;
	}

	public static void setScreenOrientationFixed(Activity activity, boolean orientationFixed) {
		if (orientationFixed) {
			WindowManager windowManager = activity.getWindowManager();
			boolean naturallyPortrait = getNaturalScreenOrientation(windowManager) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
			int reversePortrait = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
			int reverseLandscape = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
			switch (windowManager.getDefaultDisplay().getRotation()) {
				case Surface.ROTATION_0:
					activity.setRequestedOrientation(naturallyPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT :
							ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
					break;
				case Surface.ROTATION_90:
					activity.setRequestedOrientation(
							naturallyPortrait ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : reversePortrait);
					break;
				case Surface.ROTATION_180:
					activity.setRequestedOrientation(naturallyPortrait ? reversePortrait : reverseLandscape);
					break;
				case Surface.ROTATION_270:
					activity.setRequestedOrientation(
							naturallyPortrait ? reverseLandscape : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
					break;
				default:
					break;
			}
		} else {
			activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		}
	}

	public static void setFullScreen(final Window window) {
		if ((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) !=
				WindowManager.LayoutParams.FLAG_FULLSCREEN) {
			Handler handler = new Handler();
			handler.post(new Runnable() {
				@Override
				public void run() {
					try {
						WindowManager.LayoutParams attrs = window.getAttributes();
						attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
						window.setAttributes(attrs);
					} catch (Throwable ignored) {
					}
				}
			});
		}
	}

	public static void setNonFullScreen(final Window window) {
		if ((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) ==
				WindowManager.LayoutParams.FLAG_FULLSCREEN) {
			Handler handler = new Handler();
			handler.post(new Runnable() {
				@Override
				public void run() {
					try {
						WindowManager.LayoutParams attrs = window.getAttributes();
						attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
						window.setAttributes(attrs);
					} catch (Throwable ignored) {
					}
				}
			});
		}
	}

	public static void acquireKeepScreenOn(Window window) {
		window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	public static void releaseKeepScreenOn(Window window) {
		window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	public static void showToast(Context context, int id) {
		showToast(context, id, false);
	}

	public static void showToast(Context context, int id, boolean longToast) {
		Toast.makeText(context, id, longToast ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
	}

	public static void showToast(Context context, int id, int duration) {
		Handler handler = new Handler();
		final Toast toast = Toast.makeText(context, id, Toast.LENGTH_LONG);
		toast.show();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				toast.cancel();
			}
		}, duration);
	}

	public static void showFormattedToast(Context context, int id, Object... args) {
		Toast.makeText(context, String.format(context.getText(id).toString(), args), Toast.LENGTH_LONG).show();
	}

	// whether an intent can be launched
	// see: http://android-developers.blogspot.co.uk/2009/01/can-i-use-this-intent.html
	public static boolean isIntentAvailable(Context context, String action) {
		final PackageManager packageManager = context.getPackageManager();
		final Intent intent = new Intent(action);
		List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
		return list.size() > 0;
	}

	/**
	 * Use resources.getDimensionPixelSize instead - manual method left here only as a reminder
	 */
	@Deprecated
	public static int dipToPx(Resources resources, int dip) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 170 - 8, resources.getDisplayMetrics()));
	}

	/**
	 * Set colour filters for a button so that the standard resources can be used in different colours
	 *
	 * @param button       The button to set colour filters for
	 * @param defaultColor The normal (untouched) colour for the button
	 * @param touchedColor The touch colour - currently ignored. For API 11 and above the coloured filter is applied to
	 *                     the touched button; for For API 10 and below the normal platform button touch colour is used
	 */
	public static void setButtonColorFilters(View button, final int defaultColor, final int touchedColor) {
		Drawable background = button.getBackground();
		if (background == null) {
			return;
		}

		final LightingColorFilter normalColour;
		// use the requested colour to replace normal buttons; for white we lighten the existing colour instead
		if (defaultColor != 0xffffffff) {
			// float[] hsv = new float[3];
			// Color.colorToHSV(defaultColor, hsv);
			// hsv[1] = 0.95f; // fully saturate and slightly darken the requested colour to improve display
			// hsv[2] *= 0.88f;
			// normalColour = new LightingColorFilter(Color.TRANSPARENT, Color.HSVToColor(hsv));
			normalColour = new LightingColorFilter(Color.TRANSPARENT, defaultColor);
		} else {
			normalColour = new LightingColorFilter(0x00EAEAEA, Color.TRANSPARENT);
		}

		background.setColorFilter(normalColour);
	}

	public static class MarginCorrectorPrefsContainer {
		final int mViewId;
		final boolean mIgnoreLeft;
		final boolean mIgnoreTop;
		final boolean mIgnoreRight;
		final boolean mIgnoreBottom;

		public MarginCorrectorPrefsContainer(int viewId) {
			this(viewId, false, false, false, false);
		}

		public MarginCorrectorPrefsContainer(int viewId, boolean ignoreLeft, boolean ignoreTop, boolean ignoreRight,
											 boolean ignoreBottom) {
			mViewId = viewId;
			mIgnoreLeft = ignoreLeft;
			mIgnoreTop = ignoreTop;
			mIgnoreRight = ignoreRight;
			mIgnoreBottom = ignoreBottom;
		}
	}

	// better fullscreen with insets (fix bugs with incorrect margins when switching between fullscreen and normal views)
	// see: https://stackoverflow.com/a/50775459/1993220 and https://chris.banes.dev/2019/04/12/insets-listeners-to-layouts/
	public static void addFullscreenMarginsCorrectorListener(final Activity activity, int rootView,
															 final MarginCorrectorPrefsContainer[] insetViews) {
		ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(rootView), new OnApplyWindowInsetsListener() {
			@Override
			public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
				// perhaps related to notch handling (see themes-v28) for some reason this gets called twice, once all zero
				int left = insets.getSystemWindowInsetLeft();
				int top = insets.getSystemWindowInsetTop();
				int right = insets.getSystemWindowInsetRight();
				int bottom = insets.getSystemWindowInsetBottom();

				if (left != 0 || top != 0 || right != 0 || bottom != 0) {
					for (MarginCorrectorPrefsContainer viewContainer : insetViews) {
						View controlsView = activity.findViewById(viewContainer.mViewId);
						if (controlsView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
							ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) controlsView.getLayoutParams();
							p.setMargins(viewContainer.mIgnoreLeft ? p.leftMargin : left,
									viewContainer.mIgnoreTop ? p.topMargin : top,
									viewContainer.mIgnoreRight ? p.rightMargin : right,
									viewContainer.mIgnoreBottom ? p.bottomMargin : bottom);
							controlsView.requestLayout();
						}
					}
				}
				return insets.consumeSystemWindowInsets();
			}
		});
	}
}
