/*
 * Copyright (C) 2008 Romain Guy
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

package ac.robinson.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;

import ac.robinson.view.FastBitmapDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class ImageCacheUtilities {

	public static final FastBitmapDrawable NULL_DRAWABLE = new FastBitmapDrawable(null);
	public static final FastBitmapDrawable LOADING_DRAWABLE = new FastBitmapDrawable(null);

	// TODO: Use a concurrent HashMap to support multiple threads
	private static final HashMap<String, SoftReference<FastBitmapDrawable>> sArtCache = new HashMap<String, SoftReference<FastBitmapDrawable>>();

	// TODO: use these for most/all bitmap operations
	public static final BitmapFactory.Options mBitmapFactoryOptions;

	static {
		mBitmapFactoryOptions = new BitmapFactory.Options();
		mBitmapFactoryOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
	}

	private ImageCacheUtilities() {
	}

	public static boolean addIconToCache(File cacheDirectory, String cacheId, Bitmap bitmap,
			Bitmap.CompressFormat cacheType, int cacheQuality) {
		if (bitmap == null) {
			return false;
		}

		if (!cacheDirectory.exists()) {
			cacheDirectory.mkdirs();
			if (!cacheDirectory.exists()) {
				return false;
			}
			IOUtilities.createMediaScannerIgnoreFile(cacheDirectory);
		}

		File iconFile = new File(cacheDirectory, cacheId);
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(iconFile);
			bitmap.compress(cacheType, cacheQuality, out);
			deleteCachedIcon(cacheId);
		} catch (FileNotFoundException e) {
			return false;
		} finally {
			IOUtilities.closeStream(out);
		}

		return true;
	}

	/**
	 * Deletes the specified drawable from the cache. Calling this method will remove the drawable from the in-memory
	 * cache
	 * 
	 * @param id The id of the drawable to delete from the cache
	 */
	public static void deleteCachedIcon(String id) {
		sArtCache.remove(id);
	}

	public static void setLoadingIcon(String id) {
		sArtCache.remove(id);
		sArtCache.put(id, new SoftReference<FastBitmapDrawable>(LOADING_DRAWABLE));
	}

	/**
	 * Retrieves a drawable from the cache, identified by the specified id. If the drawable does not exist in the cache,
	 * it is loaded and added to the cache. If the drawable cannot be added to the cache, the specified default drawable
	 * is returned.
	 * 
	 * @param id The id of the drawable to retrieve
	 * @param defaultIcon The default drawable returned if no drawable can be found that matches the id
	 * 
	 * @return The drawable identified by id or defaultIcon
	 */
	public static FastBitmapDrawable getCachedIcon(File cacheDirectory, String id, FastBitmapDrawable defaultIcon) {
		FastBitmapDrawable drawable = null;

		SoftReference<FastBitmapDrawable> reference = sArtCache.get(id);
		if (reference != null) {
			drawable = reference.get();
		}

		if (drawable == null) {
			final Bitmap bitmap = loadIcon(cacheDirectory, id);
			if (bitmap != null) {
				drawable = new FastBitmapDrawable(bitmap);
			} else {
				drawable = NULL_DRAWABLE;
			}

			sArtCache.put(id, new SoftReference<FastBitmapDrawable>(drawable));
		}

		return drawable == NULL_DRAWABLE ? defaultIcon : drawable;
	}

	/**
	 * Removes all the callbacks from the drawables stored in the memory cache. This method must be called from the
	 * onDestroy() method of any activity using the cached drawables. Failure to do so will result in the entire
	 * activity being leaked.
	 */
	public static void cleanupCache() {
		for (SoftReference<FastBitmapDrawable> reference : sArtCache.values()) {
			final FastBitmapDrawable drawable = reference.get();
			if (drawable != null)
				drawable.setCallback(null);
		}
	}

	private static Bitmap loadIcon(File cacheDirectory, String id) {
		final File file = new File(cacheDirectory, id);
		if (file.exists()) {
			InputStream stream = null;
			try {
				stream = new FileInputStream(file);
				return BitmapFactory.decodeStream(stream, null, mBitmapFactoryOptions);
			} catch (FileNotFoundException e) {
				// Ignore
			} finally {
				IOUtilities.closeStream(stream);
			}
		}
		return null;
	}
}
