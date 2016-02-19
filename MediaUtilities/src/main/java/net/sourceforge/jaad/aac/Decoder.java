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
package net.sourceforge.jaad.aac;

import net.sourceforge.jaad.aac.syntax.BitStream;
import net.sourceforge.jaad.aac.syntax.Constants;
import net.sourceforge.jaad.aac.syntax.PCE;
import net.sourceforge.jaad.aac.syntax.SyntacticElements;
import net.sourceforge.jaad.aac.filterbank.FilterBank;
import net.sourceforge.jaad.aac.transport.ADIFHeader;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

/**
 * Main AAC decoder class
 * 
 * @author in-somnia
 */
public class Decoder implements Constants {

	static {
		for (Handler h : LOGGER.getHandlers()) {
			LOGGER.removeHandler(h);
		}
		LOGGER.setLevel(Level.WARNING);

		final ConsoleHandler h = new ConsoleHandler();
		h.setLevel(Level.ALL);
		LOGGER.addHandler(h);
	}
	private final DecoderConfig config;
	private final SyntacticElements syntacticElements;
	private final FilterBank filterBank;
	private BitStream in;
	private ADIFHeader adifHeader;

	/**
	 * The methods returns true, if a profile is supported by the decoder.
	 * 
	 * @param profile an AAC profile
	 * @return true if the specified profile can be decoded
	 * @see Profile#isDecodingSupported()
	 */
	public static boolean canDecode(Profile profile) {
		return profile.isDecodingSupported();
	}

	/**
	 * Initializes the decoder with a MP4 decoder specific info.
	 * 
	 * After this the MP4 frames can be passed to the <code>decodeFrame(byte[], SampleBuffer)</code> method to decode
	 * them.
	 * 
	 * @param decoderSpecificInfo a byte array containing the decoder specific info from an MP4 container
	 * @throws AACException if the specified profile is not supported
	 */
	public Decoder(byte[] decoderSpecificInfo) throws AACException {
		config = DecoderConfig.parseMP4DecoderSpecificInfo(decoderSpecificInfo);
		if (config == null)
			throw new IllegalArgumentException("illegal MP4 decoder specific info");

		if (!canDecode(config.getProfile()))
			throw new AACException("unsupported profile: " + config.getProfile().getDescription());

		syntacticElements = new SyntacticElements(config);
		filterBank = new FilterBank(config.isSmallFrameUsed(), config.getChannelConfiguration().getChannelCount());

		in = new BitStream();

		LOGGER.log(Level.FINE, "profile: {0}", config.getProfile());
		LOGGER.log(Level.FINE, "sf: {0}", config.getSampleFrequency().getFrequency());
		LOGGER.log(Level.FINE, "channels: {0}", config.getChannelConfiguration().getDescription());
	}

	public DecoderConfig getConfig() {
		return config;
	}

	/**
	 * Decodes one frame of AAC data in frame mode and returns the raw PCM data.
	 * 
	 * @param frame the AAC frame
	 * @param buffer a buffer to hold the decoded PCM data
	 * @throws AACException if decoding fails
	 */
	public void decodeFrame(byte[] frame, SampleBuffer buffer) throws AACException {
		if (frame != null)
			in.setData(frame);
		try {
			decode(buffer);
		} catch (AACException e) {
			if (!e.isEndOfStream())
				throw e;
			else
				LOGGER.warning("unexpected end of frame");
		}
	}

	private void decode(SampleBuffer buffer) throws AACException {
		if (ADIFHeader.isPresent(in)) {
			adifHeader = ADIFHeader.readHeader(in);
			final PCE pce = adifHeader.getFirstPCE();
			config.setProfile(pce.getProfile());
			config.setSampleFrequency(pce.getSampleFrequency());
			config.setChannelConfiguration(ChannelConfiguration.forInt(pce.getChannelCount()));
		}

		if (!canDecode(config.getProfile()))
			throw new AACException("unsupported profile: " + config.getProfile().getDescription());

		syntacticElements.startNewFrame();

		try {
			// 1: bitstream parsing and noiseless coding
			syntacticElements.decode(in);
			// 2: spectral processing
			syntacticElements.process(filterBank);
			// 3: send to output buffer
			syntacticElements.sendToOutput(buffer);
		} catch (AACException e) {
			buffer.setData(new byte[0], 0, 0, 0, 0);
			throw e;
		} catch (Exception e) {
			buffer.setData(new byte[0], 0, 0, 0, 0);
			throw new AACException(e);
		}
	}
}
