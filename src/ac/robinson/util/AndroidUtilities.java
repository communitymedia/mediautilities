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

import java.util.Arrays;

import android.annotation.TargetApi;
import android.os.Build;

public class AndroidUtilities {

	/**
	 * Check if an array contains the item given - could use Arrays.binarySearch, but that requires a sorted array
	 * 
	 * @param array
	 * @param item
	 * @return
	 */
	public static <T> boolean arrayContains(T[] array, T item) {
		if (item == null) {
			return false;
		}
		for (T i : array) {
			if (item.equals(i)) {
				return true;
			}
		}
		return false;
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static int[] arrayCopyOf(int[] array, int length) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return Arrays.copyOf(array, length);
		} else {
			int[] newArray = new int[length];
			System.arraycopy(array, 0, newArray, 0, length);
			return newArray;
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static boolean[] arrayCopyOf(boolean[] array, int length) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return Arrays.copyOf(array, length);
		} else {
			boolean[] newArray = new boolean[length];
			System.arraycopy(array, 0, newArray, 0, length);
			return newArray;
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static byte[] arrayCopyOf(byte[] array, int length) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return Arrays.copyOf(array, length);
		} else {
			byte[] newArray = new byte[length];
			System.arraycopy(array, 0, newArray, 0, length);
			return newArray;
		}
	}
}
