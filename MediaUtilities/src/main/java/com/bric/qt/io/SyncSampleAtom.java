package com.bric.qt.io;

import com.bric.io.GuardedOutputStream;

import java.io.IOException;

import androidx.annotation.NonNull;

// Format documentation says this atom is automatically implied if not present, but VLC does not seem to respect this
public class SyncSampleAtom extends LeafAtom {
	int version = 0;
	int flags = 0;

	long[] sampleTable = new long[0];

	public SyncSampleAtom(int version, int flags) {
		super(null);
		this.version = version;
		this.flags = flags;
	}

	public SyncSampleAtom() {
		super(null);
	}

	public void addSample(long sample) {
		long[] newArray = new long[sampleTable.length + 1];
		System.arraycopy(sampleTable, 0, newArray, 0, sampleTable.length);
		newArray[newArray.length - 1] = sample;
		sampleTable = newArray;
	}

	@Override
	protected String getIdentifier() {
		return "stss";
	}

	@Override
	protected long getSize() {
		return 16 + sampleTable.length * 4;
	}

	@Override
	protected void writeContents(GuardedOutputStream out) throws IOException {
		out.write(version);
		write24Int(out, flags);
		write32Int(out, sampleTable.length);
		long sampleNumber = 1; // the list starts at 1
		for (int a = 0; a < sampleTable.length; a++) {
			write32Int(out, sampleNumber);
			sampleNumber += sampleTable[a];
		}
	}

	@NonNull
	@Override
	public String toString() {
		String entriesString;
		if (sampleTable.length > 50 && ABBREVIATE) {
			entriesString = "[ ... ]";
		} else {
			StringBuffer sb = new StringBuffer();
			sb.append("[ ");
			for (int a = 1; a < sampleTable.length; a++) {
				if (a != 1) {
					sb.append(", ");
				}
				sb.append(sampleTable[a]);
			}
			sb.append(" ]");
			entriesString = sb.toString();
		}

		return "SyncSampleAtom[ version=" + version + ", " + "flags=" + flags + ", " + "sampleTable=" + entriesString +
				"]";
	}
}

