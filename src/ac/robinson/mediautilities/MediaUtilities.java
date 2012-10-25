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
import java.io.FileInputStream;
import java.util.ArrayList;

import ac.robinson.util.IOUtilities;
import ac.robinson.util.StringUtilities;
import android.media.MediaPlayer;
import android.text.TextUtils;

public class MediaUtilities {

	public static final String APPLICATION_NAME = "mediautilities";
	public static final String LOG_TAG = APPLICATION_NAME;

	// file extensions (including dots)
	public static final String SMIL_FILE_EXTENSION = ".smil";
	public static final String SYNC_FILE_EXTENSION = ".sync.jpg"; // to counter ridiculous incoming filename filtering
	public static final String HTML_FILE_EXTENSION = ".html";
	public static final String MOV_FILE_EXTENSION = ".mp4";

	public static final String MOV_AUDIO_FILE_EXTENSION = ".m4a";
	public static final String MOV_IMAGE_FILE_EXTENSION = ".jpg";

	// message IDs (see: http://stackoverflow.com/questions/3432649/)
	public static final int MSG_RECEIVED_SMIL_FILE = 1;
	public static final int MSG_RECEIVED_HTML_FILE = 2;
	public static final int MSG_RECEIVED_MOV_FILE = 3;
	public static final int MSG_RECEIVED_IMPORT_FILE = 4;

	public static final int MSG_REGISTER_CLIENT = 5;
	public static final int MSG_DISCONNECT_CLIENT = 6;

	public static final String KEY_OBSERVER_CLASS = "bluetooth_observer_class";
	public static final String KEY_OBSERVER_PATH = "bluetooth_directory_path";
	public static final String KEY_FILE_NAME = "received_file_name";

	// keys for settings map
	public static final int KEY_OUTPUT_WIDTH = 1;
	public static final int KEY_OUTPUT_HEIGHT = 2;
	public static final int KEY_PLAYER_BAR_ADJUSTMENT = 3;
	public static final int KEY_BACKGROUND_COLOUR = 4;
	public static final int KEY_TEXT_COLOUR_NO_IMAGE = 5;
	public static final int KEY_TEXT_COLOUR_WITH_IMAGE = 6;
	public static final int KEY_TEXT_BACKGROUND_COLOUR = 7;
	public static final int KEY_TEXT_SPACING = 8;
	public static final int KEY_TEXT_CORNER_RADIUS = 9;
	public static final int KEY_MAX_TEXT_FONT_SIZE = 10;
	public static final int KEY_MAX_TEXT_CHARACTERS_PER_LINE = 11;
	public static final int KEY_MAX_TEXT_HEIGHT_WITH_IMAGE = 12;
	public static final int KEY_IMAGE_QUALITY = 13;
	public static final int KEY_AUDIO_RESOURCE_ID = 14;
	public static final int KEY_COPY_FILES_TO_OUTPUT = 15;
	public static final int KEY_GENERATE_CONTAINER_ONLY = 16;

	public static class FrameMediaContainer {

		// TODO: convert to getters/setters
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

		public void addText(String textContent, String mediaId, String mediaDuration) {
			mTextContent = textContent;
			getMediaDuration(mediaDuration);
		}

		public void addMedia(String mediaType, File mediaFile, String mediaId, String mediaDuration,
				String mediaRegion, boolean validateAudioLengths) {
			if (mediaFile.exists()) {
				int currentDuration = getMediaDuration(mediaDuration);
				if ("img".equals(mediaType)) {
					mImagePath = mediaFile.getAbsolutePath();
					if (mediaRegion.startsWith(SMILUtilities.SMIL_FRONT_IMAGE_STRING)) {
						mImageIsFrontCamera = true;
					}
				} else if ("audio".equals(mediaType)) {
					
					int preciseDuration = currentDuration;
					
					// check the audio duration - having the correct duration is *critical* for narrative playback
					// TODO: fix playback so that this isn't the case...
					if (validateAudioLengths) {
						MediaPlayer mediaPlayer = new MediaPlayer();
						try {
							// can't play from data directory (they're private; permissions don't work), must use input
							// stream
							// mMediaPlayer = MediaPlayer.create(AudioActivity.this,
							// Uri.fromFile(audioMediaItem.getFile()));
							FileInputStream playerInputStream = new FileInputStream(mediaFile);
							mediaPlayer.setDataSource(playerInputStream.getFD());
							IOUtilities.closeStream(playerInputStream);
							mediaPlayer.prepare();
							preciseDuration = mediaPlayer.getDuration();
						} catch (Exception e) {
						} finally {
							if (mediaPlayer != null) {
								mediaPlayer.release();
								mediaPlayer = null;
							}
						}
					}
					addAudioFile(mediaFile.getAbsolutePath(), preciseDuration);
				}
			}
		}

		public void addAudioFile(String fileName, int audioDuration) {
			mAudioPaths.add(fileName);
			mAudioDurations.add(audioDuration);
		}

		private int getMediaDuration(String mediaDuration) {
			if (!TextUtils.isEmpty(mediaDuration)) {
				if (mediaDuration.endsWith(SMILUtilities.SMIL_MILLISECOND_STRING)) {
					mediaDuration = mediaDuration.substring(0, mediaDuration.length()
							- SMILUtilities.SMIL_MILLISECOND_STRING.length());
					int newMediaDuration = StringUtilities.safeStringToInteger(mediaDuration);
					updateFrameMaxDuration(newMediaDuration);
					return newMediaDuration;
				}
			}
			return 0;
		}
	}
}
