/*
 *  Copyright (C) 2011 in-somnia
 * 
 *  This file is part of JAAD.
 * 
 *  JAAD is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU Lesser General Public License as 
 *  published by the Free Software Foundation; either version 3 of the 
 *  License, or (at your option) any later version.
 *
 *  JAAD is distributed in the hope that it will be useful, but WITHOUT 
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General 
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge.jaad.aac.filterbank;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.syntax.Constants;
import net.sourceforge.jaad.aac.syntax.ICSInfo.WindowSequence;

public class FilterBank implements Constants, SineWindows, KBDWindows {

	private final float[][] LONG_WINDOWS;// = {SINE_LONG, KBD_LONG};
	private final float[][] SHORT_WINDOWS;// = {SINE_SHORT, KBD_SHORT};
	private final int length, shortLen, mid, trans;
	private final MDCT mdctShort, mdctLong;
	private final float[] buf;
	private final float[][] overlaps;

	public FilterBank(boolean smallFrames, int channels) throws AACException {
		if (smallFrames) {
			length = WINDOW_SMALL_LEN_LONG;
			shortLen = WINDOW_SMALL_LEN_SHORT;
			LONG_WINDOWS = new float[][] { SINE_960, KBD_960 };
			SHORT_WINDOWS = new float[][] { SINE_120, KBD_120 };
		} else {
			length = WINDOW_LEN_LONG;
			shortLen = WINDOW_LEN_SHORT;
			LONG_WINDOWS = new float[][] { SINE_1024, KBD_1024 };
			SHORT_WINDOWS = new float[][] { SINE_128, KBD_128 };
		}
		mid = (length - shortLen) / 2;
		trans = shortLen / 2;

		mdctShort = new MDCT(shortLen * 2);
		mdctLong = new MDCT(length * 2);

		overlaps = new float[channels][length];
		buf = new float[2 * length];
	}

	public void process(WindowSequence windowSequence, int windowShape, int windowShapePrev, float[] in, float[] out,
			int channel) {
		int i;
		float[] overlap = overlaps[channel];
		switch (windowSequence) {
			case ONLY_LONG_SEQUENCE:
				mdctLong.process(in, 0, buf, 0);
				// add second half output of previous frame to windowed output of current frame
				for (i = 0; i < length; i++) {
					out[i] = overlap[i] + (buf[i] * LONG_WINDOWS[windowShapePrev][i]);
				}

				// window the second half and save as overlap for next frame
				for (i = 0; i < length; i++) {
					overlap[i] = buf[length + i] * LONG_WINDOWS[windowShape][length - 1 - i];
				}
				break;
			case LONG_START_SEQUENCE:
				mdctLong.process(in, 0, buf, 0);
				// add second half output of previous frame to windowed output of current frame
				for (i = 0; i < length; i++) {
					out[i] = overlap[i] + (buf[i] * LONG_WINDOWS[windowShapePrev][i]);
				}

				// window the second half and save as overlap for next frame
				for (i = 0; i < mid; i++) {
					overlap[i] = buf[length + i];
				}
				for (i = 0; i < shortLen; i++) {
					overlap[mid + i] = buf[length + mid + i] * SHORT_WINDOWS[windowShape][shortLen - i - 1];
				}
				for (i = 0; i < mid; i++) {
					overlap[mid + shortLen + i] = 0;
				}
				break;
			case EIGHT_SHORT_SEQUENCE:
				for (i = 0; i < 8; i++) {
					mdctShort.process(in, i * shortLen, buf, 2 * i * shortLen);
				}

				// add second half output of previous frame to windowed output of current frame
				for (i = 0; i < mid; i++) {
					out[i] = overlap[i];
				}
				for (i = 0; i < shortLen; i++) {
					out[mid + i] = overlap[mid + i] + (buf[i] * SHORT_WINDOWS[windowShapePrev][i]);
					out[mid + 1 * shortLen + i] = overlap[mid + shortLen * 1 + i]
							+ (buf[shortLen * 1 + i] * SHORT_WINDOWS[windowShape][shortLen - 1 - i])
							+ (buf[shortLen * 2 + i] * SHORT_WINDOWS[windowShape][i]);
					out[mid + 2 * shortLen + i] = overlap[mid + shortLen * 2 + i]
							+ (buf[shortLen * 3 + i] * SHORT_WINDOWS[windowShape][shortLen - 1 - i])
							+ (buf[shortLen * 4 + i] * SHORT_WINDOWS[windowShape][i]);
					out[mid + 3 * shortLen + i] = overlap[mid + shortLen * 3 + i]
							+ (buf[shortLen * 5 + i] * SHORT_WINDOWS[windowShape][shortLen - 1 - i])
							+ (buf[shortLen * 6 + i] * SHORT_WINDOWS[windowShape][i]);
					if (i < trans)
						out[mid + 4 * shortLen + i] = overlap[mid + shortLen * 4 + i]
								+ (buf[shortLen * 7 + i] * SHORT_WINDOWS[windowShape][shortLen - 1 - i])
								+ (buf[shortLen * 8 + i] * SHORT_WINDOWS[windowShape][i]);
				}

				// window the second half and save as overlap for next frame
				for (i = 0; i < shortLen; i++) {
					if (i >= trans)
						overlap[mid + 4 * shortLen + i - length] = (buf[shortLen * 7 + i] * SHORT_WINDOWS[windowShape][shortLen
								- 1 - i])
								+ (buf[shortLen * 8 + i] * SHORT_WINDOWS[windowShape][i]);
					overlap[mid + 5 * shortLen + i - length] = (buf[shortLen * 9 + i] * SHORT_WINDOWS[windowShape][shortLen
							- 1 - i])
							+ (buf[shortLen * 10 + i] * SHORT_WINDOWS[windowShape][i]);
					overlap[mid + 6 * shortLen + i - length] = (buf[shortLen * 11 + i] * SHORT_WINDOWS[windowShape][shortLen
							- 1 - i])
							+ (buf[shortLen * 12 + i] * SHORT_WINDOWS[windowShape][i]);
					overlap[mid + 7 * shortLen + i - length] = (buf[shortLen * 13 + i] * SHORT_WINDOWS[windowShape][shortLen
							- 1 - i])
							+ (buf[shortLen * 14 + i] * SHORT_WINDOWS[windowShape][i]);
					overlap[mid + 8 * shortLen + i - length] = (buf[shortLen * 15 + i] * SHORT_WINDOWS[windowShape][shortLen
							- 1 - i]);
				}
				for (i = 0; i < mid; i++) {
					overlap[mid + shortLen + i] = 0;
				}
				break;
			case LONG_STOP_SEQUENCE:
				mdctLong.process(in, 0, buf, 0);
				// add second half output of previous frame to windowed output of current frame
				// construct first half window using padding with 1's and 0's
				for (i = 0; i < mid; i++) {
					out[i] = overlap[i];
				}
				for (i = 0; i < shortLen; i++) {
					out[mid + i] = overlap[mid + i] + (buf[mid + i] * SHORT_WINDOWS[windowShapePrev][i]);
				}
				for (i = 0; i < mid; i++) {
					out[mid + shortLen + i] = overlap[mid + shortLen + i] + buf[mid + shortLen + i];
				}
				// window the second half and save as overlap for next frame
				for (i = 0; i < length; i++) {
					overlap[i] = buf[length + i] * LONG_WINDOWS[windowShape][length - 1 - i];
				}
				break;
		}
	}

	// only for LTP: no overlapping, no short blocks
	public void processLTP(WindowSequence windowSequence, int windowShape, int windowShapePrev, float[] in, float[] out) {
		int i;

		switch (windowSequence) {
			case ONLY_LONG_SEQUENCE:
				for (i = length - 1; i >= 0; i--) {
					buf[i] = in[i] * LONG_WINDOWS[windowShapePrev][i];
					buf[i + length] = in[i + length] * LONG_WINDOWS[windowShape][length - 1 - i];
				}
				break;

			case LONG_START_SEQUENCE:
				for (i = 0; i < length; i++) {
					buf[i] = in[i] * LONG_WINDOWS[windowShapePrev][i];
				}
				for (i = 0; i < mid; i++) {
					buf[i + length] = in[i + length];
				}
				for (i = 0; i < shortLen; i++) {
					buf[i + length + mid] = in[i + length + mid] * SHORT_WINDOWS[windowShape][shortLen - 1 - i];
				}
				for (i = 0; i < mid; i++) {
					buf[i + length + mid + shortLen] = 0;
				}
				break;

			case LONG_STOP_SEQUENCE:
				for (i = 0; i < mid; i++) {
					buf[i] = 0;
				}
				for (i = 0; i < shortLen; i++) {
					buf[i + mid] = in[i + mid] * SHORT_WINDOWS[windowShapePrev][i];
				}
				for (i = 0; i < mid; i++) {
					buf[i + mid + shortLen] = in[i + mid + shortLen];
				}
				for (i = 0; i < length; i++) {
					buf[i + length] = in[i + length] * LONG_WINDOWS[windowShape][length - 1 - i];
				}
				break;
		}
		mdctLong.processForward(buf, out);
	}

	public float[] getOverlap(int channel) {
		return overlaps[channel];
	}
}
