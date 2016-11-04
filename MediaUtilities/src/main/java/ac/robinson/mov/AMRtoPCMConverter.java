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
import java.io.InputStream;
import java.io.OutputStream;

import ac.robinson.util.IOUtilities;

//TODO: currently we only support AMR-NB, but we could support AMR-WB as well (is it necessary?)
public final class AMRtoPCMConverter {

	private static int mNativeAmrDecoder = 0; // the pointer to the native amr-nb decoder

	/* From WmfDecBytesPerFrame in dec_input_format_tab.cpp */
	private static int mFrameSizes[] = { 12, 13, 15, 17, 19, 20, 26, 31, 5, 6, 5, 5, 0, 0, 0, 0 };

	/**
	 * Convert an AMR input file to PCM
	 * 
	 * @param input the input file to convert
	 * @param output the output stream to write to
	 * @throws IOException
	 */
	public static void convertFile(File input, OutputStream output) throws IOException {
		int fileSize = (int) input.length();
		if (fileSize < 128) {
			throw new java.io.IOException("File too small to parse");
		}

		FileInputStream inputAMRStream = null;
		try {
			inputAMRStream = new FileInputStream(input);

			// check the file header is correct
			byte[] header = new byte[12];
			inputAMRStream.read(header, 0, 6);
			if (header[0] != '#' || header[1] != '!' || header[2] != 'A' || header[3] != 'M' || header[4] != 'R'
					|| header[5] != '\n') {

				// not an AMR file; probably 3gp/3gpp - remove the header
				inputAMRStream.read(header, 6, 6);
				if (header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p' && header[8] == '3'
						&& header[9] == 'g' && header[10] == 'p' && header[11] == '4') {
					int boxLen = ((0xff & header[0]) << 24) | ((0xff & header[1]) << 16) | ((0xff & header[2]) << 8)
							| ((0xff & header[3]));
					if (boxLen >= 4 && boxLen <= fileSize - 8) {
						inputAMRStream.skip(boxLen - 12);
					}
					strip3GPPHeader(inputAMRStream, fileSize - boxLen);
				} else {
					throw new IOException("Not an AMR or 3GP/3GPP file");
				}
			}

			mNativeAmrDecoder = AmrDecoderInit();

			byte[] amrBuffer = new byte[32]; // maximum frame size (see above) is 31 (+ 1-byte length)
			short[] outputBuffer = new short[160]; // length copied from amrnb-dec.c; hardcoded below too
			int i, n, size;
			while (true) {
				// read the mode byte
				if (inputAMRStream.read(amrBuffer, 0, 1) <= 0) {
					break;
				}

				// find the packet size
				size = mFrameSizes[(amrBuffer[0] >> 3) & 0x0f];
				n = inputAMRStream.read(amrBuffer, 1, size);
				if (n < size) {
					break;
				}

				// decode using the native AMR decoder - produces signed 16-bit mono at 8000Hz
				AmrDecoderDecode(mNativeAmrDecoder, amrBuffer, outputBuffer, 0);

				// convert to byte and write to the output stream
				for (i = 0; i < 160; i++) {
					// output.write(outputBuffer[i] & 0xff); // little endian
					output.write((outputBuffer[i] >> 8) & 0xff);
					output.write(outputBuffer[i] & 0xff); // we want big endian
				}
			}

		} finally {
			if (mNativeAmrDecoder != 0) {
				AmrDecoderExit(mNativeAmrDecoder);
			}
			IOUtilities.closeStream(inputAMRStream);
		}
	}

	private static void strip3GPPHeader(InputStream stream, int maxLen) throws java.io.IOException {
		// this is copied directly from CheapAMR
		if (maxLen < 8) {
			return;
		}
		byte[] boxHeader = new byte[8];
		stream.read(boxHeader, 0, 8);
		int boxLen = ((0xff & boxHeader[0]) << 24) | ((0xff & boxHeader[1]) << 16) | ((0xff & boxHeader[2]) << 8)
				| ((0xff & boxHeader[3]));
		if (boxLen > maxLen || boxLen <= 0) {
			return;
		}
		if (boxHeader[4] == 'm' && boxHeader[5] == 'd' && boxHeader[6] == 'a' && boxHeader[7] == 't') {
			return;
		}
		stream.skip(boxLen - 8);
		strip3GPPHeader(stream, maxLen - boxLen);
	}

	// note: the native library is loaded in MediaUtilities during capability detection
	private static native int AmrDecoderInit();

	private static native int AmrDecoderDecode(int gae, byte[] amr, short[] pcm, int unused) throws IOException;

	private static native void AmrDecoderExit(int decoder);
}
