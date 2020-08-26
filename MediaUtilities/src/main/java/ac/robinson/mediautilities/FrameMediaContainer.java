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

import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;

import ac.robinson.util.IOUtilities;
import androidx.annotation.NonNull;

public class FrameMediaContainer {

	public enum SpanType {
		SPAN_NONE, SPAN_ROOT, SPAN_EXTENSION
	}

	public String mParentId = null;
	public String mFrameId;
	public int mFrameSequenceId;
	public int mFrameMaxDuration = 0; // milliseconds

	public int mBackgroundColour = 0;
	public int mForegroundColour = 0;

	public String mTextContent = null;
	public SpanType mSpanningTextType = SpanType.SPAN_NONE;

	public String mImagePath = null;
	public boolean mImageIsFrontCamera = false;
	public SpanType mSpanningImageType = SpanType.SPAN_NONE;

	// image and text items can simply span or not span; audio is more complex, because we can have both types in one frame
	public ArrayList<Integer> mAudioDurations = new ArrayList<>();
	public ArrayList<String> mAudioPaths = new ArrayList<>();
	public int mSpanningAudioIndex = -1; // only one spanning item per frame; if this is not -1 then that item spans
	public int mSpanningAudioStart = 0; // hint for SMIL/HTML exports to indicate where (ms) audio should start on spanned frames
	public boolean mSpanningAudioRoot = false; // whether this spanning audio item is the first part, or inherited
	public boolean mEndsPreviousSpanningAudio = false; // whether other inherited audio should end here (for SMIL imports)

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
	 * @param fileName      the path to the audio file
	 * @param audioDuration the duration of this audio item, in milliseconds
	 * @return the index in mAudioPaths of the inserted item, or -1 if the item wasn't inserted (due to a missing or
	 * incompatible file)
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
	 * @param textContent   the text to add
	 * @param mediaId       the id of the media item (not currently used)
	 * @param mediaDuration the duration of this text item, in milliseconds
	 */
	public void addTextFromSMIL(String textContent, String mediaId, int mediaDuration, SpanType spanType) {
		if (!TextUtils.isEmpty(textContent) && spanType != SpanType.SPAN_EXTENSION) {
			mTextContent = textContent;
			mSpanningTextType = spanType;
			updateFrameMaxDuration(mediaDuration);
		}
	}

	/**
	 * Add an image or audio item directly to this container from a SMIL import, and update the container's maximum
	 * duration.
	 *
	 * @param mediaType            the type of this media item (the SMIL node's name)
	 * @param mediaFile            the file that contains the media
	 * @param mediaId              the id of the media item (not currently used)
	 * @param mediaDuration        the duration of the item, in milliseconds
	 * @param mediaRegion          the region - applicable to images only; SMIL_FRONT_IMAGE_STRING or SMIL_BACK_IMAGE_STRING
	 * @param spanType             for spanning media, whether this is the original item, or an inherited version
	 * @param endPreviousSpan      whether this item replaces inherited spanning media (currently applies to audio only)
	 * @param validateAudioLengths whether to re-calculate the duration of imported audio items
	 */
	public void addMediaFromSMIL(String mediaType, File mediaFile, String mediaId, int mediaDuration, String mediaRegion,
								 SpanType spanType, boolean endPreviousSpan, boolean validateAudioLengths) {
		if (mediaFile.exists() && mediaFile.length() > 0 && spanType != SpanType.SPAN_EXTENSION) {
			if (SMILUtilities.SMIL_MEDIA_IMAGE.equals(mediaType)) {
				mImagePath = mediaFile.getAbsolutePath();
				if (mediaRegion.startsWith(SMILUtilities.SMIL_FRONT_IMAGE_STRING)) {
					mImageIsFrontCamera = true;
				}
				mSpanningImageType = spanType;
				updateFrameMaxDuration(mediaDuration);

			} else if (SMILUtilities.SMIL_MEDIA_AUDIO.equals(mediaType)) {
				int preciseDuration = mediaDuration;

				// check the audio duration - having the correct audio duration stored used to be critical for correct
				// playback; now it is less of an issue, but it still helps for dividing other media over long-running
				// audio items, so we continue to check lengths where appropriate
				if (validateAudioLengths) {
					int audioDuration = IOUtilities.getAudioFileLength(mediaFile);
					if (audioDuration > 0) {
						preciseDuration = audioDuration;
					}
				}
				int audioIndex = addAudioFile(mediaFile.getAbsolutePath(), preciseDuration);
				if (spanType == SpanType.SPAN_ROOT) {
					mSpanningAudioIndex = audioIndex;
					mSpanningAudioRoot = true;
				}
				mEndsPreviousSpanningAudio = endPreviousSpan;

				updateFrameMaxDuration(preciseDuration);
			}
		}
	}

	@NonNull
	@Override
	public String toString() {
		return "FrameMediaContainer{" + "mParentId='" + mParentId + '\'' + ", mFrameId='" + mFrameId + '\'' +
				", mFrameSequenceId=" + mFrameSequenceId + ", mFrameMaxDuration=" + mFrameMaxDuration + ", mBackgroundColour=" +
				mBackgroundColour + ", mForegroundColour=" + mForegroundColour + ", mTextContent='" + mTextContent + '\'' +
				", mSpanningTextType=" + mSpanningTextType + ", mImagePath='" + mImagePath + '\'' + ", mImageIsFrontCamera=" +
				mImageIsFrontCamera + ", mSpanningImageType=" + mSpanningImageType + ", mAudioDurations=" + mAudioDurations +
				", mAudioPaths=" + mAudioPaths + ", mSpanningAudioIndex=" + mSpanningAudioIndex + ", mSpanningAudioStart=" +
				mSpanningAudioStart + ", mSpanningAudioRoot=" + mSpanningAudioRoot + ", mEndsPreviousSpanningAudio=" +
				mEndsPreviousSpanningAudio + '}';
	}
}
