/*
 *  Copyright (C) 2020 Simon Robinson
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

package ac.robinson.mediautilities;

import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;

import com.bric.audio.AudioFormat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import ac.robinson.mov.MP3toPCMConverter;
import ac.robinson.mov.MP4toPCMConverter;
import ac.robinson.mov.WAVtoPCMConverter;
import ac.robinson.mp4.AudioToPCMConverter;
import ac.robinson.util.AndroidUtilities;
import ac.robinson.util.IOUtilities;
import vavi.sound.pcm.resampling.ssrc.SSRC;

public class AudioUtilities {

	private static final String LOG_TAG = "AudioUtilities";

	public enum AudioType {NONE, M4A, MP3, WAV}

	public static class CombinedAudioTrack {
		public File mCombinedPCMFile;
		public AudioFormat mCombinedPCMAudioFormat;
		long mCombinedPCMDurationMs;
		ArrayList<File> mTemporaryFilesToDelete; // must be deleted *after* we actually use the PCM file
	}

	static CombinedAudioTrack createCombinedNarrativeAudioTrack(ArrayList<FrameMediaContainer> framesToSend, int sampleRate,
																File tempDirectory) {

		Log.d(LOG_TAG, "Exporting combined audio (" + sampleRate + ")");
		CombinedAudioTrack exportedTrack = new CombinedAudioTrack();

		// the list of files to be deleted after they've been written to the movie
		ArrayList<File> filesToDelete = new ArrayList<>();

		// see how many tracks we need to create - find the maximum number of (compatible) audio items per frame
		int trackCount = 0;
		boolean automaticSampleRate = sampleRate == -1;
		final SparseIntArray fileSampleRates = new SparseIntArray();
		for (FrameMediaContainer frame : framesToSend) {
			int localCount = 0;
			int audioId = -1;

			// workaround for a bug where spanning audio items are not the first item in the list, meaning the longer
			// duration of a spanning audio item leads to a negative calculated duration and, as a result, a failure to
			// export; the solution is simply to move any spanning items to the front of the list
			if (frame.mSpanningAudioIndex >= 0) {
				String spanningAudioPath = frame.mAudioPaths.remove(frame.mSpanningAudioIndex);
				frame.mAudioPaths.add(0, spanningAudioPath);
				int spanningAudioDuration = frame.mAudioDurations.remove(frame.mSpanningAudioIndex);
				frame.mAudioDurations.add(0, spanningAudioDuration);
				frame.mSpanningAudioIndex = 0;
			}

			for (String type : frame.mAudioPaths) {
				audioId += 1;
				int audioDuration = frame.mAudioDurations.get(audioId);
				String actualFileExtension = IOUtilities.getFileExtension(type);
				File inputAudioFile = new File(type);
				final String fileExtension; // use the base file extension instead of the actual - combine same types
				if (AndroidUtilities.arrayContains(MediaUtilities.M4A_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.M4A_FILE_EXTENSIONS[0];
					if (automaticSampleRate) {
						RandomAccessFile inputRandomAccessFile = null;
						try {
							inputRandomAccessFile = new RandomAccessFile(inputAudioFile, "r");
							MP4toPCMConverter pcmConverter = new MP4toPCMConverter(inputRandomAccessFile);
							fileSampleRates.put(pcmConverter.getSampleRate(),
									audioDuration + fileSampleRates.get(pcmConverter.getSampleRate(), 0));
							Log.d(LOG_TAG, "M4A type: " + pcmConverter.getSampleRate() + ", " + audioDuration);
						} catch (Exception ignored) {
						} finally {
							IOUtilities.closeStream(inputRandomAccessFile);
						}
					}

				} else if (AndroidUtilities.arrayContains(MediaUtilities.MP3_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.MP3_FILE_EXTENSIONS[0];
					if (automaticSampleRate) {
						try {
							MP3toPCMConverter.MP3Configuration mp3Config = new MP3toPCMConverter.MP3Configuration();
							MP3toPCMConverter.getFileConfig(inputAudioFile, mp3Config);
							fileSampleRates.put(mp3Config.sampleFrequency,
									audioDuration + fileSampleRates.get(mp3Config.sampleFrequency, 0));
							Log.d(LOG_TAG, "MP3 type: " + mp3Config.sampleFrequency + ", " + audioDuration);
						} catch (Exception ignored) {
						}
					}

				} else if (AndroidUtilities.arrayContains(MediaUtilities.WAV_FILE_EXTENSIONS, actualFileExtension)) {
					fileExtension = MediaUtilities.WAV_FILE_EXTENSIONS[0];
					if (automaticSampleRate) {
						try {
							WAVtoPCMConverter.WAVConfiguration wavConfig = new WAVtoPCMConverter.WAVConfiguration();
							WAVtoPCMConverter.getFileConfig(inputAudioFile, wavConfig);
							fileSampleRates.put(wavConfig.sampleFrequency,
									audioDuration + fileSampleRates.get(wavConfig.sampleFrequency, 0));
							Log.d(LOG_TAG, "WAV type: " + wavConfig.sampleFrequency + ", " + audioDuration);
						} catch (Exception ignored) {
						}
					}

				} else {
					fileExtension = actualFileExtension;
				}
				if (AndroidUtilities.arrayContains(MediaUtilities.MOV_AUDIO_FILE_EXTENSIONS, fileExtension)) {
					localCount += 1;
				}
			}
			trackCount = Math.max(trackCount, localCount);
		}

		if (trackCount == 0) {
			exportedTrack.mTemporaryFilesToDelete = filesToDelete;
			return exportedTrack; // no audio present - nothing to do
		}

		// find the most common sample rate
		if (automaticSampleRate) {
			ArrayList<Integer> sortedSampleRates = new ArrayList<>();
			for (int i = 0, n = fileSampleRates.size(); i < n; i++) {
				sortedSampleRates.add(fileSampleRates.keyAt(i));
			}
			Collections.sort(sortedSampleRates, (i1, i2) -> {
				return fileSampleRates.get(i1) > fileSampleRates.get(i2) ? -1 : 1; // if equal, just pick any
			});

			sampleRate = sortedSampleRates.get(0);
			Log.d(LOG_TAG, "Chosen most common sample rate: " + sampleRate + " (" + fileSampleRates.get(sampleRate) + " ms)");
		}

		// all audio parts are combined into one track with these properties
		AudioFormat globalAudioFormat = new AudioFormat(sampleRate, 16, 1, true, false);

		// prepare the global audio track
		boolean globalAudioWritten = false;
		long globalAudioDuration = 0;
		File globalPCMFile;
		try {
			globalPCMFile = File.createTempFile("export", "all.pcm", tempDirectory);
		} catch (IOException e) {
			exportedTrack.mTemporaryFilesToDelete = filesToDelete;
			return exportedTrack; // not much else we can do
		}
		filesToDelete.add(globalPCMFile); // deleted after the rest of the movie has been written

		// create a separate temporary PCM file for each parallel audio track
		File[] pcmFiles = new File[trackCount];
		long[] pcmFileDurations = new long[trackCount];
		for (int i = 0; i < pcmFiles.length; i++) {
			BufferedOutputStream outputPCMStream = null;
			try {
				pcmFiles[i] = File.createTempFile("export-" + i, ".pcm", tempDirectory);
				outputPCMStream = new BufferedOutputStream(new FileOutputStream(pcmFiles[i]));
			} catch (Exception e) {
				IOUtilities.closeStream(outputPCMStream);
				if (pcmFiles[i] != null) {
					pcmFiles[i].delete();
				}
				pcmFiles[i] = null;
				Log.d(LOG_TAG, "Error creating combined MOV audio track - couldn't create stream " + i + " PCM file");
				continue;
			}

			File inputAudioFile;
			boolean audioWritten = false;
			File currentPCMFile = null;
			BufferedOutputStream currentPCMStream = null;
			File temporaryPCMFile = null;
			BufferedInputStream temporaryPCMInputStream = null;
			BufferedOutputStream temporaryPCMOutputStream = null;

			// get the available tracks of the right type from each frame, then remove when done
			long frameStartTime = 0;
			for (FrameMediaContainer frame : framesToSend) {

				boolean audioFound = false;
				boolean decodingError = false;
				AudioUtilities.AudioType currentAudioType = AudioUtilities.AudioType.NONE;

				int audioId = -1;
				for (String audioPath : frame.mAudioPaths) {
					audioId += 1;

					// don't need to add inherited spanning audio items - they've already been processed
					if (frame.mSpanningAudioIndex == audioId && !frame.mSpanningAudioRoot) {
						frame.mSpanningAudioIndex = -1; // this frame no longer has any spanning audio
						audioFound = true;
						break; // no more items in this frame can be added, as the spanning item overlaps them
					}

					String audioFileExtension = IOUtilities.getFileExtension(audioPath);
					if (!AndroidUtilities.arrayContains(MediaUtilities.MOV_AUDIO_FILE_EXTENSIONS, audioFileExtension)) {
						continue; // skip incompatible files
					}

					// only use tracks of the right extension TODO: pick the longest track instead of the first one?
					if (AndroidUtilities.arrayContains(MediaUtilities.M4A_FILE_EXTENSIONS, audioFileExtension)) {
						currentAudioType = AudioType.M4A;
					} else if (AndroidUtilities.arrayContains(MediaUtilities.MP3_FILE_EXTENSIONS, audioFileExtension)) {
						currentAudioType = AudioType.MP3;
					} else if (AndroidUtilities.arrayContains(MediaUtilities.WAV_FILE_EXTENSIONS, audioFileExtension)) {
						currentAudioType = AudioType.WAV;
					} else {
						continue;
					}

					Log.d(LOG_TAG, "Processing " + audioPath);

					// if we get here it's the right type of audio, so begin to get PCM from the compressed source
					inputAudioFile = new File(audioPath);
					audioFound = true;

					// create temporary files per-track, so that one corrupt track doesn't break everything
					try {
						currentPCMFile = File.createTempFile(inputAudioFile.getName(), ".pcm", tempDirectory);
						currentPCMStream = new BufferedOutputStream(new FileOutputStream(currentPCMFile));
					} catch (Exception e) {
						IOUtilities.closeStream(currentPCMStream);
						if (currentPCMFile != null) {
							currentPCMFile.delete();
						}
						Log.d(LOG_TAG,
								"Error creating combined MOV audio track - couldn't create individual " + audioFileExtension +
										" files");
						continue;
					}

					// begin to convert the compressed audio
					if (currentAudioType == AudioType.M4A) {
						RandomAccessFile inputRandomAccessFile = null;
						try {
							// first we need to extract PCM audio from the M4A file - use the native methods if present
							// output from PCM converter is mono signed little-endian integers
							int pcmSampleRate = -1;
							int pcmSampleSize = -1;
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
								AudioToPCMConverter pcmConverter = new AudioToPCMConverter(inputAudioFile);
								pcmConverter.convertFile(currentPCMStream, true);
								pcmSampleRate = pcmConverter.getSampleRate();
								pcmSampleSize = pcmConverter.getSampleSize();
							} else {
								inputRandomAccessFile = new RandomAccessFile(inputAudioFile, "r");
								MP4toPCMConverter pcmConverter = new MP4toPCMConverter(inputRandomAccessFile);
								pcmConverter.convertFile(currentPCMStream, true);
								pcmSampleRate = pcmConverter.getSampleRate();
								pcmSampleSize = pcmConverter.getSampleSize();
							}

							// if the sample rate or sample size don't match our output, use SSRC to resample the audio
							if (pcmSampleRate != globalAudioFormat.getSampleRate() ||
									pcmSampleSize != globalAudioFormat.getSampleSizeInBits()) {

								Log.d(LOG_TAG, "Resampling M4A audio");
								try {
									temporaryPCMInputStream = new BufferedInputStream(new FileInputStream(currentPCMFile));
									temporaryPCMFile = File.createTempFile(inputAudioFile.getName(), ".temp.pcm", tempDirectory);
									temporaryPCMOutputStream = new BufferedOutputStream(new FileOutputStream(temporaryPCMFile));

									// use SSRC to resample PCM audio - note that two passes are required for accuracy
									new SSRC(tempDirectory, temporaryPCMInputStream, temporaryPCMOutputStream,
											ByteOrder.LITTLE_ENDIAN, pcmSampleRate, (int) globalAudioFormat.getSampleRate(),
											pcmSampleSize, globalAudioFormat.getSampleSizeInBits(),
											1, currentPCMFile.length(), 0, 0, 0, true,
											false, false, true);

									// this is now the PCM file to use
									if (currentPCMFile != null) {
										currentPCMFile.delete();
									}
									currentPCMFile = temporaryPCMFile;

								} catch (Exception e) {
									if (temporaryPCMFile != null) {
										temporaryPCMFile.delete();
									}
								} finally {
									IOUtilities.closeStream(temporaryPCMInputStream);
									IOUtilities.closeStream(temporaryPCMOutputStream);
								}
							}

							Log.d(LOG_TAG, "Outputting M4A: " + globalAudioFormat.getSampleRate() + ", " +
									globalAudioFormat.getSampleSizeInBits() + " from " + pcmSampleSize + "," +
									" " + pcmSampleSize);
						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating combined M4A audio track: " + e.getLocalizedMessage());
						} finally {
							IOUtilities.closeStream(inputRandomAccessFile);
						}

					} else if (currentAudioType == AudioType.MP3) {
						try {
							// first we need to extract PCM audio from the MP3 file
							// output from PCM converter is mono signed 16-bit little-endian integers
							MP3toPCMConverter.MP3Configuration mp3Config = new MP3toPCMConverter.MP3Configuration();
							MP3toPCMConverter.convertFile(inputAudioFile, currentPCMStream, mp3Config);

							// if the sample rate or sample size don't match our output, use SSRC to resample the audio
							if (mp3Config.sampleFrequency != globalAudioFormat.getSampleRate() ||
									mp3Config.sampleSize != globalAudioFormat.getSampleSizeInBits()) {

								Log.d(LOG_TAG, "Resampling MP3 audio");
								try {
									temporaryPCMInputStream = new BufferedInputStream(new FileInputStream(currentPCMFile));
									temporaryPCMFile = File.createTempFile(inputAudioFile.getName(), ".temp.pcm", tempDirectory);
									temporaryPCMOutputStream = new BufferedOutputStream(new FileOutputStream(temporaryPCMFile));

									// use SSRC to resample PCM audio - note that two passes are required for accuracy
									new SSRC(tempDirectory, temporaryPCMInputStream, temporaryPCMOutputStream,
											ByteOrder.LITTLE_ENDIAN, mp3Config.sampleFrequency,
											(int) globalAudioFormat.getSampleRate(), mp3Config.sampleSize,
											globalAudioFormat.getSampleSizeInBits(), 1, currentPCMFile.length(), 0, 0, 0, true,
											false, false, true);

									// this is now the PCM file to use
									if (currentPCMFile != null) {
										currentPCMFile.delete();
									}
									currentPCMFile = temporaryPCMFile;

								} catch (Exception e) {
									if (temporaryPCMFile != null) {
										temporaryPCMFile.delete();
									}
								} finally {
									IOUtilities.closeStream(temporaryPCMInputStream);
									IOUtilities.closeStream(temporaryPCMOutputStream);
								}
							}

							Log.d(LOG_TAG, "Outputting MP3: " + globalAudioFormat.getSampleRate() + ", " +
									globalAudioFormat.getSampleSizeInBits() + " from " + mp3Config.sampleFrequency + ", " +
									mp3Config.sampleSize);

						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating combined MP3 audio track: " + e.getLocalizedMessage());
						}

					} else if (currentAudioType == AudioType.WAV) {
						try {
							// first we need to extract PCM audio from the WAV file
							// output from PCM converter is mono signed little-endian integers
							WAVtoPCMConverter.WAVConfiguration wavConfig = new WAVtoPCMConverter.WAVConfiguration();
							WAVtoPCMConverter.convertFile(inputAudioFile, currentPCMStream, wavConfig);

							// if the sample rate or sample size don't match our output, use SSRC to resample the audio
							if (wavConfig.sampleFrequency != globalAudioFormat.getSampleRate() ||
									wavConfig.sampleSize != globalAudioFormat.getSampleSizeInBits()) {

								Log.d(LOG_TAG, "Resampling WAV audio");
								try {
									temporaryPCMInputStream = new BufferedInputStream(new FileInputStream(currentPCMFile));
									temporaryPCMFile = File.createTempFile(inputAudioFile.getName(), ".temp.pcm", tempDirectory);
									temporaryPCMOutputStream = new BufferedOutputStream(new FileOutputStream(temporaryPCMFile));

									// use SSRC to resample PCM audio - note that two passes are required for accuracy
									new SSRC(tempDirectory, temporaryPCMInputStream, temporaryPCMOutputStream,
											ByteOrder.LITTLE_ENDIAN, wavConfig.sampleFrequency,
											(int) globalAudioFormat.getSampleRate(), wavConfig.sampleSize,
											globalAudioFormat.getSampleSizeInBits(), 1, currentPCMFile.length(), 0, 0, 0, true,
											false, false, true);

									// this is now the PCM file to use
									if (currentPCMFile != null) {
										currentPCMFile.delete();
									}
									currentPCMFile = temporaryPCMFile;

								} catch (Exception e) {
									if (temporaryPCMFile != null) {
										temporaryPCMFile.delete();
									}
								} finally {
									IOUtilities.closeStream(temporaryPCMInputStream);
									IOUtilities.closeStream(temporaryPCMOutputStream);
								}
							}

							Log.d(LOG_TAG, "Outputting WAV: " + globalAudioFormat.getSampleRate() + ", " +
									globalAudioFormat.getSampleSizeInBits() + " from " + wavConfig.sampleFrequency + ", " +
									wavConfig.sampleSize);

						} catch (Exception e) {
							decodingError = true;
							Log.d(LOG_TAG, "Error creating combined WAV audio track: " + e.getLocalizedMessage());
						}
					}

					// if successful, combine the streams and store locations
					IOUtilities.closeStream(currentPCMStream);
					if (!decodingError) {

						// pad any gaps in audio (i.e., frames that don't have sound) with silence
						// TODO: will we ever overflow Long.MAX_VALUE with this?
						long silenceNeeded =
								((long) globalAudioFormat.getSampleRate() * (long) (globalAudioFormat.getSampleSizeInBits() / 8) *
										(frameStartTime - pcmFileDurations[i])) / 1000;
						if (silenceNeeded % 2 != 0) {
							silenceNeeded += 1; // must be an even number: two bytes for each sample
						}
						Log.d(LOG_TAG, "Adding " + silenceNeeded + " samples of silence");

						// copy from input file to pcmStream - don't use IOUtilities so we can keep the stream open
						InputStream inputStream = null;
						try {
							// fill with silence to span the gap in audio files - first pad to multiple of buffer
							// (int cast is fine as long as IOUtilities.IO_BUFFER_SIZE is less than Integer.MAX_VALUE)
							int remainingSamples = (int) (silenceNeeded % IOUtilities.IO_BUFFER_SIZE);
							byte[] buffer = new byte[remainingSamples];
							outputPCMStream.write(buffer, 0, remainingSamples);
							buffer = new byte[IOUtilities.IO_BUFFER_SIZE];
							for (int s = 0; s < silenceNeeded / IOUtilities.IO_BUFFER_SIZE; s++) {
								outputPCMStream.write(buffer, 0, IOUtilities.IO_BUFFER_SIZE);
							}

							// now add the new audio data
							int numSamples;
							inputStream = new FileInputStream(currentPCMFile);
							while ((numSamples = inputStream.read(buffer)) > 0) {
								outputPCMStream.write(buffer, 0, numSamples);
							}

							audioWritten = true;
						} catch (IOException e) {
							Log.d(LOG_TAG, "Error creating combined MOV audio track - combining failed");
						} finally {
							IOUtilities.closeStream(inputStream);
						}

						pcmFileDurations[i] = frameStartTime + frame.mAudioDurations.get(audioId);
					}
					if (currentPCMFile != null) {
						currentPCMFile.delete();
					}

					break; // we're done with this frame - we only ever add one audio track to the stream per frame
				}

				// we've processed this file (any error that occurred is irrelevant now - remove track anyway)
				if (audioFound) {
					frame.mAudioPaths.remove(audioId);
					frame.mAudioDurations.remove(audioId);
				}

				// move on to the next frame's start time
				frameStartTime += frame.mFrameMaxDuration;
			}

			IOUtilities.closeStream(outputPCMStream);

			globalAudioWritten |= audioWritten;
			globalAudioDuration = Math.max(pcmFileDurations[i], globalAudioDuration);
		}

		// finally, create the combined PCM stream for writing to the exported movie file
		if (globalAudioWritten) { // only write if at least one part of the stream succeeded

			// remove any streams that had errors
			File[] nonNullPCMFiles = new File[pcmFiles.length];
			int arrayIndex = 0;
			for (File file : pcmFiles) {
				if (file != null) {
					nonNullPCMFiles[arrayIndex] = file;
					filesToDelete.add(file);
					arrayIndex += 1;
				}
			}
			if (arrayIndex < pcmFiles.length) {
				Log.d(LOG_TAG, "Error stream found - trimming length to " + arrayIndex);
				pcmFiles = Arrays.copyOf(nonNullPCMFiles, arrayIndex);
			}

			// if we have parallel audio, average the streams
			if (pcmFiles.length > 1) {

				// TODO: a better way might be to intelligently combine (e.g., pick one stream when others are blank)
				// pad files with silence so they are all the same length - this simplifies combining
				long totalBytes = 0;
				for (File file : pcmFiles) {
					totalBytes = Math.max(totalBytes, file.length());
				}

				boolean streamLengthsNormalised = true;
				for (int i = 0; i < pcmFiles.length; i++) {
					if (pcmFiles[i].length() < totalBytes) {
						// fill with silence to span the gap in audio files - first pad to multiple of buffer
						// (int cast is fine as long as IOUtilities.IO_BUFFER_SIZE is less than Integer.MAX_VALUE)
						long silenceNeeded = totalBytes - pcmFiles[i].length();
						Log.d(LOG_TAG, "Extending stream " + i + " with " + silenceNeeded + " samples of silence");
						BufferedOutputStream silencePCMStream = null;
						try {
							silencePCMStream = new BufferedOutputStream(new FileOutputStream(pcmFiles[i], true));
							int remainingSamples = (int) (silenceNeeded % IOUtilities.IO_BUFFER_SIZE);
							byte[] buffer = new byte[remainingSamples];
							silencePCMStream.write(buffer, 0, remainingSamples);
							buffer = new byte[IOUtilities.IO_BUFFER_SIZE];
							for (int s = 0; s < silenceNeeded / IOUtilities.IO_BUFFER_SIZE; s++) {
								silencePCMStream.write(buffer, 0, IOUtilities.IO_BUFFER_SIZE);
							}
						} catch (Exception e) {
							streamLengthsNormalised = false;
							globalPCMFile.delete(); // delete the existing global file, and fall back to track 1
							globalPCMFile = pcmFiles[0];
							Log.d(LOG_TAG, "Error creating combined MOV audio track - couldn't normalise stream " + "lengths");
							break;
						} finally {
							IOUtilities.closeStream(silencePCMStream);
						}
					}
				}

				// average the 2 or 3 streams
				if (streamLengthsNormalised) {
					Log.d(LOG_TAG, "Stream lengths normalised; averaging " + pcmFiles.length + ": " + totalBytes);
					InputStream[] pcmStreams = new InputStream[pcmFiles.length];
					byte[][] streamBuffers = new byte[pcmFiles.length][];
					int[] streamOffsets = new int[pcmFiles.length];
					int[] streamBytesRead = new int[pcmFiles.length];
					short[] streamAverages = new short[pcmFiles.length];
					BufferedOutputStream outputPCMStream = null;
					try {
						for (int i = 0; i < pcmStreams.length; i++) {
							pcmStreams[i] = new FileInputStream(pcmFiles[i]);
							streamBuffers[i] = new byte[IOUtilities.IO_BUFFER_SIZE];
						}
						outputPCMStream = new BufferedOutputStream(new FileOutputStream(globalPCMFile));

						// read the same size buffer from each file, averaging over all 2 or 3 of them
						int totalBytesRead = 0;
						int bytesToWrite;
						short averageValue;
						while (totalBytesRead < totalBytes) {
							for (int i = 0; i < pcmStreams.length; i++) {
								// see: https://goo.gl/1k7cZ1
								while ((streamBytesRead[i] = pcmStreams[i].read(streamBuffers[i], streamOffsets[i],
										streamBuffers[i].length - streamOffsets[i])) != -1) {
									streamOffsets[i] += streamBytesRead[i];
									if (streamOffsets[i] >= streamBuffers[i].length) {
										break;
									}
								}
							}

							totalBytesRead += streamOffsets[0];
							bytesToWrite = Integer.MAX_VALUE;
							for (int i = 0; i < pcmStreams.length; i++) {
								bytesToWrite = Math.min(bytesToWrite, streamOffsets[i]);
							}

							// average the 2 or 3 buffers, then write to the global output stream
							for (int i = 0; i < bytesToWrite; i += 2) {
								for (int s = 0; s < pcmStreams.length; s++) {
									streamAverages[s] = (short) (((streamBuffers[s][i + 1] & 0xff) << 8) |
											(streamBuffers[s][i] & 0xff));
								}

								// TODO: if we ever make the number of streams flexible, this will need updating
								// see: https://stackoverflow.com/questions/3816446/
								if (pcmStreams.length == 3) {
									averageValue = (short) ((streamAverages[0] >> 1) + (streamAverages[1] >> 1) +
											(streamAverages[2] >> 1) +
											(streamAverages[0] & streamAverages[1] & streamAverages[2] & 0x1));
								} else {
									averageValue = (short) ((streamAverages[0] >> 1) + (streamAverages[1] >> 1) +
											(streamAverages[0] & streamAverages[1] & 0x1));
								}

								// our output is little-endian
								outputPCMStream.write(averageValue & 0xff);
								outputPCMStream.write((averageValue >> 8) & 0xff);
							}

							// shift any bytes between bytesToWrite and streamOffsets to start of buffer, then repeat
							for (int i = 0; i < pcmStreams.length; i++) {
								if (streamOffsets[i] > bytesToWrite) {
									Log.d(LOG_TAG, "Correcting offset read: " + (streamOffsets[i] - bytesToWrite));
									System.arraycopy(streamBuffers, bytesToWrite, streamBuffers, 0,
											streamOffsets[i] - bytesToWrite);
									streamOffsets[i] -= bytesToWrite;
								} else {
									streamOffsets[i] = 0;
								}
							}
						}

					} catch (Exception e) {
						globalPCMFile.delete(); // delete the existing global file, and fall back to track 1
						globalPCMFile = pcmFiles[0];
						Log.d(LOG_TAG, "Error creating combined MOV audio track - couldn't create combined stream");
					} finally {
						for (InputStream pcmStream : pcmStreams) {
							IOUtilities.closeStream(pcmStream);
						}
						IOUtilities.closeStream(outputPCMStream);
					}
				}

			} else {
				globalPCMFile.delete(); // delete the existing global file, and just use our first stream
				globalPCMFile = pcmFiles[0]; // most of the time we have only one track
			}

			exportedTrack.mCombinedPCMFile = globalPCMFile;
			exportedTrack.mCombinedPCMAudioFormat = globalAudioFormat;
			exportedTrack.mCombinedPCMDurationMs = globalAudioDuration;
		}

		exportedTrack.mTemporaryFilesToDelete = filesToDelete;
		return exportedTrack;
	}
}
