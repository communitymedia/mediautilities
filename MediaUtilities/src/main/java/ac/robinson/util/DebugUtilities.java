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
		return o.getClass().getSimpleName();
	}

	public static String getScreenDensityString(Resources resources) {
		switch (resources.getDisplayMetrics().densityDpi) {
			case DisplayMetrics.DENSITY_LOW:
				return "ldpi";
			case DisplayMetrics.DENSITY_HIGH:
				return "hdpi";
			case DisplayMetrics.DENSITY_XHIGH:
				return "xhdpi";
			case DisplayMetrics.DENSITY_XXHIGH:
				return "x2hdpi";
			case DisplayMetrics.DENSITY_XXXHIGH:
				return "x3hdpi";
			case DisplayMetrics.DENSITY_MEDIUM:
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
		return Build.MODEL + ", " + getDeviceBrandProduct() + ", v" + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE +
				"), " + Build.CPU_ABI + ", " + screenSize.x + "x" + screenSize.y + "-" +
				getScreenDensityString(resources).replace("dpi", "") + "-" + getScreenSizeString(resources).substring(0, 1);
	}

	public static String getDeviceBrandProduct() {
		return Build.BRAND + "/" + Build.PRODUCT + "/" + Build.DEVICE;
	}

	// some devices have a bug where the internal storage folder requires storage permission to be granted
	// - currently just Galaxy Tab A 10.1 (6.0.1, v23)
	public static boolean hasAppDataFolderPermissionBug() {
		ArrayList<String> devices = new ArrayList<>();
		devices.add("samsung/gt510wifixx/gt510wifi"); // Samsung Galaxy Tab A 10.1
		return devices.contains(getDeviceBrandProduct());
	}

	public static boolean supportsLandscapeCameraOnly() {
		// TODO: should probably be deprecated as these devices don't support our minimum SDK level
		// TODO: kept for now as there may be other devices in future that have this limitation
		ArrayList<String> devices = new ArrayList<>();
		devices.add("samsung/GT-S5830/GT-S5830"); // Samsung Galaxy Ace
		devices.add("samsung/GT-S5830i/GT-S5830i"); // Samsung Galaxy Ace i
		// devices.add("samsung/GT-S5360/GT-S5360"); // Samsung Galaxy Y - probable, but not certain

		return devices.contains(getDeviceBrandProduct());
	}
}
