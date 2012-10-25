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

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.LightingColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public class UIUtilities {

	public static void setPixelDithering(Window window) {
		if (window != null) {
			// better gradient drawables
			window.setFormat(PixelFormat.RGBA_8888);
			window.addFlags(WindowManager.LayoutParams.FLAG_DITHER);
		}
	}

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

	public static int getNaturalScreenOrientation(WindowManager windowManager) {
		Display display = windowManager.getDefaultDisplay();
		int width = 0;
		int height = 0;
		switch (display.getRotation()) {
			case Surface.ROTATION_0:
			case Surface.ROTATION_180:
				width = display.getWidth();
				height = display.getHeight();
				break;
			case Surface.ROTATION_90:
			case Surface.ROTATION_270:
				width = display.getHeight();
				height = display.getWidth();
				break;
			default:
				break;
		}

		if (width > height) {
			return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
		}
		return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
	}

	public static void setScreenOrientationFixed(Activity activity, boolean orientationFixed) {
		if (orientationFixed) {
			WindowManager windowManager = activity.getWindowManager();
			boolean naturallyPortrait = getNaturalScreenOrientation(windowManager) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
			switch (windowManager.getDefaultDisplay().getRotation()) {
				case Surface.ROTATION_0:
					activity.setRequestedOrientation(naturallyPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
							: ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
					break;
				case Surface.ROTATION_90:
					activity.setRequestedOrientation(naturallyPortrait ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
							: ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
					break;
				case Surface.ROTATION_180:
					activity.setRequestedOrientation(naturallyPortrait ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
							: ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
					break;
				case Surface.ROTATION_270:
					activity.setRequestedOrientation(naturallyPortrait ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
							: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
					break;
			}
		} else {
			activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		}
	}

	// requires <uses-permission android:name="android.permission.WAKE_LOCK" />
	@Deprecated
	public static PowerManager.WakeLock acquireWakeLock(Activity activity, String tag) {
		PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, tag);
		wakeLock.acquire();
		return wakeLock;
	}

	public static void acquireKeepScreenOn(Window window) {
		window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	public static void releaseKeepScreenOn(Window window) {
		window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	// requires <uses-permission android:name="android.permission.DISABLE_KEYGUARD" />
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

	/**
	 * Use resources.getDimensionPixelSize instead
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
	public static void setButtonColorFilters(View button, final Integer defaultColor, final int touchedColor) {

		final LightingColorFilter normalColour = new LightingColorFilter(defaultColor, 0x00000000);
		// final LightingColorFilter touchedColour = new LightingColorFilter(colourTouched, 0x00000000);

		button.getBackground().setColorFilter(normalColour);
		button.setOnTouchListener(new OnTouchListener() {
			Rect viewRect;

			private void setNormalColour(View v) {
				v.getBackground().setColorFilter(normalColour);
			}

			private void setTouchedColour(View v) {
				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB) {
					// better colours before Honeycomb
					// mode = PorterDuff.Mode.MULTIPLY;
					v.getBackground().clearColorFilter();
				} else {
					// TODO: colours are still bad on Honeycomb, but if we clear the colour filter there is a flicker
					// while the display is updated.

					// Make sure to use proper touched colours: AP11 uses transparent buttons, so use a different
					// approach
					// mode = PorterDuff.Mode.SRC_IN;
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
			Log.d(AndroidUtilities.LOG_TAG, "ActionBar not present; not configuring");
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
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
			Log.d(AndroidUtilities.LOG_TAG, "ActionBar not present; not adding tabs");
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		} catch (NoSuchFieldException e) {
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
				Class<?> actionBarClass = actionBar.getClass();
				Method actionBarMethod = actionBarClass.getMethod("removeAllTabs");
				if (actionBarMethod != null) {
					actionBarMethod.invoke(actionBar, (Object[]) null);
				}
			}
		} catch (ClassNotFoundException e) {
		} catch (SecurityException e) {
		} catch (NoSuchMethodException e) {
			Log.d(AndroidUtilities.LOG_TAG, "ActionBar not present; not removing tabs");
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
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
