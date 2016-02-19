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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
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
import android.util.Log;
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

	/**
	 * Use acquireKeepScreenOn instead - key guard lock method left here only as a reminder
	 * 
	 * Requires <uses-permission android:name="android.permission.DISABLE_KEYGUARD" />
	 */
	@Deprecated
	public static KeyguardManager.KeyguardLock acquireKeyguardLock(Activity activity) {
		KeyguardManager keyguardManager = (KeyguardManager) activity.getSystemService(Activity.KEYGUARD_SERVICE);
		KeyguardManager.KeyguardLock keyguardLock = keyguardManager.newKeyguardLock(Activity.KEYGUARD_SERVICE);
		keyguardLock.disableKeyguard();
		return keyguardLock;
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

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static void refreshActionBar(Activity activity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			activity.invalidateOptionsMenu();
		}
	}

	/**
	 * Use reflection to configure the most common ActionBar properties (so we can target API < 11)
	 * 
	 * @param activity
	 * @param setDisplayHomeAsUpEnabled
	 * @param setDisplayShowTitleEnabled
	 * @param setTitle
	 * @param setSubtitle
	 */
	public static void configureActionBar(Activity activity, boolean setDisplayHomeAsUpEnabled,
			boolean setDisplayShowTitleEnabled, int setTitle, int setSubtitle) {
		configureActionBar(activity, setDisplayHomeAsUpEnabled, setDisplayShowTitleEnabled,
				setTitle != 0 ? activity.getString(setTitle) : null, setSubtitle != 0 ? activity.getString(setSubtitle)
						: null);
	}

	/**
	 * Use reflection to configure the most common ActionBar properties (so we can target API < 11)
	 * 
	 * @param activity
	 * @param setDisplayHomeAsUpEnabled
	 * @param setDisplayShowTitleEnabled
	 * @param setTitle
	 * @param setSubtitle
	 */
	public static void configureActionBar(Activity activity, boolean setDisplayHomeAsUpEnabled,
			boolean setDisplayShowTitleEnabled, String setTitle, String setSubtitle) {
		try {
			Class<?> activityClass = Class.forName("android.app.Activity");
			Method getActionBarMethod = activityClass.getMethod("getActionBar");
			if (getActionBarMethod != null) {
				Object actionBar = getActionBarMethod.invoke(activity, (Object[]) null);
				if (actionBar == null) {
					throw new NoSuchMethodException();
				}
				Class<?> actionBarClass = actionBar.getClass();
				Method actionBarMethod = null;

				try {
					actionBarMethod = actionBarClass.getMethod("setDisplayHomeAsUpEnabled", Boolean.TYPE);
					if (actionBarMethod != null) {
						actionBarMethod.invoke(actionBar, setDisplayHomeAsUpEnabled);
					}
				} catch (IllegalArgumentException e) {
				} catch (IllegalAccessException e) {
				} catch (InvocationTargetException e) {
				}

				try {
					actionBarMethod = actionBarClass.getMethod("setDisplayShowTitleEnabled", Boolean.TYPE);
					if (actionBarMethod != null) {
						actionBarMethod.invoke(actionBar, setDisplayShowTitleEnabled);
					}
				} catch (IllegalArgumentException e) {
				} catch (IllegalAccessException e) {
				} catch (InvocationTargetException e) {
				}

				if (setTitle != null) {
					try {
						actionBarMethod = actionBarClass.getMethod("setTitle", CharSequence.class);
						if (actionBarMethod != null) {
							actionBarMethod.invoke(actionBar, setTitle);
						}
					} catch (IllegalArgumentException e) {
					} catch (IllegalAccessException e) {
					} catch (InvocationTargetException e) {
					}
				}

				if (setSubtitle != null) {
					try {
						actionBarMethod = actionBarClass.getMethod("setSubtitle", CharSequence.class);
						if (actionBarMethod != null) {
							actionBarMethod.invoke(actionBar, setSubtitle);
						}
					} catch (IllegalArgumentException e) {
					} catch (IllegalAccessException e) {
					} catch (InvocationTargetException e) {
					}
				}
			}
		} catch (ClassNotFoundException e) {
		} catch (SecurityException e) {
		} catch (NoSuchMethodException e) {
			Log.d(LOG_TAG, "ActionBar not present; not configuring");
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		} catch (Throwable t) {
		}
	}

	/**
	 * Use reflection to hide/show the action bar
	 * 
	 * @param activity
	 */
	public static void actionBarVisibility(Activity activity, boolean visible) {
		try {
			Class<?> activityClass = Class.forName("android.app.Activity");
			Method getActionBarMethod = activityClass.getMethod("getActionBar");
			if (getActionBarMethod != null) {
				Object actionBar = getActionBarMethod.invoke(activity, (Object[]) null);
				if (actionBar == null) {
					throw new NoSuchMethodException();
				}
				Class<?> actionBarClass = actionBar.getClass();
				Method actionBarMethod = null;

				try {
					actionBarMethod = actionBarClass.getMethod(visible ? "show" : "hide");
					if (actionBarMethod != null) {
						actionBarMethod.invoke(actionBar, (Object[]) null);
					}
				} catch (IllegalArgumentException e) {
				} catch (IllegalAccessException e) {
				} catch (InvocationTargetException e) {
				}
			}
		} catch (ClassNotFoundException e) {
		} catch (SecurityException e) {
		} catch (NoSuchMethodException e) {
			Log.d(LOG_TAG, "ActionBar not present; not configuring");
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		} catch (Throwable t) {
		}
	}

	/**
	 * Use reflection to add action bar tabs (so we can target API < 11)
	 * 
	 * @param activity
	 * @param tabs
	 * @param listener
	 */
	public static void addActionBarTabs(Activity activity, ReflectionTab[] tabs, final ReflectionTabListener listener) {
		try {
			Class<?> activityClass = Class.forName("android.app.Activity");
			Method getActionBarMethod = activityClass.getMethod("getActionBar");
			if (getActionBarMethod != null) {
				Object actionBar = getActionBarMethod.invoke(activity, (Object[]) null);
				if (actionBar == null) {
					throw new NoSuchMethodException();
				}
				Class<?> actionBarClass = actionBar.getClass();
				Method actionBarMethod = actionBarClass.getMethod("setNavigationMode", Integer.TYPE);
				if (actionBarMethod != null) {
					Class<?> tabListenerClass = Class.forName("android.app.ActionBar$TabListener");
					Object originalListener = Proxy.newProxyInstance(tabListenerClass.getClassLoader(),
							new java.lang.Class[] { tabListenerClass }, new InvocationHandler() {
								@Override
								public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
										throws java.lang.Throwable {
									if (listener != null) {
										String methodName = method.getName();
										Class<?> tabClass = Class.forName("android.app.ActionBar$Tab");
										Method actionBarMethod = tabClass.getMethod("getTag");
										if (actionBarMethod != null) {
											Object tabTag = actionBarMethod.invoke(args[0], (Object[]) null);
											if (tabTag != null && tabTag instanceof Integer) {
												if (methodName.equals("onTabSelected")) {
													listener.onTabSelected((Integer) tabTag);
												} else if (methodName.equals("onTabReselected")) {
													listener.onTabReselected((Integer) tabTag);
												} else if (methodName.equals("onTabUnselected")) {
													listener.onTabUnselected((Integer) tabTag);
												}
											}
										}
									}
									return null;
								}
							}); // http://stackoverflow.com/a/9583681

					Class<?> barClass = Class.forName("android.app.ActionBar");
					actionBarMethod.invoke(actionBar, barClass.getField("NAVIGATION_MODE_TABS").getInt(null));

					Method tabMethod = actionBarClass.getMethod("newTab");
					Object actionTab = null;
					int tabPosition = 0;
					if (tabMethod != null) {
						for (ReflectionTab newTab : tabs) {
							actionTab = tabMethod.invoke(actionBar, (Object[]) null);
							Class<?> actionTabClass = actionTab.getClass();
							Method actionTabMethod = null;
							actionTabMethod = actionTabClass.getMethod("setTag", Object.class);
							if (actionTabMethod != null) {
								actionTabMethod.invoke(actionTab, newTab.mTabId);
							}
							if (newTab.mDrawableId != 0) {
								actionTabMethod = actionTabClass.getMethod("setIcon", Integer.TYPE);
								if (actionTabMethod != null) {
									actionTabMethod.invoke(actionTab, newTab.mDrawableId);
								}
							}
							if (newTab.mTabText != null) {
								actionTabMethod = actionTabClass.getMethod("setText", CharSequence.class);
								if (actionTabMethod != null) {
									actionTabMethod.invoke(actionTab, newTab.mTabText);
								}
							}
							actionTabMethod = actionTabClass.getMethod("setTabListener", tabListenerClass);
							if (actionTabMethod != null) {
								actionTabMethod.invoke(actionTab, originalListener);
							}
							if (actionTab != null) {
								Class<?> tabClass = Class.forName("android.app.ActionBar$Tab");
								actionBarMethod = actionBarClass.getMethod("addTab", tabClass, Integer.TYPE,
										Boolean.TYPE);
								if (actionBarMethod != null) {
									actionBarMethod.invoke(actionBar, actionTab, tabPosition, newTab.mTabSelected);
								}
							}
							tabPosition += 1;
						}
					}
				}
			}
		} catch (ClassNotFoundException e) {
		} catch (SecurityException e) {
		} catch (NoSuchMethodException e) {
			Log.d(LOG_TAG, "ActionBar not present; not adding tabs");
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		} catch (NoSuchFieldException e) {
		} catch (Throwable t) {
		}
	}

	/**
	 * Use reflection to remove all action bar tabs (so we can target API < 11)
	 * 
	 * @param activity
	 * @param tabs
	 * @param listener
	 */
	public static void removeActionBarTabs(Activity activity) {
		try {
			Class<?> activityClass = Class.forName("android.app.Activity");
			Method getActionBarMethod = activityClass.getMethod("getActionBar");
			if (getActionBarMethod != null) {
				Object actionBar = getActionBarMethod.invoke(activity, (Object[]) null);
				if (actionBar == null) {
					throw new NoSuchMethodException();
				}
				Class<?> actionBarClass = actionBar.getClass();
				Method actionBarMethod = actionBarClass.getMethod("removeAllTabs");
				if (actionBarMethod != null) {
					actionBarMethod.invoke(actionBar, (Object[]) null);
				}
			}
		} catch (ClassNotFoundException e) {
		} catch (SecurityException e) {
		} catch (NoSuchMethodException e) {
			Log.d(LOG_TAG, "ActionBar not present; not removing tabs");
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		} catch (Throwable t) {
		}
	}

	public static class ReflectionTab {
		public Integer mTabId = 0;
		public int mDrawableId = 0;
		public String mTabText = null;
		public boolean mTabSelected = false;

		@SuppressWarnings("unused")
		private ReflectionTab() {
		};

		public ReflectionTab(int tabId, int drawableId, String tabText, boolean tabSelected) {
			mTabId = tabId;
			mDrawableId = drawableId;
			mTabText = tabText;
			mTabSelected = tabSelected;
		}

		public ReflectionTab(int tabId, int drawableId, String tabText) {
			this(tabId, drawableId, tabText, false);
		}

		public ReflectionTab(int tabId, int drawableId) {
			this(tabId, drawableId, null, false);
		}

		public ReflectionTab(int tabId, String tabText) {
			this(tabId, 0, tabText, false);
		}
	}

	public interface ReflectionTabListener {

		void onTabSelected(int tabId);

		void onTabReselected(int tabId);

		void onTabUnselected(int tabId);
	}
}
