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
package net.sourceforge.jaad.mp4.boxes.impl.meta;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import net.sourceforge.jaad.mp4.MP4InputStream;
import net.sourceforge.jaad.mp4.boxes.FullBox;
import ac.robinson.util.AndroidUtilities;
import ac.robinson.util.StringUtilities;

/**
 * This box contains the data for a metadata tag. It is right below an iTunes metadata box (e.g. '@nam') or a custom
 * meta tag box ('----'). A custom meta tag box also contains a 'name'-box declaring the tag's name.
 * 
 * @author in-somnia
 */
/*
 * TODO: use generics here? -> each DataType should return <T> corresponding to its class (String/Integer/...)
 */
public class ITunesMetadataBox extends FullBox {

	private static final String[] TIMESTAMPS = { "yyyy", "yyyy-MM", "yyyy-MM-dd" };

	public enum DataType {

		IMPLICIT(/*Object.class*/),
		UTF8(/*String.class*/),
		UTF16(/*String.class*/),
		HTML(/*String.class*/),
		XML(/*String.class*/),
		UUID(/*Long.class*/),
		ISRC(/*String.class*/),
		MI3P(/*String.class*/),
		GIF(/*byte[].class*/),
		JPEG(/*byte[].class*/),
		PNG(/*byte[].class*/),
		URL(/*String.class*/),
		DURATION(/*Long.class*/),
		DATETIME(/*Long.class*/),
		GENRE(/*Integer.class*/),
		INTEGER(/*Long.class*/),
		RIAA(/*Integer.class*/),
		UPC(/*String.class*/),
		BMP(/*byte[].class*/),
		UNDEFINED(/*byte[].class*/);
		
		private static final DataType[] TYPES = { IMPLICIT, UTF8, UTF16, null, null, null, HTML, XML, UUID, ISRC, MI3P,
				null, GIF, JPEG, PNG, URL, DURATION, DATETIME, GENRE, null, null, INTEGER, null, null, RIAA, UPC, null,
				BMP };

		private DataType() {
		}

		private static DataType forInt(int i) {
			DataType type = null;
			if (i >= 0 && i < TYPES.length)
				type = TYPES[i];

			if (type == null)
				type = UNDEFINED;
			return type;
		}
	}

	private DataType dataType;
	private byte[] data;

	public ITunesMetadataBox() {
		super("iTunes Metadata Box");
	}

	@Override
	public void decode(MP4InputStream in) throws IOException {
		super.decode(in);

		dataType = DataType.forInt(flags);

		in.skipBytes(4); // padding?

		data = new byte[(int) getLeft(in)];
		in.readBytes(data);
	}

	public DataType getDataType() {
		return dataType;
	}

	/**
	 * Returns an unmodifiable array with the raw content, that can be present in different formats.
	 * 
	 * @return the raw metadata
	 */
	public byte[] getData() {
		return AndroidUtilities.arrayCopyOf(data, data.length);
	}

	/**
	 * Returns the content as a text string.
	 * 
	 * @return the metadata as text
	 */
	public String getText() {
		// first four bytes are padding (zero)
		return StringUtilities.byteToString(data, 0, data.length, Charset.forName("UTF-8"));
	}

	/**
	 * Returns the content as an unsigned 8-bit integer.
	 * 
	 * @return the metadata as an integer
	 */
	public long getNumber() {
		// first four bytes are padding (zero)
		long l = 0;
		for (int i = 0; i < data.length; i++) {
			l <<= 8;
			l |= (data[i] & 0xFF);
		}
		return l;
	}

	public int getInteger() {
		return (int) getNumber();
	}

	/**
	 * Returns the content as a boolean (flag) value.
	 * 
	 * @return the metadata as a boolean
	 */
	public boolean getBoolean() {
		return getNumber() != 0;
	}

	public Date getDate() {
		// timestamp lengths: 4,7,9
		final int i = (int) Math.floor(data.length / 3) - 1;
		final Date date;
		if (i >= 0 && i < TIMESTAMPS.length) {
			final SimpleDateFormat sdf = new SimpleDateFormat(TIMESTAMPS[i], Locale.US);
			date = sdf.parse(new String(data), new ParsePosition(0));
		} else
			date = null;
		return date;
	}
}
