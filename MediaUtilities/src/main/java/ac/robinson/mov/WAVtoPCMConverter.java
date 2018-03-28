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

import android.util.Log;

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
	 * @param input  the input file to convert
	 * @param output the output stream to write to
	 * @throws IOException
	 */
	// @SuppressWarnings("resource") is to suppress complaint about stream closure (handled by closeStream in finally)
	@SuppressWarnings("resource")
	public static void convertFile(File input, OutputStream output, WAVConfiguration config) throws IOException {
		int fileSize = (int) input.length();
		if (fileSize < 128) {
			throw new IOException("File too small to parse");
		}

		FileInputStream inputWAVStream = null;
		try {
			inputWAVStream = new FileInputStream(input);

			int offset = 0;
			byte[] header = new byte[12];
			inputWAVStream.read(header, 0, 12);
			offset += 12;
			if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' || header[8] != 'W' ||
					header[9] != 'A' || header[10] != 'V' || header[11] != 'E') {
				throw new IOException("Not a WAV file");
			}

			while (offset + 8 <= fileSize) {
				byte[] chunkHeader = new byte[8];
				inputWAVStream.read(chunkHeader, 0, 8);
				offset += 8;

				int chunkLen = ((0xff & chunkHeader[7]) << 24) | ((0xff & chunkHeader[6]) << 16) |
						((0xff & chunkHeader[5]) << 8) | ((0xff & chunkHeader[4]));

				if (chunkHeader[0] == 'f' && chunkHeader[1] == 'm' && chunkHeader[2] == 't' && chunkHeader[3] == ' ') {
					if (chunkLen < 16 || chunkLen > 1024) {
						throw new IOException("WAV file has bad fmt chunk");
					}

					byte[] fmt = new byte[chunkLen];
					inputWAVStream.read(fmt, 0, chunkLen);
					offset += chunkLen;

					int format = ((0xff & fmt[1]) << 8) | ((0xff & fmt[0]));
					if (format != 1) {
						throw new IOException("Unsupported WAV file encoding (only 16-bit PCM is supported)");
					}

					config.numberOfChannels = ((0xff & fmt[3]) << 8) | ((0xff & fmt[2]));
					config.sampleSize = ((0xff & fmt[15]) << 8) | ((0xff & fmt[14]));
					config.sampleFrequency =
							((0xff & fmt[7]) << 24) | ((0xff & fmt[6]) << 16) | ((0xff & fmt[5]) << 8) |
									((0xff & fmt[4]));

				} else if (chunkHeader[0] == 'd' && chunkHeader[1] == 'a' && chunkHeader[2] == 't' &&
						chunkHeader[3] == 'a') {
					if (config.numberOfChannels == 0 || config.sampleFrequency == 0) {
						throw new IOException("Bad WAV file: data chunk before fmt chunk");
					}

					// always output in mono, as mixing mono and stereo WAVs is fairly common amongst our users
					// (e.g, audio track + dictaphone output), and this is an easy fix (i.e., average to 1 channel)
					boolean mono = config.numberOfChannels == 1;

					int numSamples;
					byte[] buffer = new byte[IOUtilities.IO_BUFFER_SIZE];
					while ((numSamples = inputWAVStream.read(buffer)) > 0) {
						if (mono) {
							output.write(buffer, 0, numSamples);
						} else {
							if (config.sampleSize == 8) {
								for (int i = 0; i < numSamples; i += 2) {
									short average = (short) ((buffer[i] >> 1) + (buffer[i + 1] >> 1) +
											(buffer[i] & buffer[i + 1] & 0x1));
									output.write(average & 0xff);
									output.write((average >> 8) & 0xff);
								}
							} else { // 16-bit
								for (int i = 0; i < numSamples; i += 4) {
									short left = (short) (((buffer[i + 1] & 0xff) << 8) | (buffer[i] & 0xff));
									short right = (short) (((buffer[i + 3] & 0xff) << 8) | (buffer[i + 2] & 0xff));
									short average = (short) ((left >> 1) + (right >> 1) + (left & right & 0x1));
									output.write(average & 0xff);
									output.write((average >> 8) & 0xff);
								}
							}
						}
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

	public static void getFileConfig(File input, WAVConfiguration config) throws IOException {
		int fileSize = (int) input.length();
		if (fileSize < 128) {
			throw new IOException("File too small to parse");
		}

		FileInputStream inputWAVStream = null;
		try {
			inputWAVStream = new FileInputStream(input);

			int offset = 0;
			byte[] header = new byte[12];
			inputWAVStream.read(header, 0, 12);
			offset += 12;
			if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' || header[8] != 'W' ||
					header[9] != 'A' || header[10] != 'V' || header[11] != 'E') {
				throw new IOException("Not a WAV file");
			}

			while (offset + 8 <= fileSize) {
				byte[] chunkHeader = new byte[8];
				inputWAVStream.read(chunkHeader, 0, 8);
				offset += 8;

				int chunkLen = ((0xff & chunkHeader[7]) << 24) | ((0xff & chunkHeader[6]) << 16) |
						((0xff & chunkHeader[5]) << 8) | ((0xff & chunkHeader[4]));

				if (chunkHeader[0] == 'f' && chunkHeader[1] == 'm' && chunkHeader[2] == 't' && chunkHeader[3] == ' ') {
					if (chunkLen < 16 || chunkLen > 1024) {
						throw new IOException("WAV file has bad fmt chunk");
					}

					byte[] fmt = new byte[chunkLen];
					inputWAVStream.read(fmt, 0, chunkLen);
					offset += chunkLen;

					int format = ((0xff & fmt[1]) << 8) | ((0xff & fmt[0]));
					if (format != 1) {
						throw new IOException("Unsupported WAV file encoding (only 16-bit PCM is supported)");
					}

					config.numberOfChannels = ((0xff & fmt[3]) << 8) | ((0xff & fmt[2]));
					config.sampleSize = ((0xff & fmt[15]) << 8) | ((0xff & fmt[14]));
					config.sampleFrequency =
							((0xff & fmt[7]) << 24) | ((0xff & fmt[6]) << 16) | ((0xff & fmt[5]) << 8) |
									((0xff & fmt[4]));
					break;

				} else if (chunkHeader[0] == 'd' && chunkHeader[1] == 'a' && chunkHeader[2] == 't' &&
						chunkHeader[3] == 'a') {
					if (config.numberOfChannels == 0 || config.sampleFrequency == 0) {
						throw new IOException("Bad WAV file: data chunk before fmt chunk");
					}
					inputWAVStream.skip(chunkLen);
					offset += chunkLen;
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
