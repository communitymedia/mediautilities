/*
 * @(#)CombinedAudioInputStream.java
 *
 * $Date: 2012-03-14 17:27:16 +0000 (Wed, 14 Mar 2012) $
 *
 * Copyright (c) 2012 by Jeremy Wood.
 * All rights reserved.
 *
 * The copyright of this software is owned by Jeremy Wood. 
 * You may not use, copy or modify this software, except in  
 * accordance with the license agreement you entered into with  
 * Jeremy Wood. For details see accompanying license terms.
 * 
 * This software is probably, but not necessarily, discussed here:
 * http://javagraphics.java.net/
 * 
 * That site should also contain the most recent official version
 * of this software.  (See the SVN repository for more details.)
 */
package com.bric.audio;

import java.io.InputStream;
import java.util.List;
import java.util.Vector;

import com.bric.io.CombinedInputStream;

public class CombinedAudioInputStream extends AudioInputStream {
	List<AudioInputStream> streams = new Vector<AudioInputStream>();

	public CombinedAudioInputStream(AudioInputStream in1, AudioInputStream in2) {
		this(new AudioInputStream[] { in1, in2 });
	}

	public CombinedAudioInputStream(AudioInputStream[] inputs) {
		super(createInputStream(inputs), inputs[0].getFormat(), getFrameCount(inputs));

		for (int a = 1; a < inputs.length; a++) {
			if (!equals(inputs[0].getFormat(), inputs[a].getFormat())) {
				throw new IllegalArgumentException("inputs[0] = " + inputs[0].getFormat() + ", inputs[" + a + "] = "
						+ inputs[a].getFormat());
			}
		}
	}

	private boolean equals(AudioFormat format1, AudioFormat format2) {
		if (format1.getChannels() != format2.getChannels())
			return false;
		if (format1.isBigEndian() != format2.isBigEndian())
			return false;
		if (format1.getSampleRate() != format2.getSampleRate())
			return false;
		if (format1.getSampleSizeInBits() != format2.getSampleSizeInBits())
			return false;
		if (!format1.getEncoding().equals(format2.getEncoding()))
			return false;

		return true;
	}

	private static InputStream createInputStream(AudioInputStream[] audioIns) {
		boolean[] b = new boolean[audioIns.length];
		for (int a = 0; a < audioIns.length; a++) {
			b[a] = true;
		}
		return new CombinedInputStream(audioIns, b);
	}

	private static long getFrameCount(AudioInputStream[] audioIn) {
		long sum = 0;
		for (int a = 0; a < audioIn.length; a++) {
			sum += audioIn[a].getFrameLength();
		}
		return sum;
	}
}
