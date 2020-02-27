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

public class AndroidUtilities {

	/**
	 * Check if an array contains the item given - could use Arrays.binarySearch, but that requires a sorted array
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
}
