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

package ac.robinson.mov;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import ac.robinson.util.IOUtilities;

public final class WAVtoPCMConverter {

	public static class WAVConfiguration {
		public int sampleFrequency = 0;
		public int sampleSize = 0;
		public int numberOfChannels = 0;

		@Override
		public String toString() {
			return this.getClass().getName() + "[" + sampleFrequency + "," + sampleSize + "," + numberOfChannels + "]";
		}
	}

	/**
	 * Convert a WAV input file to PCM
	 * 
	 * @param input the input file to convert
	 * @param output the output stream to write to
	 * @throws IOException
	 */
	// @SuppressWarnings("resource") is to suppress complaint about stream closure (handled by closeStream in finally)
	@SuppressWarnings("resource")
	public static void convertFile(File input, OutputStream output, WAVConfiguration config) throws IOException {
		int fileSize = (int) input.length();
		if (fileSize < 128) {
			throw new java.io.IOException("File too small to parse");
		}

		FileInputStream inputWAVStream = null;
		try {
			inputWAVStream = new FileInputStream(input);

			int offset = 0;
			byte[] header = new byte[12];
			inputWAVStream.read(header, 0, 12);
			offset += 12;
			if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' || header[8] != 'W'
					|| header[9] != 'A' || header[10] != 'V' || header[11] != 'E') {
				throw new java.io.IOException("Not a WAV file");
			}

			while (offset + 8 <= fileSize) {
				byte[] chunkHeader = new byte[8];
				inputWAVStream.read(chunkHeader, 0, 8);
				offset += 8;

				int chunkLen = ((0xff & chunkHeader[7]) << 24) | ((0xff & chunkHeader[6]) << 16)
						| ((0xff & chunkHeader[5]) << 8) | ((0xff & chunkHeader[4]));

				if (chunkHeader[0] == 'f' && chunkHeader[1] == 'm' && chunkHeader[2] == 't' && chunkHeader[3] == ' ') {
					if (chunkLen < 16 || chunkLen > 1024) {
						throw new java.io.IOException("WAV file has bad fmt chunk");
					}

					byte[] fmt = new byte[chunkLen];
					inputWAVStream.read(fmt, 0, chunkLen);
					offset += chunkLen;

					int format = ((0xff & fmt[1]) << 8) | ((0xff & fmt[0]));
					if (format != 1) {
						throw new java.io.IOException("Unsupported WAV file encoding");
					}

					config.numberOfChannels = ((0xff & fmt[3]) << 8) | ((0xff & fmt[2]));
					config.sampleSize = 16; // TODO: always 16-bit?
					config.sampleFrequency = ((0xff & fmt[7]) << 24) | ((0xff & fmt[6]) << 16) | ((0xff & fmt[5]) << 8)
							| ((0xff & fmt[4]));

				} else if (chunkHeader[0] == 'd' && chunkHeader[1] == 'a' && chunkHeader[2] == 't'
						&& chunkHeader[3] == 'a') {
					if (config.numberOfChannels == 0 || config.sampleFrequency == 0) {
						throw new java.io.IOException("Bad WAV file: data chunk before fmt chunk");
					}

					// copy the bits from input stream to output stream
					byte[] buf = new byte[IOUtilities.IO_BUFFER_SIZE];
					int len;
					while ((len = inputWAVStream.read(buf)) > 0) {
						output.write(buf, 0, len);
					}

				} else {
					inputWAVStream.skip(chunkLen);
					offset += chunkLen;
				}
			}
		} finally {
			IOUtilities.closeStream(inputWAVStream);
		}
	}
}
