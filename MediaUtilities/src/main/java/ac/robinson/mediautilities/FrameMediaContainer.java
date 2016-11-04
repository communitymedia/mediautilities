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
import android.text.TextUtils;

public class FrameMediaContainer {

	public String mParentId = null;
	public String mFrameId;
	public int mFrameSequenceId;
	public int mFrameMaxDuration = 0; // milliseconds

	public String mTextContent = null;
	public int mTextDuration = -1; // milliseconds, only set if the user has set a specific duration for this item

	public String mImagePath = null;
	public int mImageDuration = -1; // milliseconds, only set if the user has set a specific duration for this item

	public boolean mImageIsFrontCamera = false;
	public boolean mImageIsLandscape = false;

	public ArrayList<Integer> mAudioDurations = new ArrayList<Integer>();
	public ArrayList<String> mAudioPaths = new ArrayList<String>();
	public int mSpanningAudioIndex = -1; // only one spanning item per frame; if this is not -1 then that item spans
	public boolean mSpanningAudioRoot = false; // whether this spanning audio item is the first part, or inherited

	public FrameMediaContainer(String frameId, int frameSequenceId) {
		mFrameId = frameId;
		mFrameSequenceId = frameSequenceId;
	}

	public void updateFrameMaxDuration(int possibleMaxDuration) {
		if (possibleMaxDuration > mFrameMaxDuration) {
			mFrameMaxDuration = possibleMaxDuration;
		}
	}

	/**
	 * Add an audio file to this container. Because audio items need to store a duration, they are added here; text and
	 * images are added directly in the public member variables.
	 * 
	 * @param fileName the path to the audio file
	 * @param audioDuration the duration of this audio item, in milliseconds
	 * @return the index in mAudioPaths of the inserted item, or -1 if the item wasn't inserted (due to a missing or
	 *         incompatible file)
	 */
	public int addAudioFile(String fileName, int audioDuration) {
		File mediaFile = new File(fileName);
		if (mediaFile.exists() && mediaFile.length() > 0) {
			mAudioPaths.add(fileName);
			mAudioDurations.add(audioDuration);
			return mAudioPaths.size() - 1;
		}
		return -1;
	}

	/**
	 * Add text directly to this container from a SMIL import, and update the container's maximum duration.
	 * 
	 * @param textContent the text to add
	 * @param mediaId the id of the media item (not currently used)
	 * @param mediaDuration the duration of this text item, in milliseconds
	 */
	public void addTextFromSMIL(String textContent, String mediaId, int mediaDuration) {
		if (!TextUtils.isEmpty(textContent)) {
			mTextContent = textContent;
			updateFrameMaxDuration(mediaDuration);
		}
	}

	/**
	 * Add an image or audio item directly to this container from a SMIL import, and update the container's maximum
	 * duration.
	 * 
	 * @param mediaType the type of this media item (the SMIL node's name)
	 * @param mediaFile the file that contains the media
	 * @param mediaId the id of the media item (not currently used)
	 * @param mediaDuration the duration of the item, in milliseconds
	 * @param mediaRegion the region - applicable to images only; SMIL_FRONT_IMAGE_STRING or SMIL_BACK_IMAGE_STRING
	 * @param validateAudioLengths whether to re-calculate the duration of imported audio items
	 */
	public void addMediaFromSMIL(String mediaType, File mediaFile, String mediaId, int mediaDuration,
			String mediaRegion, boolean validateAudioLengths) {
		if (mediaFile.exists() && mediaFile.length() > 0) {
			if ("img".equals(mediaType)) {
				mImagePath = mediaFile.getAbsolutePath();
				if (mediaRegion.startsWith(SMILUtilities.SMIL_FRONT_IMAGE_STRING)) {
					mImageIsFrontCamera = true;
				}
				updateFrameMaxDuration(mediaDuration);
			} else if ("audio".equals(mediaType)) {

				int preciseDuration = mediaDuration;

				// check the audio duration - having the correct audio duration stored used to be critical for correct
				// playback; now it is less important, but it still helps for dividing other media over long-running
				// audio items, so we continue to check lengths where appropriate
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
