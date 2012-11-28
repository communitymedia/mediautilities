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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Build.VERSION;
import android.os.Environment;
import android.provider.MediaStore;

public class IOUtilities {
	public static final int IO_BUFFER_SIZE = 4 * 1024;

	public static void copyFileDirectory(File sourceLocation, File targetLocation) throws IOException {
		if (sourceLocation.isDirectory()) {
			if (!targetLocation.exists()) {
				targetLocation.mkdirs();
			}
			File[] files = sourceLocation.listFiles();
			for (File file : files) {
				InputStream in = new FileInputStream(file);
				OutputStream out = new FileOutputStream(targetLocation + "/" + file.getName());

				// copy the bits from input stream to output stream
				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				in.close();
				out.close();
			}
		}
	}

	public static void copyFile(File sourceLocation, File targetLocation) throws IOException {
		InputStream in = new FileInputStream(sourceLocation);
		copyFile(in, targetLocation);
	}

	public static void copyFile(InputStream in, File targetLocation) throws IOException {
		OutputStream out = new FileOutputStream(targetLocation);

		byte[] buf = new byte[IO_BUFFER_SIZE];
		int len;

		// copy the bits from input stream to output stream
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}

		in.close(); // TODO: should we do this here?
		out.close();
	}

	public static boolean copyResource(Resources resources, int resourceID, File targetLocation) {
		Bitmap resourceImage = BitmapFactory.decodeResource(resources, resourceID);
		FileOutputStream fileOutputStream = null;
		BufferedOutputStream bufferedOutputStream = null;
		try {
			fileOutputStream = new FileOutputStream(targetLocation);
			bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
			resourceImage.compress(CompressFormat.PNG, 100, bufferedOutputStream);
			bufferedOutputStream.flush();
			bufferedOutputStream.close();
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				bufferedOutputStream.close();
			} catch (NullPointerException e) {
			} catch (IOException e) {
			}
		}
		return true;
	}

	public static byte[] readFileToByteArray(String file) throws IOException {
		return readFileToByteArray(new File(file));
	}

	public static byte[] readFileToByteArray(File file) throws IOException {
		RandomAccessFile f = new RandomAccessFile(file, "r");
		try {
			// get and check length
			long longlength = f.length();
			int length = (int) longlength;
			if (length != longlength)
				throw new IOException("File size >= 2 GB");

			// read file and return data
			byte[] data = new byte[length];
			f.readFully(data);
			return data;
		} finally {
			f.close();
		}
	}

	/**
	 * Closes the specified stream.
	 * 
	 * @param stream The stream to close.
	 */
	public static boolean closeStream(Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
				return true;
			} catch (IOException e) {
				// Log.e(LOG_TAG, "Could not close stream", e);
			} catch (Throwable t) {
			}
		}
		return false;
	}

	public static File ensureDirectoryExists(File directory) throws IOException {
		if (!directory.exists()) {
			directory.mkdirs();
			// TODO: does this actually work for stopping the media scanner?
			new File(directory, MediaStore.MEDIA_IGNORE_FILENAME).createNewFile(); // don't media scan
		}
		return directory;
	}

	public static void setFullyPublic(File file) {
		file.setReadable(true, false);
		file.setWritable(true, false);
		file.setExecutable(true, false);
	}

	public static File getNewCachePath(Context context, String pathName) {
		return getNewCachePath(context, pathName, false);
	}

	public static File getNewCachePath(Context context, String pathName, boolean deleteExisting) {
		File newCacheDir = new File(context.getCacheDir(), pathName);
		if (!newCacheDir.exists()) {
			if (!newCacheDir.mkdirs()) {
				return null;
			}
		} else if (deleteExisting) {
			if (newCacheDir.isDirectory()) {
				for (File child : newCacheDir.listFiles()) {
					deleteRecursive(child);
				}
			}
		}
		return newCacheDir;
	}

	public static boolean externalStorageIsWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true; // available and writeable
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			return false; // available but read only
		} else {
			return false; // not available
		}

	}

	// TODO: keep track of states:
	// http://developer.android.com/reference/android/os/Environment.html#getExternalStorageDirectory()
	public static File getNewStoragePath(Context context, String pathName, boolean preferExternal) {
		File newFilesDir;
		if (preferExternal && externalStorageIsWritable()) {
			newFilesDir = new File(context.getExternalFilesDir(null), pathName);
		} else {
			newFilesDir = new File(context.getFilesDir(), pathName);
		}
		if (!newFilesDir.exists()) {
			if (!newFilesDir.mkdirs()) {
				return null;
			}
		}
		return newFilesDir;
	}

	public static File getExternalStoragePath(Context context, String pathName) {
		File newFilesDir;
		if (externalStorageIsWritable()) {
			newFilesDir = new File(context.getExternalFilesDir(null), pathName);
		} else {
			return null;
		}
		if (!newFilesDir.exists()) {
			if (!newFilesDir.mkdirs()) {
				return null;
			}
		}
		return newFilesDir;
	}

	public static void deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory()) {
			for (File child : fileOrDirectory.listFiles()) {
				deleteRecursive(child);
			}
		}

		fileOrDirectory.delete();
	}

	public static boolean deleteFiles(File file) {
		if (file.exists()) {
			String deleteCmd = "rm -r " + file.getAbsolutePath();
			Runtime runtime = Runtime.getRuntime();
			try {
				runtime.exec(deleteCmd);
				return true;
			} catch (IOException e) {
			}
		}
		return false;
	}

	// see:
	// http://stackoverflow.com/questions/4004650/android-2-2-how-do-i-detect-if-i-am-installed-on-the-sdcard-or-not
	@SuppressLint("SdCardPath")
	public static boolean isInstalledOnSdCard(Context context) {

		// better method for API level 8 and higher
		if (VERSION.SDK_INT > android.os.Build.VERSION_CODES.ECLAIR_MR1) {
			PackageManager packageManager = context.getPackageManager();
			try {
				PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
				ApplicationInfo applicationInfo = packageInfo.applicationInfo;
				return (applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE;
			} catch (NameNotFoundException e) {
				// ignore
			}
		}

		// for API level 7 and below - check files directory by name
		try {
			String filesDir = context.getFilesDir().getAbsolutePath();
			if (filesDir.startsWith("/data/")) {
				return false;
			} else if (filesDir.contains("/mnt/") || filesDir.contains("/sdcard/")) {
				return true;
			}
		} catch (Throwable e) {
			// ignore
		}

		return false;
	}

	public static boolean mustCreateTempDirectory(Context context) {
		return !IOUtilities.isInstalledOnSdCard(context)
				|| context.getCacheDir().getAbsolutePath().startsWith("/data/");
	}

	// TODO: move to StringUtilities
	// see: http://stackoverflow.com/questions/941272/
	public static String removeExtension(String s) {

		String separator = System.getProperty("file.separator");
		String filename;

		// Remove the path upto the filename.
		int lastSeparatorIndex = s.lastIndexOf(separator);
		if (lastSeparatorIndex == -1) {
			filename = s;
		} else {
			filename = s.substring(lastSeparatorIndex + 1);
		}

		// Remove the extension.
		int extensionIndex = filename.lastIndexOf(".");
		if (extensionIndex == -1)
			return filename;

		return filename.substring(0, extensionIndex);
	}

	// pass -1 to read the entire file
	public static String getFileContentSnippet(String filePath, int snippetLength) {
		StringBuilder fileString = new StringBuilder();
		FileInputStream fileStream = null;
		BufferedReader bufferedReader = null;
		String currentLine;
		try {
			fileStream = new FileInputStream(filePath);
			bufferedReader = new BufferedReader(new InputStreamReader(fileStream));
			while ((currentLine = bufferedReader.readLine()) != null && fileString.length() < snippetLength) {
				fileString.append(currentLine);
				fileString.append("\n");
			}
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		} finally {
			IOUtilities.closeStream(bufferedReader);
			IOUtilities.closeStream(fileStream);
		}

		String textSnippet = fileString.toString().trim().replace("\n", " ");
		int textLength = textSnippet.length();
		if (textLength > 0) {
			textSnippet = textSnippet.substring(0, textLength > snippetLength ? snippetLength : textLength);
		}

		return textSnippet;
	}

	public static String getFileContents(String filePath) {
		StringBuilder fileString = new StringBuilder();
		if (new File(filePath).exists()) {
			FileInputStream fileStream = null;
			BufferedReader bufferedReader = null;
			String currentLine;
			try {
				fileStream = new FileInputStream(filePath);
				bufferedReader = new BufferedReader(new InputStreamReader(fileStream));
				while ((currentLine = bufferedReader.readLine()) != null) {
					fileString.append(currentLine);
					fileString.append("\n");
				}
			} catch (FileNotFoundException e) {
			} catch (IOException e) {
			} finally {
				IOUtilities.closeStream(bufferedReader);
				IOUtilities.closeStream(fileStream);
			}
		}
		if (fileString.length() > 0) {
			fileString.deleteCharAt(fileString.length() - 1);
		}
		return fileString.toString();
	}

	public static String getFileExtension(String fileName) {
		return fileName == null ? null : fileName.substring(fileName.lastIndexOf(".") + 1);
	}

	public static boolean fileExtensionIs(String fileName, String extension) {
		return fileName == null ? false : fileName.toLowerCase(Locale.ENGLISH).endsWith(
				extension.toLowerCase(Locale.ENGLISH));
	}

	public static File newDatedFileName(File baseDirectory, String fileExtension) {
		Calendar logStartTime;
		StringBuilder newFileName = new StringBuilder();
		File newFile = null;
		boolean fileExists = true;
		while (fileExists) {
			logStartTime = Calendar.getInstance();
			newFileName.setLength(0);
			newFileName.append(logStartTime.get(Calendar.YEAR));
			newFileName.append("-");
			newFileName.append(String.format("%02d", logStartTime.get(Calendar.MONTH) + 1));
			newFileName.append("-");
			newFileName.append(String.format("%02d", logStartTime.get(Calendar.DAY_OF_MONTH)));
			newFileName.append("-");
			newFileName.append(String.format("%02d", logStartTime.get(Calendar.HOUR_OF_DAY)));
			newFileName.append("-");
			newFileName.append(String.format("%02d", logStartTime.get(Calendar.MINUTE)));
			newFileName.append("-");
			newFileName.append(String.format("%02d", logStartTime.get(Calendar.SECOND)));
			newFileName.append(".");
			newFileName.append(fileExtension);
			newFile = new File(baseDirectory, newFileName.toString());
			fileExists = newFile.exists();
		}
		return newFile;
	}
}
