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

import android.annotation.TargetApi;
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
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.List;

public class UIUtilities {

	private static final String LOG_TAG = "UIUtilities";

	/**
	 * Enable pixel dithering for this window (but only in API < 17)
	 * 
	 * @param window
	 */
	@SuppressWarnings("deprecation")
	public static void setPixelDithering(Window window) {
		if (window != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) { // deprecated from API 17+
			// better gradient drawables
			window.setFormat(PixelFormat.RGBA_8888);
			window.addFlags(WindowManager.LayoutParams.FLAG_DITHER);
		}
	}

	/**
	 * Get the current rotation of the screen, either 0, 90, 180 or 270 degrees
	 * 
	 * @param windowManager
	 * @return
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
		}
		return degrees;
	}

	/**
	 * Get the "natural" screen orientation - i.e. the orientation in which this device is designed to be used most
	 * often.
	 * 
	 * @param windowManager
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
				width = screenSize.y;
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

	@SuppressWarnings("deprecation")
	public static Point getScreenSize(WindowManager windowManager) {
		Point screenSize = new Point();
		Display display = windowManager.getDefaultDisplay();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			getPointScreenSize(display, screenSize);
		} else {
			screenSize.set(display.getWidth(), display.getHeight());
		}
		return screenSize;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private static void getPointScreenSize(Display display, Point screenSize) {
		display.getSize(screenSize);
	}

	/**
	 * Set the SurfaceHolder's type to SURFACE_TYPE_PUSH_BUFFERS, but only in API < 11 (after this it is set
	 * automatically by the system when needed)
	 * 
	 * @param holder
	 */
	@SuppressWarnings("deprecation")
	public static void setPushBuffers(SurfaceHolder holder) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static void setScreenOrientationFixed(Activity activity, boolean orientationFixed) {
		if (orientationFixed) {
			WindowManager windowManager = activity.getWindowManager();
			boolean naturallyPortrait = getNaturalScreenOrientation(windowManager) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
			int reversePortrait = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
			int reverseLandscape = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
				reversePortrait = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
				reverseLandscape = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
			}
			switch (windowManager.getDefaultDisplay().getRotation()) {
				case Surface.ROTATION_0:
					activity.setRequestedOrientation(naturallyPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
							: ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
					break;
				case Surface.ROTATION_90:
					activity.setRequestedOrientation(naturallyPortrait ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
							: reversePortrait);
					break;
				case Surface.ROTATION_180:
					activity.setRequestedOrientation(naturallyPortrait ? reversePortrait : reverseLandscape);
					break;
				case Surface.ROTATION_270:
					activity.setRequestedOrientation(naturallyPortrait ? reverseLandscape
							: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
					break;
			}
		} else {
			activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		}
	}

	public static void setFullScreen(final Window window) {
		if ((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != WindowManager.LayoutParams.FLAG_FULLSCREEN) {
			Handler handler = new Handler();
			handler.post(new Runnable() {
				@Override
				public void run() {
					try {
						WindowManager.LayoutParams attrs = window.getAttributes();
						attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
						window.setAttributes(attrs);
					} catch (Throwable t) {
					}
				}
			});
		}
	}

	public static void setNonFullScreen(final Window window) {
		if ((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == WindowManager.LayoutParams.FLAG_FULLSCREEN) {
			Handler handler = new Handler();
			handler.post(new Runnable() {
				@Override
				public void run() {
					try {
						WindowManager.LayoutParams attrs = window.getAttributes();
						attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
						window.setAttributes(attrs);
					} catch (Throwable t) {
					}
				}
			});
		}
	}

	/**
	 * Use acquireKeepScreenOn instead - wake lock method left here only as a reminder
	 * 
	 * Requires <uses-permission android:name="android.permission.WAKE_LOCK" />
	 */
	@Deprecated
	public static PowerManager.WakeLock acquireWakeLock(Activity activity, String tag) {
		PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, tag);
		wakeLock.acquire();
		return wakeLock;
	}

	@Deprecated
	public static void releaseWakeLock(PowerManager.WakeLock wakeLock) {
		wakeLock.release();
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
		return Math
				.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 170 - 8, resources.getDisplayMetrics()));
	}

	/**
	 * Set colour filters for a button so that the standard resources can be used in different colours
	 * 
	 * @param button The button to set colour filters for
	 * @param defaultColor The normal (untouched) colour for the button
	 * @param touchedColor The touch colour - currently ignored. For API 11 and above the coloured filter is applied to
	 *            the touched button; for For API 10 and below the normal platform button touch colour is used
	 */
	public static void setButtonColorFilters(View button, final int defaultColor, final int touchedColor) {

		Drawable background = button.getBackground();
		if (background == null) {
			return;
		}

		final LightingColorFilter normalColour;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			normalColour = new LightingColorFilter(defaultColor, Color.TRANSPARENT);
		} else {
			// use the requested colour to replace normal buttons; for white we lighten the existing colour instead
			if (defaultColor != 0xffffffff) {
//				float[] hsv = new float[3];
//				Color.colorToHSV(defaultColor, hsv);
//				hsv[1] = 0.95f; // fully saturate and slightly darken the requested colour to improve display
//				hsv[2] *= 0.88f;
//				normalColour = new LightingColorFilter(Color.TRANSPARENT, Color.HSVToColor(hsv));
				normalColour = new LightingColorFilter(Color.TRANSPARENT, defaultColor );
			} else {
				normalColour = new LightingColorFilter(0x00EAEAEA, Color.TRANSPARENT);
			}
		}

		background.setColorFilter(normalColour);

		// before Honeycomb the colour filter looks bad on a focused button, and if we use the blanking approach
		// (above) then we lose the gradient of a button; instead we remove the colour filter entirely
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			button.setOnFocusChangeListener(new OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (hasFocus) {
						v.getBackground().clearColorFilter();
					} else {
						v.getBackground().setColorFilter(normalColour);
					}
				}
			});
		}

		button.setOnTouchListener(new OnTouchListener() {
			Rect viewRect;

			private void setNormalColour(View v) {
				v.getBackground().setColorFilter(normalColour);
			}

			private void setTouchedColour(View v) {
				// before Honeycomb the colour filter looks bad on a pressed button, and if we use the blanking approach
				// (above) then we lose the gradient of a button; instead we remove the colour filter entirely
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
					v.getBackground().clearColorFilter();
				}
			}

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int eventAction = event.getAction();
				if (eventAction == MotionEvent.ACTION_DOWN) {
					viewRect = new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
					setTouchedColour(v);
				} else if (eventAction == MotionEvent.ACTION_MOVE) {
					// touch moved outside bounds
					if (!viewRect.contains(v.getLeft() + (int) event.getX(), v.getTop() + (int) event.getY())) {
						setNormalColour(v);
					} else {
						setTouchedColour(v);
					}
				} else if (eventAction == MotionEvent.ACTION_UP || eventAction == MotionEvent.ACTION_CANCEL) {
					setNormalColour(v);
				}

				return false;
			}

		});
	}
}
