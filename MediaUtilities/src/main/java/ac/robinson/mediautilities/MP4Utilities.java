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

import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

import ac.robinson.mp4.MP4Encoder;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MP4Utilities {

	private static final String LOG_TAG = "MP4Utilities";

	public static ArrayList<Uri> generateNarrativeMP4(Resources res, File outputFile, ArrayList<FrameMediaContainer> framesToSend,
													  Map<Integer, Object> settings) {

		ArrayList<Uri> filesToSend = new ArrayList<>();
		if (framesToSend == null || framesToSend.size() <= 0) {
			return filesToSend;
		}
		boolean fileError;

		// should really do proper checking on these
		final int audioResamplingRate = (Integer) settings.get(MediaUtilities.KEY_RESAMPLE_AUDIO);

		ArrayList<File> filesToDelete = new ArrayList<>();

		try {
			AudioUtilities.CombinedAudioTrack resampledAudioTrack = AudioUtilities.createCombinedNarrativeAudioTrack(framesToSend,
					audioResamplingRate, outputFile.getParentFile());

			MP4Encoder mp4Encoder = new MP4Encoder();
			fileError = !mp4Encoder.createMP4(res, outputFile, framesToSend, resampledAudioTrack, settings);

			filesToDelete.addAll(resampledAudioTrack.mTemporaryFilesToDelete);

		} catch (Throwable t) {
			fileError = true; // these are the only places where errors really matter
			Log.d(LOG_TAG, "Error creating MP4 file - Throwable: " + t.getLocalizedMessage());
		}

		// deletion must be *after* creation because audio and video are interleaved in the MP4 output
		for (File file : filesToDelete) {
			if (file != null && file.exists()) {
				Log.d(LOG_TAG, "Deleting temporary file " + file.getAbsolutePath());
				file.delete();
			}
		}

		if (!fileError) {
			filesToSend.add(Uri.fromFile(outputFile));
			return filesToSend;
		}

		//noinspection RedundantOperationOnEmptyContainer
		filesToSend.clear();
		return filesToSend;
	}
}
