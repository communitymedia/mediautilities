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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.io.BufferedInputStream;
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
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class IOUtilities {
	public static final int IO_BUFFER_SIZE = 4 * 1024;

	public static void copyFileDirectory(File sourceLocation, File targetLocation) throws IOException {
		if (sourceLocation.isDirectory()) {
			if (!targetLocation.exists()) {
				targetLocation.mkdirs();
			}
			File[] files = sourceLocation.listFiles();
			for (File file : files) {
				copyFile(file, new File(targetLocation, file.getName()));
			}
		}
	}

	public static void copyFile(File sourceLocation, File targetLocation) throws IOException {
		InputStream in = new FileInputStream(sourceLocation);
		copyFile(in, targetLocation);
	}

	public static void copyFile(File sourceLocation, OutputStream out) throws IOException {
		InputStream in = new FileInputStream(sourceLocation);
		copyFile(in, out);
	}

	public static void copyFile(InputStream in, File targetLocation) throws IOException {
		OutputStream out = new FileOutputStream(targetLocation);
		copyFile(in, out);
	}

	public static void copyFile(InputStream in, OutputStream out) throws IOException {
		BufferedInputStream inBuf = new BufferedInputStream(in, IO_BUFFER_SIZE);
		BufferedOutputStream outBuf = new BufferedOutputStream(out);
		byte[] buf = new byte[IO_BUFFER_SIZE];
		int len;

		// copy the bits from input stream to output stream
		while ((len = inBuf.read(buf)) > 0) {
			outBuf.write(buf, 0, len);
		}

		inBuf.close();
		outBuf.close();
	}

	public static boolean moveFile(File sourceLocation, File targetLocation) {
		// need to try both methods of moving the file as some devices (e.g., Acer Chromebook 14) fail
		// on renameTo even though both files are on the same storage media
		if (sourceLocation.renameTo(targetLocation)) {
			return true;
		}
		try {
			IOUtilities.copyFile(sourceLocation, targetLocation);
			sourceLocation.delete();
			return true;
		} catch (IOException ignored) {
		}
		return false;
	}

	public static boolean copyResource(Resources resources, int resourceID, File targetLocation) {
		Bitmap resourceImage = BitmapFactory.decodeResource(resources, resourceID);
		if (resourceImage != null) {
			return BitmapUtilities.saveBitmap(resourceImage, Bitmap.CompressFormat.PNG, 100, targetLocation);
		}
		return false;
	}

	public static byte[] readFileToByteArray(String file) throws IOException {
		return readFileToByteArray(new File(file));
	}

	public static byte[] readFileToByteArray(File file) throws IOException {
		try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
			// get and check length
			long longlength = f.length();
			int length = (int) longlength;
			if (length != longlength) {
				throw new IOException("File size >= 2 GB");
			}

			// read file and return data
			byte[] data = new byte[length];
			f.readFully(data);
			return data;
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
			} catch (Throwable ignored) {
			}
		}
		return false;
	}

	@Deprecated
	public static void setFullyPublic(File file) {
		file.setReadable(true, false);
		file.setWritable(true, false);
		file.setExecutable(true, false);
	}

	public static boolean externalStorageIsWritable() {
		return externalStorageIsAccessible(true);
	}

	public static boolean externalStorageIsReadable() {
		return externalStorageIsAccessible(false);
	}

	private static boolean externalStorageIsAccessible(boolean requireWritable) {
		final String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true; // available and writable
		} else if (!requireWritable && Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			return true; // available but read only
		} else {
			return false; // not available
		}
	}

	public static File getNewCachePath(Context context, String pathName, boolean preferExternal, boolean deleteExisting) {
		File cacheDir = null;
		if (preferExternal && externalStorageIsWritable()) {
			cacheDir = context.getExternalCacheDir();
		}
		if (cacheDir == null) {
			cacheDir = context.getCacheDir();
		}
		if (cacheDir == null) {
			return null; // sometimes getCacheDir returns null - perhaps when low on space?
		}
		File newCacheDir = new File(cacheDir, pathName);
		if (deleteExisting && newCacheDir.exists()) {
			deleteRecursive(newCacheDir);
		}
		if (!newCacheDir.exists()) {
			if (!newCacheDir.mkdirs()) {
				return null;
			}
		} else if (!newCacheDir.isDirectory()) {
			return null; // the directory exists as a file - could delete, but not worth the risk, so return error
		}
		createMediaScannerIgnoreFile(newCacheDir); // don't want our storage directory scanned
		return newCacheDir;
	}

	public static File getNewStoragePath(Context context, String pathName, boolean preferExternal) {
		File filesDir = null;
		if (preferExternal && externalStorageIsWritable()) {
			filesDir = context.getExternalFilesDir(null);
		}
		if (filesDir == null) {
			filesDir = context.getFilesDir();
		}
		if (filesDir == null) {
			return null; // sometimes getFilesDir returns null - perhaps when low on space?
		}
		File newFilesDir = new File(filesDir, pathName);
		if (!newFilesDir.exists()) {
			if (!newFilesDir.mkdirs()) {
				return null;
			}
		} else if (!newFilesDir.isDirectory()) {
			return null; // the directory exists as a file - could delete, but not worth the risk, so return error
		}
		createMediaScannerIgnoreFile(newFilesDir); // don't want our storage directory scanned
		return newFilesDir;
	}

	public static boolean isInstalledOnSdCard(Context context) {
		// see: http://stackoverflow.com/questions/4004650/
		PackageManager packageManager = context.getPackageManager();
		try {
			PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
			ApplicationInfo applicationInfo = packageInfo.applicationInfo;
			return (applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE;
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean isInternalPath(String filePath) {
		File dataDirectory = Environment.getDataDirectory();
		String dataDirectoryString;
		if (dataDirectory != null) { // can't trust anything on Android...
			dataDirectoryString = dataDirectory.getAbsolutePath();
		} else {
			dataDirectoryString = "/data";
		}
		return filePath.startsWith(dataDirectoryString);
	}

	public static boolean deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory()) {
			File[] fileList = fileOrDirectory.listFiles();
			if (fileList != null) {
				for (File child : fileList) {
					deleteRecursive(child);
				}
			}
		}
		return fileOrDirectory.delete();
	}

	public static boolean createMediaScannerIgnoreFile(File directory) {
		try {
			new File(directory, MediaStore.MEDIA_IGNORE_FILENAME).createNewFile(); // prevent media scanner
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static String removeExtension(String s) {
		String name = new File(s).getName();
		String extension = MimeTypeMap.getFileExtensionFromUrl(s);
		if (!TextUtils.isEmpty(extension)) {
			int extensionIndex = name.lastIndexOf(extension);
			if (extensionIndex > 0) {
				extensionIndex -= 1; // the dot character
			}
			return name.substring(0, extensionIndex);

		}
		return name;
	}

	// pass -1 to read the entire file
	public static String getFileContentSnippet(String filePath, int snippetLength) {
		final String spaceString = " ";
		Pattern replacementPattern = Pattern.compile("\\s+");
		StringBuilder fileString = new StringBuilder();
		String currentLine;
		FileInputStream fileStream = null;
		BufferedReader bufferedReader = null;
		try {
			fileStream = new FileInputStream(filePath);
			bufferedReader = new BufferedReader(new InputStreamReader(fileStream));
			while ((currentLine = bufferedReader.readLine()) != null && fileString.length() < snippetLength) {
				currentLine = replacementPattern.matcher(currentLine).replaceAll(spaceString).trim();
				if (currentLine.length() > 0) {
					fileString.append(currentLine);
					fileString.append(spaceString);
				}
			}
		} catch (Exception ignored) {
		} finally {
			IOUtilities.closeStream(bufferedReader);
			IOUtilities.closeStream(fileStream);
		}

		int stringLength = fileString.length();
		if (stringLength > snippetLength) {
			fileString.setLength(snippetLength);
		} else if (stringLength > 0) {
			fileString.setLength(stringLength - 1); // remove the last space we added
		}
		return fileString.toString();
	}

	public static String getFileContents(String filePath) {
		if (new File(filePath).exists()) {
			try {
				return getFileContents(new FileInputStream(filePath));
			} catch (FileNotFoundException ignored) {
			}
		}
		return "";
	}

	public static String getFileContents(InputStream stream) {
		StringBuilder fileString = new StringBuilder();
		BufferedReader bufferedReader = null;
		String currentLine;
		try {
			bufferedReader = new BufferedReader(new InputStreamReader(stream));
			while ((currentLine = bufferedReader.readLine()) != null) {
				fileString.append(currentLine);
				fileString.append("\n");
			}
		} catch (FileNotFoundException ignored) {
		} catch (IOException ignored) {
		} finally {
			IOUtilities.closeStream(bufferedReader);
			IOUtilities.closeStream(stream);
		}
		if (fileString.length() > 0) {
			fileString.deleteCharAt(fileString.length() - 1);
		}
		return fileString.toString();
	}

	public static String getFileExtension(String fileName) {
		return fileName == null ? null : fileName.substring(fileName.lastIndexOf('.') + 1);
	}

	public static boolean fileExtensionIs(String fileName, String extension) {
		return fileName != null && fileName.toLowerCase(Locale.ENGLISH).endsWith(extension.toLowerCase(Locale.ENGLISH));
	}

	public static File newDatedFileName(File baseDirectory, String fileExtension) {
		Calendar fileDate;
		StringBuilder newFileName = new StringBuilder();
		File newFile;
		boolean fileExists = false;
		do {
			fileDate = Calendar.getInstance();
			newFileName.setLength(0);
			newFileName.append(fileDate.get(Calendar.YEAR));
			newFileName.append('-');
			newFileName.append(String.format(Locale.US, "%02d", fileDate.get(Calendar.MONTH) + 1));
			newFileName.append('-');
			newFileName.append(String.format(Locale.US, "%02d", fileDate.get(Calendar.DAY_OF_MONTH)));
			newFileName.append('_');
			newFileName.append(String.format(Locale.US, "%02d", fileDate.get(Calendar.HOUR_OF_DAY)));
			newFileName.append('-');
			newFileName.append(String.format(Locale.US, "%02d", fileDate.get(Calendar.MINUTE)));
			newFileName.append('-');
			newFileName.append(String.format(Locale.US, "%02d", fileDate.get(Calendar.SECOND)));
			if (fileExists) {
				// add random chars to avoid collisions
				newFileName.append('_');
				newFileName.append(UUID.randomUUID().toString().substring(0, 4));
			}
			newFileName.append('.');
			newFileName.append(fileExtension);
			newFile = new File(baseDirectory, newFileName.toString());
			fileExists = newFile.exists();
		} while (fileExists);
		return newFile;
	}

	/**
	 * Get the duration of an audio file using MediaPlayer
	 *
	 * @return the file's duration, or -1 on error
	 */
	public static int getAudioFileLength(File audioFile) {
		MediaPlayer mediaPlayer = null;
		FileInputStream playerInputStream = null;
		int audioDuration = -1;
		try {
			// can't play from data directory (it's private; permissions don't work), must use input stream
			// mediaPlayer = MediaPlayer.create(activity, Uri.fromFile(audioFile));
			mediaPlayer = new MediaPlayer();
			playerInputStream = new FileInputStream(audioFile);
			mediaPlayer.setDataSource(playerInputStream.getFD());
			mediaPlayer.prepare();
			audioDuration = mediaPlayer.getDuration();
		} catch (Throwable ignored) {
		} finally {
			IOUtilities.closeStream(playerInputStream);
			if (mediaPlayer != null) {
				mediaPlayer.release();
			}
		}
		return audioDuration;
	}

	public static boolean zipFiles(String[] inputFilePaths, File zipFile) {
		ZipOutputStream outZip = null;
		try {
			outZip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
			byte[] buf = new byte[IO_BUFFER_SIZE];

			int i;
			BufferedInputStream inFile = null;
			for (String file : inputFilePaths) {
				try {
					inFile = new BufferedInputStream(new FileInputStream(file), IO_BUFFER_SIZE);
					outZip.putNextEntry(new ZipEntry(file.substring(file.lastIndexOf("/") + 1)));
					while ((i = inFile.read(buf, 0, IO_BUFFER_SIZE)) != -1) {
						outZip.write(buf, 0, i);
					}
					outZip.closeEntry();
				} finally {
					IOUtilities.closeStream(inFile);
				}
			}

		} catch (Exception e) {
			return false;
		} finally {
			IOUtilities.closeStream(outZip);
		}
		return true;
	}

	public static boolean unzipFiles(InputStream inputStream, File outputFolder) {
		ZipInputStream inZip = null;
		try {
			inZip = new ZipInputStream(new BufferedInputStream(inputStream));
			byte[] buf = new byte[IO_BUFFER_SIZE];

			ZipEntry currentEntry;
			while ((currentEntry = inZip.getNextEntry()) != null) {
				if (!currentEntry.isDirectory()) {
					BufferedOutputStream outStream = null;
					try {
						int i;
						File outFile = new File(outputFolder, currentEntry.getName());
						if (!outFile.getCanonicalPath().startsWith(outputFolder.getCanonicalPath())) {
							throw new SecurityException("Extracted zip file path does not match intended folder; aborting");
						}
						outStream = new BufferedOutputStream(new FileOutputStream(outFile));
						while ((i = inZip.read(buf)) != -1) {
							outStream.write(buf, 0, i);
						}
					} finally {
						IOUtilities.closeStream(outStream);
					}
				}
				inZip.closeEntry();
			}

		} catch (Exception e) {
			return false;
		} finally {
			IOUtilities.closeStream(inZip);
		}
		return true;
	}
}
