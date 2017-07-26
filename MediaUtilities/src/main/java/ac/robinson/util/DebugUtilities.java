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

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DebugUtilities {
	public static String getLogTag(Object o) {
		return o.getClass().getName();
	}

	@Deprecated // does not work with recent Android Studio - see: https://code.google.com/p/android/issues/detail?id=220039
	public static String getApplicationBuildTime(PackageManager packageManager, String packageName) {
		try {
			ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
			ZipFile zipFile = new ZipFile(applicationInfo.sourceDir);
			ZipEntry zipEntry = zipFile.getEntry("classes.dex");
			SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yy HH:mm", Locale.ENGLISH);
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			return dateFormat.format(new java.util.Date(zipEntry.getTime()));
		} catch (Exception e) {
		}
		return "unknown";
	}

	public static String getScreenDensityString(Resources resources) {
		switch (resources.getDisplayMetrics().densityDpi) {
			case DisplayMetrics.DENSITY_LOW:
				return "ldpi";
			case DisplayMetrics.DENSITY_HIGH:
				return "hdpi";
			case DisplayMetrics.DENSITY_XHIGH:
				return "xhdpi";
			default: // medium is the default
				return "mdpi";
		}
	}

	public static String getScreenSizeString(Resources resources) {
		switch (resources.getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) {
			case Configuration.SCREENLAYOUT_SIZE_SMALL:
				return "small";
			case Configuration.SCREENLAYOUT_SIZE_NORMAL:
				return "normal";
			case Configuration.SCREENLAYOUT_SIZE_LARGE:
				return "large";
			case Configuration.SCREENLAYOUT_SIZE_XLARGE:
				return "xlarge";
			default:
				return "undefined";
		}
	}

	public static String getDeviceDebugSummary(WindowManager windowManager, Resources resources) {
		Point screenSize = UIUtilities.getScreenSize(windowManager);
		return Build.MODEL + ", " + getDeviceBrandProduct() + ", v" + Build.VERSION.SDK_INT + " ("
				+ Build.VERSION.RELEASE + "), " + screenSize.x + "x" + screenSize.y + "-"
				+ getScreenDensityString(resources).replace("dpi", "") + "-"
				+ getScreenSizeString(resources).substring(0, 1);
	}

	public static String getDeviceBrandProduct() {
		return Build.BRAND + "/" + Build.PRODUCT + "/" + Build.DEVICE;
	}

	// some devices cannot use SoundPool and MediaPlayer simultaneously due to a bug
	// - currently just Galaxy Tab 10.1 with original SDK version
	public static boolean hasSoundPoolBug() {
		ArrayList<String> devices = new ArrayList<>();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			devices.add("samsung/GT-P7510/GT-P7510"); // Samsung Galaxy Tab 10.1
		}

		return devices.contains(getDeviceBrandProduct());
	}

	// some devices have a bug where the internal storage folder requires storage permission to be granted
	// - currently just Galaxy Tab A 10.1 (6.0.1, v23)
	public static boolean hasAppDataFolderPermissionBug() {
		ArrayList<String> devices = new ArrayList<>();
		devices.add("samsung/gt510wifixx/gt510wifi"); // Samsung Galaxy Tab A 10.1
		return devices.contains(getDeviceBrandProduct());
	}

	// some devices can only record AMR audio
	public static boolean supportsAMRAudioRecordingOnly() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1) {
			return true; // no pre-v10 devices support anything other than AMR
		}

		ArrayList<String> devices = new ArrayList<String>();
		devices.add("samsung/GT-S5830/GT-S5830"); // Samsung Galaxy Ace
		devices.add("samsung/GT-S5830i/GT-S5830i"); // Samsung Galaxy Ace i
		// devices.add("samsung/GT-S5360/GT-S5360"); // Samsung Galaxy Y - probable, but not certain

		return devices.contains(getDeviceBrandProduct());
	}

	public static boolean supportsLandscapeCameraOnly() {
		ArrayList<String> devices = new ArrayList<String>();
		devices.add("samsung/GT-S5830/GT-S5830"); // Samsung Galaxy Ace
		devices.add("samsung/GT-S5830i/GT-S5830i"); // Samsung Galaxy Ace i
		// devices.add("samsung/GT-S5360/GT-S5360"); // Samsung Galaxy Y - probable, but not certain

		return devices.contains(getDeviceBrandProduct());
	}
}
