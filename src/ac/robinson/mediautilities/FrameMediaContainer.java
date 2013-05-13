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

package ac.robinson.mediautilities;

import java.io.File;
import java.util.ArrayList;

import ac.robinson.util.IOUtilities;

public class FrameMediaContainer {

	// TODO: convert to getters/setters? (slower, so probably not)
	public String mParentId = null;
	public String mFrameId;
	public int mFrameSequenceId;
	public int mFrameMaxDuration = 0; // milliseconds
	public String mTextContent = null;
	public String mImagePath = null;

	public boolean mImageIsFrontCamera = false;
	public boolean mImageIsLandscape = false;

	public ArrayList<Integer> mAudioDurations = new ArrayList<Integer>();
	public ArrayList<String> mAudioPaths = new ArrayList<String>();

	public FrameMediaContainer(String frameId, int frameSequenceId) {
		mFrameId = frameId;
		mFrameSequenceId = frameSequenceId;
	}

	public void updateFrameMaxDuration(int possibleMaxDuration) {
		if (possibleMaxDuration > mFrameMaxDuration) {
			mFrameMaxDuration = possibleMaxDuration;
		}
	}

	public void addText(String textContent, String mediaId, int mediaDuration) {
		mTextContent = textContent;
		updateFrameMaxDuration(mediaDuration);
	}

	public void addAudioFile(String fileName, int audioDuration) {
		mAudioPaths.add(fileName);
		mAudioDurations.add(audioDuration);
	}

	public void addMedia(String mediaType, File mediaFile, String mediaId, int mediaDuration, String mediaRegion,
			boolean validateAudioLengths) {
		if (mediaFile.exists()) {
			if ("img".equals(mediaType)) {
				mImagePath = mediaFile.getAbsolutePath();
				if (mediaRegion.startsWith(SMILUtilities.SMIL_FRONT_IMAGE_STRING)) {
					mImageIsFrontCamera = true;
				}
				updateFrameMaxDuration(mediaDuration);
			} else if ("audio".equals(mediaType)) {

				int preciseDuration = mediaDuration;

				// check the audio duration - having the correct duration is *critical* for narrative playback
				// TODO: fix playback so that this isn't the case?
				if (validateAudioLengths) {
					int audioDuration = IOUtilities.getAudioFileLength(mediaFile);
					if (audioDuration > 0) {
						preciseDuration = audioDuration;
					}
				}
				addAudioFile(mediaFile.getAbsolutePath(), preciseDuration);

				updateFrameMaxDuration(preciseDuration);
			}
		}
	}
}
