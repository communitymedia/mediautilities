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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

public class DebugUtilities {
	public static String getLogTag(Object o) {
		return o.getClass().getName();
	}

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

	public static String getDeviceBrandProduct() {
		return Build.BRAND + "/" + Build.PRODUCT + "/" + Build.DEVICE;
	}

	// some devices cannot use SoundPool and MediaPlayer simultaneously due to a bug
	public static boolean hasSoundPoolBug() {
		ArrayList<String> devices = new ArrayList<String>();
		devices.add("samsung/GT-P7510/GT-P7510"); // Samsung Galaxy Tab 10.1

		return devices.contains(getDeviceBrandProduct());
	}

	// some devices cannot record audio on internal memory
	public static boolean needsSDCardToRecordAudio() {
		ArrayList<String> devices = new ArrayList<String>();
		devices.add("samsung/GT-S5830i/GT-S5830i"); // Samsung Galaxy Ace i

		return devices.contains(getDeviceBrandProduct());
	}

	public static boolean supportsLandscapeCameraOnly() {
		ArrayList<String> devices = new ArrayList<String>();
		devices.add("samsung/GT-S5830i/GT-S5830i"); // Samsung Galaxy Ace i

		return devices.contains(getDeviceBrandProduct());
	}
}
