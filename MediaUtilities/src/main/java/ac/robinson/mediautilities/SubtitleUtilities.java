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

import android.text.TextUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import ac.robinson.util.IOUtilities;

public class SubtitleUtilities {
	/**
	 * For frames that have other media in addition to text (i.e., image, audio tracks), extract the text to a subtitle file
	 * (SRT format), and remove the text from the frame.
	 *
	 * @param contentList  the frames of the narrative to process
	 * @param subtitleFile a file to save subtitle content to
	 * @return true on success; false if there are no subtitles to extract, or extraction fails
	 */
	public static boolean extractTextToSubtitles(ArrayList<FrameMediaContainer> contentList, File subtitleFile) {
		BufferedWriter outputStream = null;
		boolean hasSubtitles = false;
		try {
			int subtitleCount = 0;
			long currentTime = 0;
			outputStream = new BufferedWriter(new FileWriter(subtitleFile));
			for (FrameMediaContainer frame : contentList) {
				if ((frame.mAudioPaths.size() > 0 || frame.mImagePath != null) && !TextUtils.isEmpty(frame.mTextContent)) {
					outputStream.write(subtitleCount + "\n");
					outputStream.write(millisecondsToSrtString(currentTime) + " --> " +
							millisecondsToSrtString(currentTime + frame.mFrameMaxDuration) + "\n");
					outputStream.write(frame.mTextContent + "\n\n");
					subtitleCount += 1;
					frame.mTextContent = null;
				}
				currentTime += frame.mFrameMaxDuration;
				hasSubtitles = true;
			}
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		} finally {
			IOUtilities.closeStream(outputStream);
		}
		return hasSubtitles;
	}

	/**
	 * Convert a time in milliseconds to the format required for SRT subtitles
	 *
	 * @param time the time to format (milliseconds)
	 * @return the SRT-formatted time string; i.e., HH:MM:SS,MMS
	 */
	private static String millisecondsToSrtString(long time) {
		return String.format(Locale.US, "%02d:%02d:%02d,%03d", TimeUnit.MILLISECONDS.toHours(time),
				TimeUnit.MILLISECONDS.toMinutes(time), TimeUnit.MILLISECONDS.toSeconds(time), time % 1000);
	}
}
