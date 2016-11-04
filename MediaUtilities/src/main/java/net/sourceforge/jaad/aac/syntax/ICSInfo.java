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
package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.Profile;
import net.sourceforge.jaad.aac.SampleFrequency;
import net.sourceforge.jaad.aac.tools.ICPrediction;
import net.sourceforge.jaad.aac.tools.LTPrediction;
import ac.robinson.util.AndroidUtilities;

public class ICSInfo implements Constants, ScaleFactorBands {

	public static final int WINDOW_SHAPE_SINE = 0;
	public static final int WINDOW_SHAPE_KAISER = 1;
	public static final int PREVIOUS = 0;
	public static final int CURRENT = 1;

	public static enum WindowSequence {

		ONLY_LONG_SEQUENCE, LONG_START_SEQUENCE, EIGHT_SHORT_SEQUENCE, LONG_STOP_SEQUENCE;

		public static WindowSequence forInt(int i) throws AACException {
			WindowSequence w;
			switch (i) {
				case 0:
					w = ONLY_LONG_SEQUENCE;
					break;
				case 1:
					w = LONG_START_SEQUENCE;
					break;
				case 2:
					w = EIGHT_SHORT_SEQUENCE;
					break;
				case 3:
					w = LONG_STOP_SEQUENCE;
					break;
				default:
					throw new AACException("unknown window sequence type");
			}
			return w;
		}
	}

	private final int frameLength;
	private WindowSequence windowSequence;
	private int[] windowShape;
	private int maxSFB;
	// prediction
	private boolean predictionDataPresent;
	private ICPrediction icPredict;
	boolean ltpData1Present, ltpData2Present;
	private LTPrediction ltPredict1, ltPredict2;
	// windows/sfbs
	private int windowCount;
	private int windowGroupCount;
	private int[] windowGroupLength;
	private int swbCount;
	private int[] swbOffsets;

	public ICSInfo(int frameLength) {
		this.frameLength = frameLength;
		windowShape = new int[2];
		windowSequence = WindowSequence.ONLY_LONG_SEQUENCE;
		windowGroupLength = new int[MAX_WINDOW_GROUP_COUNT];
		ltpData1Present = false;
		ltpData2Present = false;
	}

	/* ========== decoding ========== */
	public void decode(BitStream in, DecoderConfig conf, boolean commonWindow) throws AACException {
		final SampleFrequency sf = conf.getSampleFrequency();
		if (sf.equals(SampleFrequency.SAMPLE_FREQUENCY_NONE))
			throw new AACException("invalid sample frequency");

		in.skipBit(); // reserved
		windowSequence = WindowSequence.forInt(in.readBits(2));
		windowShape[PREVIOUS] = windowShape[CURRENT];
		windowShape[CURRENT] = in.readBit();

		windowGroupCount = 1;
		windowGroupLength[0] = 1;
		if (windowSequence.equals(WindowSequence.EIGHT_SHORT_SEQUENCE)) {
			maxSFB = in.readBits(4);
			int i;
			for (i = 0; i < 7; i++) {
				if (in.readBool())
					windowGroupLength[windowGroupCount - 1]++;
				else {
					windowGroupCount++;
					windowGroupLength[windowGroupCount - 1] = 1;
				}
			}
			windowCount = 8;
			swbOffsets = SWB_OFFSET_SHORT_WINDOW[sf.getIndex()];
			swbCount = SWB_SHORT_WINDOW_COUNT[sf.getIndex()];
			predictionDataPresent = false;
		} else {
			maxSFB = in.readBits(6);
			windowCount = 1;
			swbOffsets = SWB_OFFSET_LONG_WINDOW[sf.getIndex()];
			swbCount = SWB_LONG_WINDOW_COUNT[sf.getIndex()];
			predictionDataPresent = in.readBool();
			if (predictionDataPresent)
				readPredictionData(in, conf.getProfile(), sf, commonWindow);
		}
	}

	private void readPredictionData(BitStream in, Profile profile, SampleFrequency sf, boolean commonWindow)
			throws AACException {
		switch (profile) {
			case AAC_MAIN:
				if (icPredict == null)
					icPredict = new ICPrediction();
				icPredict.decode(in, maxSFB, sf);
				break;
			case AAC_LTP:
				if (ltpData1Present = in.readBool()) {
					if (ltPredict1 == null)
						ltPredict1 = new LTPrediction(frameLength);
					ltPredict1.decode(in, this, profile);
				}
				if (commonWindow) {
					if (ltpData2Present = in.readBool()) {
						if (ltPredict2 == null)
							ltPredict2 = new LTPrediction(frameLength);
						ltPredict2.decode(in, this, profile);
					}
				}
				break;
			case ER_AAC_LTP:
				if (!commonWindow) {
					if (ltpData1Present = in.readBool()) {
						if (ltPredict1 == null)
							ltPredict1 = new LTPrediction(frameLength);
						ltPredict1.decode(in, this, profile);
					}
				}
				break;
			default:
				throw new AACException("unexpected profile for LTP: " + profile);
		}
	}

	/* =========== gets ============ */
	public int getMaxSFB() {
		return maxSFB;
	}

	public int getSWBCount() {
		return swbCount;
	}

	public int[] getSWBOffsets() {
		return swbOffsets;
	}

	public int getSWBOffsetMax() {
		return swbOffsets[swbCount];
	}

	public int getWindowCount() {
		return windowCount;
	}

	public int getWindowGroupCount() {
		return windowGroupCount;
	}

	public int getWindowGroupLength(int g) {
		return windowGroupLength[g];
	}

	public WindowSequence getWindowSequence() {
		return windowSequence;
	}

	public boolean isEightShortFrame() {
		return windowSequence.equals(WindowSequence.EIGHT_SHORT_SEQUENCE);
	}

	public int getWindowShape(int index) {
		return windowShape[index];
	}

	public boolean isICPredictionPresent() {
		return predictionDataPresent;
	}

	public ICPrediction getICPrediction() {
		return icPredict;
	}

	public boolean isLTPrediction1Present() {
		return ltpData1Present;
	}

	public LTPrediction getLTPrediction1() {
		return ltPredict1;
	}

	public boolean isLTPrediction2Present() {
		return ltpData2Present;
	}

	public LTPrediction getLTPrediction2() {
		return ltPredict2;
	}

	public void unsetPredictionSFB(int sfb) {
		if (predictionDataPresent)
			icPredict.setPredictionUnused(sfb);
		if (ltpData1Present)
			ltPredict1.setPredictionUnused(sfb);
		if (ltpData2Present)
			ltPredict2.setPredictionUnused(sfb);
	}

	public void setData(ICSInfo info) {
		windowSequence = WindowSequence.valueOf(info.windowSequence.name());
		windowShape[PREVIOUS] = windowShape[CURRENT];
		windowShape[CURRENT] = info.windowShape[CURRENT];
		maxSFB = info.maxSFB;
		predictionDataPresent = info.predictionDataPresent;
		if (predictionDataPresent)
			icPredict = info.icPredict;
		ltpData1Present = info.ltpData1Present;
		if (ltpData1Present) {
			ltPredict1.copy(info.ltPredict1);
			ltPredict2.copy(info.ltPredict2);
		}
		windowCount = info.windowCount;
		windowGroupCount = info.windowGroupCount;
		windowGroupLength = AndroidUtilities.arrayCopyOf(info.windowGroupLength, info.windowGroupLength.length);
		swbCount = info.swbCount;
		swbOffsets = AndroidUtilities.arrayCopyOf(info.swbOffsets, info.swbOffsets.length);
	}
}
