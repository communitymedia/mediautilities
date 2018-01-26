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

import net.javazoom.jl.decoder.Bitstream;
import net.javazoom.jl.decoder.BitstreamException;
import net.javazoom.jl.decoder.Decoder;
import net.javazoom.jl.decoder.DecoderException;
import net.javazoom.jl.decoder.Header;
import net.javazoom.jl.decoder.SampleBuffer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import ac.robinson.util.IOUtilities;

public class MP3toPCMConverter {

	private static final int FILE_START = 0;
	private static final int FILE_END = -1;

	public static class MP3Configuration {
		public int sampleFrequency = 0;
		public int sampleSize = 0;
		public int numberOfChannels = 0;

		@Override
		public String toString() {
			return this.getClass().getName() + "[" + sampleFrequency + "," + sampleSize + "," + numberOfChannels + "]";
		}
	}

	/**
	 * Convert an MP3 input file to PCM
	 *
	 * @param input  the input MP3 file
	 * @param output a stream to write the PCM to
	 * @param config an MP3Configuration instance that will be configured with the stream's properties
	 * @throws IOException
	 */
	public static void convertFile(File input, OutputStream output, MP3Configuration config) throws IOException {
		convertFile(input, output, config, FILE_START, FILE_END);
	}

	/**
	 * Convert an MP3 input file to PCM
	 * <p>
	 * See: http://mindtherobot.com/blog/624/android-audio-play-an-mp3-file-on-an-audiotrack/
	 *
	 * @param input   the input MP3 file
	 * @param output  a stream to write the PCM to
	 * @param config  an MP3Configuration instance that will be configured with the stream's properties
	 * @param startMs time to start reading the MP3 from, 0 for the start
	 * @param endMs   time to stop reading the MP3 from, or -1 for the end
	 * @throws IOException
	 */
	public static void convertFile(File input, OutputStream output, MP3Configuration config, int startMs, int endMs)
			throws IOException {
		float totalMs = 0;
		boolean seeking = true;

		InputStream inputStream = null;
		try {
			inputStream = new BufferedInputStream(new FileInputStream(input), 8 * 1024);
			Bitstream bitstream = new Bitstream(inputStream);
			Decoder decoder = new Decoder();
			SampleBuffer outputPCM;

			boolean done = false;
			while (!done) {
				Header frameHeader = bitstream.readFrame();
				if (frameHeader == null) {
					done = true;
				} else {
					totalMs += frameHeader.ms_per_frame();

					if (totalMs >= startMs) {
						seeking = false;
					}

					if (!seeking) {
						outputPCM = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);

						if (config.sampleFrequency == 0) {
							config.sampleFrequency = outputPCM.getSampleFrequency();
							config.sampleSize = 16; // output should always be 16-bit, even if, say, 24 or 32-bit input
							config.numberOfChannels = 2; // outputPCM.getChannelCount(); // see mono fix, below
						}

						// always output in stereo, as mixing mono and stereo MP3s is fairly common amongst our users
						// (e.g., audio track + dictaphone output), and this is an easy fix (i.e., duplicate channel 1)
						boolean mono = outputPCM.getChannelCount() == 1;

						// for mono inputs the buffer is half-full - earlier versions had a bug here where the for loop
						// was "optimised" into a foreach, but this meant that we had outputs of half silence for mono
						// audio, as the whole buffer was always used in the output
						short[] pcm = outputPCM.getBuffer();
						for (int i = 0, n = outputPCM.getBufferLength(); i < n; i += 1) {
							// output.write(s & 0xff); // little-endian
							output.write((pcm[i] >> 8) & 0xff);
							output.write(pcm[i] & 0xff); // we want big-endian
							if (mono) {
								output.write((pcm[i] >> 8) & 0xff);
								output.write(pcm[i] & 0xff);
							}
						}
					}

					if (endMs != FILE_END && totalMs >= (startMs + endMs)) {
						done = true;
					}
				}
				bitstream.closeFrame();
			}
		} catch (BitstreamException e) {
			throw new IOException("Bitstream error: " + e);
		} catch (DecoderException e) {
			throw new IOException("Decoder exception: " + e);
		} catch (FileNotFoundException e) {
			throw new IOException("File not found: " + e);
		} finally {
			IOUtilities.closeStream(inputStream);
		}
	}
}
