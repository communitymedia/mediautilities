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

package ac.robinson.mp4;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class AudioToPCMConverter {
    private final File inputFile;
    private final MediaFormat audioFormat;
    private final String mimeType;

    private int selectedTrackIndex;


    public AudioToPCMConverter(File input) throws IOException {
        inputFile = input;
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(input.getAbsolutePath());

        MediaFormat foundFormat = null;
        String foundMime = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                foundFormat = format;
                foundMime = mime;
                selectedTrackIndex = i;
                break;
            }
        }
        extractor.release();
        if (foundFormat == null) {
            throw new IOException("No audio track found");
        }
        audioFormat = foundFormat;
        mimeType = foundMime;
    }

    public int getSampleRate() {
        return audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                : -1;
    }

    public int getSampleSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return audioFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
                    ? audioFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) : 16; // key not present = 16 bit
        }
        return 16;
    }

    public int getChannelCount() {
        return audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                : 1; // Default to mono if not specified
    }

    public void convertFile(OutputStream output, boolean forceMono) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(inputFile.getAbsolutePath());
        extractor.selectTrack(selectedTrackIndex);

        MediaCodec codec = MediaCodec.createDecoderByType(mimeType);
        codec.configure(audioFormat, null, null, 0);
        codec.start();

        ByteBuffer[] inputBuffers = codec.getInputBuffers();
        ByteBuffer[] outputBuffers = codec.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        boolean inputEOS = false;
        boolean outputEOS = false;
        int channelCount = getChannelCount();

        while (!outputEOS) {
            if (!inputEOS) {
                int inputBufferId = codec.dequeueInputBuffer(10000);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferId];
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        codec.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }
            }

            int outputBufferId = codec.dequeueOutputBuffer(info, 10000);
            if (outputBufferId >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferId];
                byte[] chunk = new byte[info.size];
                outputBuffer.get(chunk);
                outputBuffer.clear();

                if (forceMono && channelCount == 2) {
                    // Downmix stereo to mono (16-bit PCM)
                    byte[] monoChunk = new byte[chunk.length / 2];
                    for (int i = 0, j = 0; i < chunk.length; i += 4, j += 2) {
                        // Little-endian: [L0][L1][R0][R1]
                        int left = (chunk[i + 1] << 8) | (chunk[i] & 0xFF);
                        int right = (chunk[i + 3] << 8) | (chunk[i + 2] & 0xFF);
                        int mono = (left + right) / 2;
                        monoChunk[j] = (byte) (mono & 0xFF);
                        monoChunk[j + 1] = (byte) ((mono >> 8) & 0xFF);
                    }
                    output.write(monoChunk);
                } else {
                    output.write(chunk);
                }

                codec.releaseOutputBuffer(outputBufferId, false);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputEOS = true;
                }
            }
        }

        codec.stop();
        codec.release();
        extractor.release();
    }
}