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

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.List;

import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Frame;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Track;

public class MP4toPCMConverter {

	private final AudioTrack track;

	public MP4toPCMConverter(RandomAccessFile input) throws IOException {
		final MP4Container cont = new MP4Container(input);
		final Movie movie = cont.getMovie();

		final List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);
		if (tracks.isEmpty()) {
			throw new IOException("The input file does not contain an AAC audio track");
		}

		track = (AudioTrack) tracks.get(0); // only the first track
	}

	public int getSampleRate() {
		return track.getSampleRate();
	}

	public int getSampleSize() {
		return track.getSampleSize();
	}

	public void convertFile(OutputStream output) throws IOException {
		final Decoder dec = new Decoder(track.getDecoderSpecificInfo());
		Frame audioFrame;
		final SampleBuffer buf = new SampleBuffer();
		while (track.hasMoreFrames()) {
			audioFrame = track.readNextFrame();
			dec.decodeFrame(audioFrame.getData(), buf);
			output.write(buf.getData());
		}
	}
}
