/*
 * @(#)MovWriter
 *
 * $Date: 2012-03-09 15:34:43 +0000 (Fri, 09 Mar 2012) $
 *
 * Copyright (c) 2011 by Jeremy Wood.
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
package ac.robinson.mov;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import ac.robinson.util.BitmapUtilities;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.bric.audio.AudioFormat;
import com.bric.audio.AudioInputStream;
import com.bric.audio.AudioSystem;
import com.bric.audio.CombinedAudioInputStream;
import com.bric.audio.SilentAudioInputStream;
import com.bric.io.MeasuredOutputStream;
import com.bric.qt.io.Atom;
import com.bric.qt.io.ChunkOffsetAtom;
import com.bric.qt.io.DataReferenceAtom;
import com.bric.qt.io.EditAtom;
import com.bric.qt.io.EditListAtom;
import com.bric.qt.io.HandlerReferenceAtom;
import com.bric.qt.io.MediaHeaderAtom;
import com.bric.qt.io.MovieHeaderAtom;
import com.bric.qt.io.ParentAtom;
import com.bric.qt.io.SampleDescriptionAtom;
import com.bric.qt.io.SampleSizeAtom;
import com.bric.qt.io.SampleToChunkAtom;
import com.bric.qt.io.SoundMediaInformationHeaderAtom;
import com.bric.qt.io.SoundSampleDescriptionAtom;
import com.bric.qt.io.SoundSampleDescriptionEntry;
import com.bric.qt.io.TimeToSampleAtom;
import com.bric.qt.io.TrackHeaderAtom;
import com.bric.qt.io.VideoMediaInformationHeaderAtom;
import com.bric.qt.io.VideoSampleDescriptionEntry;

/**
 * This writes a QuickTime MOV file as a series of images, and interleaves optional PCM (uncompressed) audio.
 * <P>
 * This abstract class does not actually encode the image data; subclasses decide how to do this. The only two current
 * subclasses use either JPG or PNG compression. By modern standards: this results in a very poorly compressed image
 * file, but it is free of painful legal implications that come with the MPEG-4 standard. (And it is worlds easier to
 * implement.)
 * <P>
 * This actually writes to a movie file in 2 passes: the first pass writes all the video and audio data to a
 * <code>FileOutputStream</code>. When <code>close()</code> is called, the movie structure is added and a
 * <code>RandomAccessFile</code> is used to correctly set the size headers.
 * 
 * @name MovWriter
 * @title Movies: Writing MOV Files Without QuickTime
 * @release June 2008
 * @blurb This article presents a class that can write a .mov file as a series of images and PCM audio.
 *        <p>
 *        This movie uses a combination of JPG/PNG and WAV encoding, which is very poor compression. But this format is
 *        not subject to nasty patent/royalty issues.
 * @see <a href="http://javagraphics.blogspot.com/2008/06/movies-writing-mov-files-without.html">Movies: Writing MOV
 *      Files Without QuickTime</a>
 * 
 */
public abstract class MovWriter {

	public static final long DEFAULT_TIME_SCALE = 30;

	private static class VideoSample {
		final int duration;
		final long fileLength;
		final long dataStart;

		public VideoSample(int duration, long dataStart, long fileLength) {
			if (duration <= 0)
				throw new IllegalArgumentException("duration (" + duration + ") must be greater than zero.");
			if (fileLength <= 0)
				throw new IllegalArgumentException("file length (" + fileLength + ") must be greater than zero.");
			this.duration = duration;
			this.fileLength = fileLength;
			this.dataStart = dataStart;
		}
	}

	class VideoTrack {
		List<VideoSample> samples = new Vector<VideoSample>();
		protected int w = -1, h = -1;
		long totalDuration;
		TimeToSampleAtom stts = new TimeToSampleAtom();
		SampleSizeAtom stsz = new SampleSizeAtom();
		SampleToChunkAtom stsc = new SampleToChunkAtom();
		ChunkOffsetAtom stco = new ChunkOffsetAtom();

		void writeToMoovRoot(ParentAtom moovRoot, int trackIndex) {
			ParentAtom trakAtom = new ParentAtom("trak");
			moovRoot.add(trakAtom);
			TrackHeaderAtom trackHeader = new TrackHeaderAtom(trackIndex, totalDuration, w, h);
			trackHeader.volume = 0;
			trakAtom.add(trackHeader);

			// EditAtom editAtom = new EditAtom();
			// EditListAtom editListAtom = new EditListAtom();
			// editListAtom.addEditListTableEntry(totalDuration, 0, 1f);
			// editAtom.add(editListAtom);
			// trakAtom.add(editAtom);

			ParentAtom mdiaAtom = new ParentAtom("mdia");
			trakAtom.add(mdiaAtom);
			MediaHeaderAtom mediaHeader = new MediaHeaderAtom(DEFAULT_TIME_SCALE, totalDuration);
			mdiaAtom.add(mediaHeader);
			HandlerReferenceAtom handlerRef1 = new HandlerReferenceAtom("mhlr", "vide", "bric");
			mdiaAtom.add(handlerRef1);
			ParentAtom minf = new ParentAtom("minf");
			mdiaAtom.add(minf);
			VideoMediaInformationHeaderAtom vmhd = new VideoMediaInformationHeaderAtom();
			minf.add(vmhd);
			HandlerReferenceAtom handlerRef2 = new HandlerReferenceAtom("dhlr", "alis", "bric");
			minf.add(handlerRef2);

			ParentAtom dinf = new ParentAtom("dinf");
			minf.add(dinf);
			DataReferenceAtom dref = new DataReferenceAtom();
			dref.addEntry("alis", 0, 1, new byte[] {});
			dinf.add(dref);

			ParentAtom stbl = new ParentAtom("stbl");
			minf.add(stbl);

			SampleDescriptionAtom stsd = new SampleDescriptionAtom();
			stsd.addEntry(getVideoSampleDescriptionEntry());
			stbl.add(stsd);

			stbl.add(stts);
			stbl.add(stsc);
			stbl.add(stsz);
			stbl.add(stco);
		}

		int samplesInCurrentChunk = 0;
		long durationOfCurrentChunk = 0;
		int currentChunkIndex = 0;

		private void addSample(VideoSample sample) throws IOException {
			samples.add(sample);
			totalDuration += sample.duration;

			// we only really need one sample size here, but YouTube skips frames fairly regularly, so we need to make
			// sure that we've got enough frames so that this doesn't matter; this is done by repeatedly referring
			// to the same entry in the chunk table
			stts.addSampleTime(sample.duration, 1, true);
			for (int i = 0; i < sample.duration; i++) {
				stsz.addSampleSize(sample.fileLength); // TODO: optimise these types of functions
			}

			// now decide if the addition of this sample concluded a chunk of samples:
			samplesInCurrentChunk++;
			durationOfCurrentChunk += sample.duration;
			if (durationOfCurrentChunk >= DEFAULT_TIME_SCALE) {
				closeChunk();
			}
		}

		void close() throws IOException {
			closeChunk();
		}

		private void closeChunk() throws IOException {
			if (samplesInCurrentChunk > 0) {
				stsc.addChunk(currentChunkIndex + 1, samplesInCurrentChunk, 1);

				// we only really need one sample size here, but YouTube skips frames fairly regularly, so we need to
				// make sure that we've got enough frames so that this doesn't matter; this is done by repeatedly
				// referring to the same entry in the chunk table
				VideoSample sample = samples.get(samples.size() - samplesInCurrentChunk);
				for (int i = 0; i < sample.duration; i++) {
					stco.addChunkOffset(sample.dataStart);
				}

				for (AudioTrack audio : audioTracks) {
					audio.writeAudio(durationOfCurrentChunk);
				}

				// reset variables
				currentChunkIndex++;
				samplesInCurrentChunk = 0;
				durationOfCurrentChunk = 0;
			}
		}

		void addFrame(int duration, File imageFile) throws IOException {
			BitmapFactory.Options imageOptions = BitmapUtilities.getImageDimensions(imageFile.getAbsolutePath());
			validateSize(imageOptions.outWidth, imageOptions.outHeight);
			long byteSize = write(out, imageFile);
			VideoSample sample = new VideoSample(duration, out.getBytesWritten() - byteSize, byteSize);
			addSample(sample);
		}

		void validateSize(int width, int height) {
			if (width == -1)
				throw new IllegalArgumentException("width = " + width);
			if (height == -1)
				throw new IllegalArgumentException("height = " + height);

			if (w == -1 && h == -1) {
				w = width;
				h = height;
			} else {
				if (w != width || h != height) {
					throw new IllegalArgumentException("Each frame must have the same dimension.  This frame (" + width
							+ "x" + height + ") is not the same dimensions as previous frames (" + w + "x" + h + ").");
				}
			}
		}

		boolean isEmpty() {
			return samples.size() == 0;
		}
	}

	private class AudioTrack {

		/** The AudioInputStream to read data from. */
		AudioInputStream audioIn;

		/** The number of samples written. */
		long totalSamples;

		/** The duration, relative not to the audio's data but to the movie's time scale. */
		long totalDurationInMovieTimeScale;

		// we might need to convert the endian-ness
		boolean reverseBytePairs = false;

		long myTimeScale;
		TimeToSampleAtom stts = new TimeToSampleAtom();
		SampleSizeAtom stsz = new SampleSizeAtom();
		SampleToChunkAtom stsc = new SampleToChunkAtom();
		ChunkOffsetAtom stco = new ChunkOffsetAtom();
		int sampleMultiplier;

		/** Whether to use an Edit List (or a silent track) to arrange the audio **/
		boolean useEditList = true; // for multi-part audio edit lists will be used regardless of this value
		float[] audioOffsets;
		float[] audioStarts;
		float[] audioLengths;

		/**
		 * audioOffsets specifies where in the movie file each audio segment should be played; audioStarts and audioEnds
		 * specify where in the audio file each segment is located. Each *must* be at least 1 value in length
		 **/
		AudioTrack(AudioInputStream audio, float[] audioOffsets, float[] audioStarts, float[] audioLengths)
				throws IOException {
			this.audioOffsets = audioOffsets;
			this.audioStarts = audioStarts;
			this.audioLengths = audioLengths;

			// hmm... I'm not sure that this logic has ever been tested, but it seems appropriate:
			AudioFormat audioFormat = audio.getFormat();
			AudioFormat.Encoding encoding = audioFormat.getEncoding();
			if (!(AudioFormat.Encoding.PCM_SIGNED.equals(encoding) || AudioFormat.Encoding.PCM_UNSIGNED
					.equals(encoding))) {
				if (audioFormat.getSampleSizeInBits() > 8) {
					audioIn = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, audio);
				} else {
					audioIn = AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_UNSIGNED, audio);
				}
			} else {
				audioIn = audio;
			}
			
			myTimeScale = (long) (audioIn.getFormat().getFrameRate());
			int bitsPerSample = audioIn.getFormat().getSampleSizeInBits();
			int numberOfChannels = audioIn.getFormat().getChannels();
			if (!(bitsPerSample == 8 || bitsPerSample == 16)) {
				throw new IllegalArgumentException("unsupported bitsPerSample: " + bitsPerSample);
			}
			if (!(numberOfChannels == 1 || numberOfChannels == 2)) {
				throw new IllegalArgumentException("unsupported numberOfChannels: " + numberOfChannels);
			}
			sampleMultiplier = audioIn.getFormat().getSampleSizeInBits() / 8 * numberOfChannels;
			reverseBytePairs = bitsPerSample > 8 && (!audioIn.getFormat().isBigEndian());

			if (!useEditList) {
				/**
				 * Previously I tried using an EditAtom to change when an audio track began playing, but that only
				 * worked for about 1 audio track (when other audio tracks were added to the test: QT Player could play
				 * the movie back fine but a MovieExporter would drop other tracks).
				 * 
				 * This approach is simple: start every sound at t=0, and pad the AudioInputStream with silence until
				 * it's supposed to start.
				 * 
				 * TODO: A better approach is to only write one small chunk of silence, and then use the chunk lookup
				 * tables to continually reference the same silent chunk of data. This will dramatically improve file
				 * size if you position an audio very late in the movie.
				 * 
				 */
				// 25-Nov-12: using EditAtom instead, as smaller file sizes are more desirable (especially when
				// creating a narrative with sounds toward the end)
				// TODO: use the chunk table approach mentioned above, as we've done for audio, because some players
				// (VLC, for example) have very poor edit list handling, and usually just give up (meaning we lose
				// audio)
				if (audioOffsets.length == 1) {
					if (audioOffsets[0] > 0) {
						long silentSamples = (long) (audioOffsets[0] * audioIn.getFormat().getFrameRate());
						AudioInputStream silence = new SilentAudioInputStream(audioIn.getFormat(), silentSamples);
						audioIn = new CombinedAudioInputStream(silence, audioIn);
					}
				} else {
					useEditList = true;
				}
			}
		}

		long getDuration() {
			if (useEditList) {
				int editSizeIndex = audioOffsets.length - 1;
				return (long) (((audioOffsets[editSizeIndex] + audioLengths[editSizeIndex]) * DEFAULT_TIME_SCALE) + .5);
			}
			return totalDurationInMovieTimeScale;
		}

		void writeToMoovRoot(ParentAtom moovRoot, int trackIndex) {
			ParentAtom trakAtom = new ParentAtom("trak");
			moovRoot.add(trakAtom);
			TrackHeaderAtom trackHeader = new TrackHeaderAtom(trackIndex, (int) getDuration(), 0, 0);
			trakAtom.add(trackHeader);
			if (useEditList) {
				EditAtom editAtom = new EditAtom();
				EditListAtom editListAtom = new EditListAtom();
				float previousOffset = 0;
				for (int i = 0, n = audioOffsets.length; i < n; i++) {
					if (audioOffsets[i] > previousOffset) {
						editListAtom.addEditListTableEntry(
								(int) ((audioOffsets[i] - previousOffset) * DEFAULT_TIME_SCALE), -1, 1f);
					}
					editListAtom.addEditListTableEntry((int) (audioLengths[i] * DEFAULT_TIME_SCALE),
							(int) (audioStarts[i] * myTimeScale), 1f);
					previousOffset = audioOffsets[i] + audioLengths[i];
				}
				editAtom.add(editListAtom);
				trakAtom.add(editAtom);
			}
			ParentAtom mdiaAtom = new ParentAtom("mdia");
			trakAtom.add(mdiaAtom);
			MediaHeaderAtom mediaHeader = new MediaHeaderAtom(myTimeScale, totalSamples);
			mdiaAtom.add(mediaHeader);
			HandlerReferenceAtom handlerRef1 = new HandlerReferenceAtom("mhlr", "soun", "bric");
			mdiaAtom.add(handlerRef1);
			ParentAtom minf = new ParentAtom("minf");
			mdiaAtom.add(minf);
			SoundMediaInformationHeaderAtom smhd = new SoundMediaInformationHeaderAtom();
			minf.add(smhd);
			HandlerReferenceAtom handlerRef2 = new HandlerReferenceAtom("dhlr", "alis", "bric");
			minf.add(handlerRef2);
			ParentAtom dinf = new ParentAtom("dinf");
			minf.add(dinf);
			DataReferenceAtom dref = new DataReferenceAtom();
			dref.addEntry("alis", 0, 1, new byte[] {});
			dinf.add(dref);

			ParentAtom stbl = new ParentAtom("stbl");
			minf.add(stbl);

			int numberOfChannels = audioIn.getFormat().getChannels();
			int bitsPerSample = audioIn.getFormat().getSampleSizeInBits();
			float sampleRate = audioIn.getFormat().getSampleRate();

			SoundSampleDescriptionAtom stsd = new SoundSampleDescriptionAtom();
			stsd.addEntry(new SoundSampleDescriptionEntry(1, numberOfChannels, bitsPerSample, sampleRate));
			stbl.add(stsd);
			stbl.add(stts);
			stbl.add(stsc);
			stbl.add(stsz);
			stbl.add(stco);
		}

		int currentChunkIndex = 0;

		/**
		 * Write audio data to the target file.
		 * <p>
		 * If there is no more data to write then this method will do nothing.
		 * 
		 * @param time the duration (relative to DEFAULT_TIME_SCALE) of audio to write. For example if this is 1200 and
		 *            DEFAULT_TIME_SCALE is 600: then this should write 2 seconds of audio data.
		 * @return true if data was written, false if the AudioInputStream has been depleted.
		 * @throws IOException
		 */
		boolean writeAudio(long time) throws IOException {
			long durationInMyTimeScale = (time * myTimeScale) / DEFAULT_TIME_SCALE;

			long byteCount = durationInMyTimeScale * sampleMultiplier;

			byteCount = write(out, audioIn, byteCount, reverseBytePairs);
			if (byteCount > 0) {
				closeChunk(byteCount);
				return true;
			}
			return false;
		}

		void close() throws IOException {
			stts.addSampleTime(totalSamples, 1, true);

			stsz.setSampleSize(1);
			stsz.setSampleCount(totalSamples);
			stsz.setSizeTable(null);

			audioIn.close();
		}

		private void closeChunk(long chunkSizeInBytes) {
			if (chunkSizeInBytes <= 0)
				throw new IllegalArgumentException("chunkSizeInBytes = " + chunkSizeInBytes);

			long dataStart = out.getBytesWritten() - chunkSizeInBytes;

			stsc.addChunk(currentChunkIndex + 1, chunkSizeInBytes / sampleMultiplier, 1);
			stco.addChunkOffset(dataStart);
			currentChunkIndex++;

			long samplesWritten = chunkSizeInBytes / sampleMultiplier;
			totalSamples += samplesWritten;
			totalDurationInMovieTimeScale += samplesWritten * DEFAULT_TIME_SCALE / myTimeScale;
		}
	}

	/** The output stream we write the movie data to. */
	private MeasuredOutputStream out;

	/** The file we're writing to. */
	File dest;

	/** Whether close() has been called yet. */
	private boolean closed = false;

	/** The video track. */
	protected VideoTrack videoTrack = new VideoTrack();

	/** The audio tracks to include. */
	protected AudioTrack[] audioTracks = new AudioTrack[] {};

	/**
	 * Constructs a new <code>MovWriter</code>.
	 * <P>
	 * By constructing this object a <code>FileOutputStream</code> is opened for the destination file. It remains open
	 * until <code>close()</code> is called or this object is finalized.
	 * 
	 * @param file the file data is written to. It is strongly recommended that this file name end with ".mov" (or
	 *            ".MOV"), although this is not required.
	 * @throws IOException
	 */
	public MovWriter(File file) throws IOException {
		dest = file;
		file.createNewFile();
		out = new MeasuredOutputStream(new FileOutputStream(file));

		// create the file type atom - must be the first atom; essential for playback in, e.g., VLC
		// see: http://developer.apple.com/library/mac/#documentation/QuickTime/QTFF/QTFFChap1/qtff1.html
		Atom.write32Int(out, 32); // mdat size will be adjusted relative to this later
		Atom.write32String(out, "ftyp");
		Atom.write32String(out, "qt  ");
		Atom.write32Int(out, 537331972); // 537331972 = 2007/09/04 as BCD
		Atom.write32String(out, "qt  ");
		Atom.write32String(out, "bric");
		Atom.write32String(out, ""); // future
		Atom.write32String(out, ""); // future

		// TODO: should use a "wide" atom here, and use the extended atom only when needed

		Atom.write32Int(out, 1); // an extended size field
		Atom.write32String(out, "mdat");

		// the extended size field: an 8-byte long that will eventually
		// reflect the size of the data atom. We don't know this in the
		// first pass, so write 8 zeroes, and we'll fill this gap in
		// when .close() is called:
		Atom.write32Int(out, 0);
		Atom.write32Int(out, 0);

		// necessary for some players
		// for (int i = 0; i < 116; i++) {
		// Atom.write32Int(out, 0); // pad to 512 bytes at the start of the file
		// }
	}

	/**
	 * Add an AudioInputStream to this movie. The audio data will be interleaved with the visual data in the output
	 * movie.
	 * <p>
	 * This method must be called before adding frames. In a future version of this class it would be nice if audio
	 * could be inserted at any time (before calling <code>close()</code>), but currently that functionality is not
	 * supported.
	 * 
	 * @param audio the audio to add to this movie, in PCM encoding.
	 * @param audioOffsets where in the movie each segment of the audio track should be played. Values *must* increase
	 *            throughout the array, and the array *must* be at least 1 value in length
	 * @param audioStarts where in the audio track each segment starts
	 * @param audioLengths the length of each segment in the audio track
	 * @throws IOException
	 * @throws RuntimeException if you invoke this method after calling <code>addFrame()</code> or <code>close()</code>.
	 */
	public synchronized void addSegmentedAudioTrack(AudioInputStream audio, float[] audioOffsets, float[] audioStarts,
			float[] audioLengths) throws IOException {
		if (closed)
			throw new RuntimeException("this writer has already been closed");

		if (videoTrack.isEmpty() == false)
			throw new RuntimeException("cannot add audio after video data has been started");
		AudioTrack newTrack;

		newTrack = new AudioTrack(audio, audioOffsets, audioStarts, audioLengths);

		AudioTrack[] newTracks = new AudioTrack[audioTracks.length + 1];
		System.arraycopy(audioTracks, 0, newTracks, 0, audioTracks.length);
		newTracks[newTracks.length - 1] = newTrack;
		audioTracks = newTracks;

		/**
		 * The QT File Format says: In order to overcome any latencies in sound playback, at least one second of sound
		 * data is placed at the beginning of the interleaved data. This means that the sound and video data are offset
		 * from each other in the file by one second.
		 */
		newTrack.writeAudio(DEFAULT_TIME_SCALE * 1);
	}

	/**
	 * Add an AudioInputStream to this movie. The audio data will be interleaved with the visual data in the output
	 * movie.
	 * <p>
	 * This method must be called before adding frames. In a future version of this class it would be nice if audio
	 * could be inserted at any time (before calling <code>close()</code>), but currently that functionality is not
	 * supported.
	 * 
	 * @param audio the audio to add to this movie, in PCM encoding.
	 * @param startTime the start time (in seconds) of this audio in the movie. For example: if this is 5, then this
	 *            audio will begin 5 seconds into the movie.
	 * @throws IOException
	 * @throws RuntimeException if you invoke this method after calling <code>addFrame()</code> or <code>close()</code>.
	 */
	public synchronized void addAudioTrack(AudioInputStream audio, float startTime) throws IOException {
		addAudioTrack(audio, startTime, Float.POSITIVE_INFINITY);
	}

	/**
	 * Add an AudioInputStream to this movie. The audio data will be interleaved with the visual data in the output
	 * movie.
	 * <p>
	 * This method must be called before adding frames. In a future version of this class it would be nice if audio
	 * could be inserted at any time (before calling <code>close()</code>), but currently that functionality is not
	 * supported.
	 * 
	 * @param audio the audio to add to this movie, in PCM encoding.
	 * @param startTime the start time (in seconds) of this audio in the movie. For example: if this is 5, then this
	 *            audio will begin 5 seconds into the movie.
	 * @param endTime the end time (in seconds) of this audio in the movie. If the audio would normally last past this
	 *            time: then it is cut off. (If the audio runs out before this time: then this argument has no effect.)
	 * @throws IOException
	 * @throws RuntimeException if you invoke this method after calling <code>addFrame()</code> or <code>close()</code>.
	 */
	public synchronized void addAudioTrack(AudioInputStream audio, float startTime, float endTime) throws IOException {
		if (closed)
			throw new RuntimeException("this writer has already been closed");

		if (videoTrack.isEmpty() == false)
			throw new RuntimeException("cannot add audio after video data has been started");
		AudioTrack newTrack;

		long sampleMin = (long) ((endTime - startTime) * audio.getFormat().getFrameRate());
		if (sampleMin < audio.getFrameLength()) {
			audio = new AudioInputStream(audio, audio.getFormat(), sampleMin);
		}
		newTrack = new AudioTrack(audio, new float[] { startTime }, new float[] { 0 }, new float[] { endTime
				- startTime });

		AudioTrack[] newTracks = new AudioTrack[audioTracks.length + 1];
		System.arraycopy(audioTracks, 0, newTracks, 0, audioTracks.length);
		newTracks[newTracks.length - 1] = newTrack;
		audioTracks = newTracks;

		/**
		 * The QT File Format says: In order to overcome any latencies in sound playback, at least one second of sound
		 * data is placed at the beginning of the interleaved data. This means that the sound and video data are offset
		 * from each other in the file by one second.
		 */
		newTrack.writeAudio(DEFAULT_TIME_SCALE * 1);
	}

	@Override
	protected void finalize() throws Throwable {
		close(false);
	}

	/**
	 * Adds an image to this animation.
	 * <P>
	 * All images must be the same dimensions; if this image is a different size from previously added images an
	 * exception is thrown.
	 * 
	 * @param duration the duration (in seconds) this frame should show. (This value is converted to a timescale of
	 *            DEFAULT_TIME_SCALE.)
	 * @param bi the image to add as a frame.
	 * @param settings an optional map of settings subclasses may use to encode this data. For example, the
	 *            JPEGMovWriter may consult this map to determine the image quality of the JPEG it writes.
	 * @throws IOException
	 */
	public synchronized void addFrame(float duration, Bitmap bi, Map<String, Object> settings) throws IOException {
		if (closed)
			throw new IllegalArgumentException("this writer has already been closed");
		int relativeDuration = (int) (duration * DEFAULT_TIME_SCALE + .5);

		videoTrack.validateSize(bi.getWidth(), bi.getHeight());
		long startPosition = out.getBytesWritten();
		writeFrame(out, bi, settings);
		long byteSize = out.getBytesWritten() - startPosition;
		VideoSample sample = new VideoSample(relativeDuration, out.getBytesWritten() - byteSize, byteSize);
		videoTrack.addSample(sample);
	}

	protected abstract void writeFrame(OutputStream out, Bitmap image, Map<String, Object> settings) throws IOException;

	/**
	 * Adds an image to this animation.
	 * <P>
	 * All images must be the same dimensions; if this image is a different size from previously added images an
	 * exception is thrown.
	 * <P>
	 * This method is provided as a convenient way to quickly merge frames into a movie. It does not, however, type
	 * check the images, or convert images that are not of the correct file type. (For example: if you add TIFF image
	 * files to a MovWriter that expects JPG image files, then no exception will be thrown. But the new mov file will be
	 * unreadable.)
	 * 
	 * @param duration the duration (in seconds) this frame should show. (This value is converted to a timescale of
	 *            DEFAULT_TIME_SCALE.)
	 * @param image the image to add.
	 * @throws IOException
	 */
	public synchronized void addFrame(float duration, File image) throws IOException {
		if (closed)
			throw new IllegalArgumentException("this writer has already been closed");

		int relativeTime = (int) (duration * DEFAULT_TIME_SCALE + .5);
		videoTrack.addFrame(relativeTime, image);
	}

	/**
	 * Subclasses must define the VideoSampleDescriptionEntry this writer uses.
	 */
	protected abstract VideoSampleDescriptionEntry getVideoSampleDescriptionEntry();

	/**
	 * This finishes writing the movie file.
	 * 
	 * @param writeRemainingAudio if true then unfinished AudioInputStreams continue to write to the movie file. If
	 *            false then the movie ends immediately. If an operation is cancelled you should pass false here to
	 *            speed up the time it takes to close everything out.
	 * 
	 * @throws IOException
	 */
	public void close(boolean writeRemainingAudio) throws IOException {
		synchronized (this) {
			if (closed)
				return;
			closed = true;
		}

		long mdatSize;
		try {
			videoTrack.close();
			if (writeRemainingAudio) {
				writeAudioLoop: while (true) {
					boolean audioRemaining = false;
					for (AudioTrack audio : audioTracks) {
						if (audio.writeAudio(DEFAULT_TIME_SCALE))
							audioRemaining = true;
					}
					if (!audioRemaining)
						break writeAudioLoop;
				}
			}

			for (AudioTrack audio : audioTracks) {
				audio.close();
			}

			mdatSize = out.getBytesWritten() - 32; // -32 for ftyp header

			ParentAtom moovRoot = new ParentAtom("moov");

			long totalDuration = videoTrack.totalDuration;
			for (AudioTrack audio : audioTracks) {
				totalDuration = Math.max(totalDuration, audio.getDuration());
			}
			MovieHeaderAtom movieHeader = new MovieHeaderAtom(DEFAULT_TIME_SCALE, totalDuration);
			moovRoot.add(movieHeader);

			videoTrack.writeToMoovRoot(moovRoot, 1);
			for (int a = 0; a < audioTracks.length; a++) {
				audioTracks[a].writeToMoovRoot(moovRoot, a + 2);
			}

			moovRoot.write(out);
		} finally {
			out.close();
		}

		// very last step: we have to rewrite the first
		// 4 bytes of this file now that we can conclusively say
		// how big the "mdat" atom is:

		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(dest, "rw");
			raf.seek(32 + 8); // 32 for ftyp atom; 8 for mdat header
			byte[] array = new byte[8];
			array[0] = (byte) ((mdatSize >> 56) & 0xff);
			array[1] = (byte) ((mdatSize >> 48) & 0xff);
			array[2] = (byte) ((mdatSize >> 40) & 0xff);
			array[3] = (byte) ((mdatSize >> 32) & 0xff);
			array[4] = (byte) ((mdatSize >> 24) & 0xff);
			array[5] = (byte) ((mdatSize >> 16) & 0xff);
			array[6] = (byte) ((mdatSize >> 8) & 0xff);
			array[7] = (byte) (mdatSize & 0xff);
			raf.write(array);
		} finally {
			raf.close();
		}
	}

	/**
	 * Write a file to an OutputStream.
	 * 
	 * @param out the stream to write to.
	 * @param file the file to write
	 * @return the number of bytes written.
	 * @throws IOException
	 */
	protected static synchronized long write(OutputStream out, File file) throws IOException {
		FileInputStream in = null;
		try {
			in = new FileInputStream(file);
			return write(out, in, false);
		} finally {
			try {
				in.close();
			} catch (IOException e) {
			}
		}
	}

	/**
	 * Write the remainder of an InputStream to an OutputStream.
	 * 
	 * @param out the stream to write to.
	 * @param in the data to write
	 * @param whether every two bytes should be switched (to convert from one endian to another)
	 * @return the number of bytes written.
	 * @throws IOException
	 */
	protected static synchronized long write(OutputStream out, InputStream in, boolean reverseBytePairs)
			throws IOException {
		byte[] block = new byte[4096];

		long written = 0;
		int k = read(in, block, block.length);
		if (reverseBytePairs)
			reverseBytePairs(block, k);
		while (k != -1) {
			written += k;
			out.write(block, 0, k);
			k = read(in, block, block.length);
			if (reverseBytePairs)
				reverseBytePairs(block, k);
		}
		return written;
	}

	/**
	 * Write up to a certain number of bytes from an InputStream to an OutputStream.
	 * 
	 * @param out the stream to write to.
	 * @param in the data to write
	 * @param maxBytes the maximum number of bytes to write
	 * @param whether every two bytes should be switched (to convert from one endian to another)
	 * @return the number of bytes written.
	 * @throws IOException
	 */
	protected static synchronized long write(OutputStream out, InputStream in, long maxBytes, boolean reverseBytePairs)
			throws IOException {
		byte[] block = new byte[4096];

		long written = 0;

		if (maxBytes % 2 == 1)
			maxBytes--;

		int k = read(in, block, Math.min(block.length, (int) maxBytes));
		if (reverseBytePairs)
			reverseBytePairs(block, k);
		loop: while (k != -1) {
			written += k;
			out.write(block, 0, k);
			k = read(in, block, Math.min(block.length, (int) (maxBytes - written)));
			if (reverseBytePairs)
				reverseBytePairs(block, k);
			if (written == maxBytes)
				break loop;
		}
		return written;
	}

	/**
	 * Reads bytes from an InputStream. This will always return an even number of bytes.
	 * 
	 * @param bytesToRead
	 * @return
	 */
	private static int read(InputStream in, byte[] dest, int bytesToRead) throws IOException {
		int read = 0;
		if (bytesToRead % 2 == 1)
			bytesToRead--;
		read = in.read(dest, 0, bytesToRead);
		if (read == -1)
			return read;
		while ((read % 2) == 1) {
			int k = in.read(dest, read, bytesToRead - read);
			if (k == -1)
				return read;
			read += k;
		}
		return read;
	}

	private static void reverseBytePairs(byte[] data, int length) {
		if (length == -1)
			return;
		// it is safe to assume length is divisible by 2
		for (int a = 0; a < length - 1; a += 2) {
			byte t = data[a];
			data[a] = data[a + 1];
			data[a + 1] = t;
		}
	}
}
