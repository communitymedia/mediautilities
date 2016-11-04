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
package net.sourceforge.jaad.mp4.od;

import java.io.IOException;
import net.sourceforge.jaad.mp4.MP4InputStream;

/**
 * The ESDescriptor conveys all information related to a particular elementary stream and has three major parts:
 * 
 * The first part consists of the ES_ID which is a unique reference to the elementary stream within its name scope, a
 * mechanism to group elementary streams within this Descriptor and an optional URL String.
 * 
 * The second part is a set of optional extension descriptors that support the inclusion of future extensions as well as
 * the transport of private data in a backward compatible way.
 * 
 * The third part consists of the DecoderConfigDescriptor, SLConfigDescriptor, IPIDescriptor and QoSDescriptor which
 * convey the parameters and requirements of the elementary stream.
 * 
 * @author in-somnia
 */
public class ESDescriptor extends Descriptor {

	private int esID, streamPriority, dependingOnES_ID;
	@SuppressWarnings("unused")
	private boolean streamDependency, urlPresent, ocrPresent;
	private String url;

	void decode(MP4InputStream in) throws IOException {
		esID = (int) in.readBytes(2);

		// 1 bit stream dependence flag, 1 it url flag, 1 reserved, 5 bits stream priority
		final int flags = in.read();
		streamDependency = ((flags >> 7) & 1) == 1;
		urlPresent = ((flags >> 6) & 1) == 1;
		streamPriority = flags & 31;

		if (streamDependency)
			dependingOnES_ID = (int) in.readBytes(2);
		else
			dependingOnES_ID = -1;

		if (urlPresent) {
			final int len = in.read();
			url = in.readString(len);
		}

		readChildren(in);
	}

	/**
	 * The ES_ID provides a unique label for each elementary stream within its name scope. The value should be within 0
	 * and 65535 exclusively. The values 0 and 65535 are reserved.
	 * 
	 * @return the elementary stream's ID
	 */
	public int getES_ID() {
		return esID;
	}

	/**
	 * Indicates if an ID of another stream is present, on which this stream depends.
	 * 
	 * @return true if the dependingOnES_ID is present
	 */
	public boolean hasStreamDependency() {
		return streamDependency;
	}

	/**
	 * The <code>dependingOnES_ID</code> is the ES_ID of another elementary stream on which this elementary stream
	 * depends. The stream with the <code>dependingOnES_ID</code> shall also be associated to this Descriptor. If no
	 * value is present (if <code>hasStreamDependency()</code> returns false) this method returns -1.
	 * 
	 * @return the dependingOnES_ID value, or -1 if none is present
	 */
	public int getDependingOnES_ID() {
		return dependingOnES_ID;
	}

	/**
	 * A flag that indicates the presence of a URL.
	 * 
	 * @return true if a URL is present
	 */
	public boolean isURLPresent() {
		return urlPresent;
	}

	/**
	 * A URL String that shall point to the location of an SL-packetized stream by name. The parameters of the
	 * SL-packetized stream that is retrieved from the URL are fully specified in this ESDescriptor. If no URL is
	 * present (if <code>isURLPresent()</code> returns false) this method returns null.
	 * 
	 * @return a URL String or null if none is present
	 */
	public String getURL() {
		return url;
	}

	/**
	 * The stream priority indicates a relative measure for the priority of this elementary stream. An elementary stream
	 * with a higher priority is more important than one with a lower priority. The absolute values are not normatively
	 * defined.
	 * 
	 * @return the stream priority
	 */
	public int getStreamPriority() {
		return streamPriority;
	}
}
