/*
 * @(#)EditListAtom.java
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
package com.bric.qt.io;

import com.bric.io.GuardedOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class EditListAtom extends LeafAtom {

	static class EditListTableEntry {
		/**
		 * A 32-bit integer that specifies the duration of this edit segment in units of the movie's time scale.
		 */
		long trackDuration;

		/**
		 * A 32-bit integer containing the starting time within the media of this edit segment (in media timescale
		 * units). If this field is set to -1, it is an empty edit. The last edit in a track should never be an empty
		 * edit. Any difference between the movie's duration and the track's duration is expressed as an implicit empty
		 * edit.
		 */
		long mediaTime;

		/**
		 * A 32-bit fixed-point number that specifies the relative rate at which to play the media corresponding to this
		 * edit segment. This rate value cannot be 0 or negative.
		 */
		float mediaRate;

		EditListTableEntry(InputStream in) throws IOException {
			trackDuration = Atom.read32Int(in);
			mediaTime = Atom.read32Int(in);
			mediaRate = Atom.read16_16Float(in);
		}

		EditListTableEntry(long trackDuration, long mediaTime, float mediaRate) {
			this.trackDuration = trackDuration;
			this.mediaTime = mediaTime;
			this.mediaRate = mediaRate;
		}

		void write(OutputStream out) throws IOException {
			Atom.write32Int(out, trackDuration);
			Atom.write32Int(out, mediaTime);
			Atom.write16_16Float(out, mediaRate);
		}

		@Override
		public String toString() {
			return "EditListTableEntry[ trackDuration=" + trackDuration + ", mediaTime=" + mediaTime + ", mediaRate="
					+ mediaRate + "]";
		}
	}

	/** A 1-byte specification of the version of this edit list atom. */
	int version = 0;
	/** Three bytes of space for flags. Set this field to 0. */
	int flags = 0;

	EditListTableEntry[] table = new EditListTableEntry[] {};

	protected EditListAtom(Atom parent, InputStream in) throws IOException {
		super(parent);
		version = Atom.read8Int(in);
		flags = Atom.read24Int(in);

		/**
		 * A 32-bit integer that specifies the number of entries in the edit list atom that follows.
		 */
		int numberOfEntries = (int) Atom.read32Int(in);

		table = new EditListTableEntry[numberOfEntries];
		for (int a = 0; a < numberOfEntries; a++) {
			table[a] = new EditListTableEntry(in);
		}
	}

	public EditListAtom() {
		super(null);
	}

	public void addEditListTableEntry(long trackDuration, long mediaTime, float mediaRate) {
		EditListTableEntry[] newTable = new EditListTableEntry[table.length + 1];
		System.arraycopy(table, 0, newTable, 0, table.length);
		newTable[newTable.length - 1] = new EditListTableEntry(trackDuration, mediaTime, mediaRate);
		table = newTable;

	}

	@Override
	protected long getSize() {
		return 16 + 12 * table.length;
	}

	@Override
	protected String getIdentifier() {
		return "elst";
	}

	@Override
	protected void writeContents(GuardedOutputStream out) throws IOException {
		Atom.write8Int(out, version);
		Atom.write24Int(out, flags);
		Atom.write32Int(out, table.length);
		for (int a = 0; a < table.length; a++) {
			table[a].write(out);
		}
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer("EditListAtom[ version=" + version + ", flags=" + flags + ", data=[");
		for (int a = 0; a < table.length; a++) {
			sb.append(table[a] + " ");
		}
		sb.append("]]");
		return sb.toString();
	}
}
