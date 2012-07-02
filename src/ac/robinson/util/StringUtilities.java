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

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class StringUtilities {

	private static final String Utf8 = "utf-8";
	private static final String Sha1 = "sha-1";
	
	/**
	 * Use TextUtils.isEmpty() unless trim() is required
	 */
	@Deprecated
	public static boolean stringContentCheck(String s) {
		if (s != null) {
			return (s.trim().length() > 0); // note: was also !s.equals("null") && for SMIL import; now changed
		}
		return false;
	}

	public static int safeStringToInteger(String s) {
		if (stringContentCheck(s)) {
			try {
				return Integer.parseInt(s);
			} catch (NumberFormatException e) {
			}
		}
		return 0;
	}

	public static String millisecondsToTimeString(long milliseconds, boolean includeMilliseconds) {
		// overestimating is better than just rounding
		int secondsIn = (int) Math.ceil(milliseconds / 1000);
		int millisecondsIn = ((int) milliseconds - (secondsIn * 1000));

		int hours = secondsIn / 3600;
		int remainder = secondsIn % 3600;
		int minutes = remainder / 60;
		int seconds = remainder % 60;

		return (hours > 0 ? hours + ":" : "") + String.format("%02d", minutes) + ":" + String.format("%02d", seconds)
				+ (includeMilliseconds ? "." + String.format("%03d", millisecondsIn) : "");
	}
	
	public static String sha1Hash(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance(Sha1);
			md.update(input.getBytes(Utf8));

			byte[] digest = md.digest();
			BigInteger bi = new BigInteger(1, digest);
			return String.format((Locale) null, "%0" + (digest.length * 2) + "x", bi).toLowerCase();
		} catch (NoSuchAlgorithmException e) {
		} catch (UnsupportedEncodingException e) {
		}
		return input;
	}
}
