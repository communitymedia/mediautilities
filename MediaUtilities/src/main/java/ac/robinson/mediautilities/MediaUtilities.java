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

public class MediaUtilities {

	public static final String APPLICATION_NAME = "mediautilities";
	public static final String LOG_TAG = APPLICATION_NAME;

	// class options
	public static final boolean MOV_USE_SEGMENTED_AUDIO = true;

	// capabilities (detected at load time)
	public static boolean CAN_EXPORT_AMR = false;
	static {
		try {
			System.loadLibrary("opencore-amrnb-wrapper");
			CAN_EXPORT_AMR = true;
		} catch (Throwable t) {
			CAN_EXPORT_AMR = false;
		}
	}

	// file extensions (including dots)
	public static final String SMIL_FILE_EXTENSION = ".smil";
	public static final String SYNC_FILE_EXTENSION = ".sync.jpg"; // to counter ridiculous incoming filename filtering
	public static final String HTML_FILE_EXTENSION = ".html";
	public static final String MOV_FILE_EXTENSION = ".mov";

	// audio file types that are compatible with our video export (no dots)
	// TODO: handle this in a better way? (see CheapSoundFile, for example)
	public static final String[] M4A_FILE_EXTENSIONS = { "m4a", "aac" }; // TODO: to MOV_AUDIO_FILE_EXTENSIONS on edit
	public static final String[] MP3_FILE_EXTENSIONS = { "mp3" }; // TODO: add to MOV_AUDIO_FILE_EXTENSIONS if editing
	public static final String[] WAV_FILE_EXTENSIONS = { "wav" }; // TODO: add to MOV_AUDIO_FILE_EXTENSIONS if editing
	public static final String[] AMR_FILE_EXTENSIONS = { "amr", "3gp", "3gpp" };
	public static String[] MOV_AUDIO_FILE_EXTENSIONS = { "m4a", "aac", "mp3", "wav" };
	static {
		if (CAN_EXPORT_AMR) {
			int totalLength = MOV_AUDIO_FILE_EXTENSIONS.length + AMR_FILE_EXTENSIONS.length;
			String[] tempExtensions = new String[totalLength];
			for (int i = 0; i < MOV_AUDIO_FILE_EXTENSIONS.length; i++) {
				tempExtensions[i] = MOV_AUDIO_FILE_EXTENSIONS[i];
			}
			for (int i = MOV_AUDIO_FILE_EXTENSIONS.length; i < totalLength; i++) {
				tempExtensions[i] = AMR_FILE_EXTENSIONS[i - MOV_AUDIO_FILE_EXTENSIONS.length];
			}
			MOV_AUDIO_FILE_EXTENSIONS = tempExtensions;
		}
	}

	// message IDs (see: http://stackoverflow.com/questions/3432649/)
	public static final int MSG_RECEIVED_SMIL_FILE = 1;
	public static final int MSG_RECEIVED_HTML_FILE = 2;
	public static final int MSG_RECEIVED_MOV_FILE = 3;
	public static final int MSG_RECEIVED_IMPORT_FILE = 4;

	public static final int MSG_REGISTER_CLIENT = 5;
	public static final int MSG_HINT_NEW_FILE = 6;
	public static final int MSG_DISCONNECT_CLIENT = 7;
	public static final int MSG_IMPORT_SERVICE_REGISTERED = 8;

	public static final String KEY_OBSERVER_CLASS = "bluetooth_observer_class";
	public static final String KEY_OBSERVER_PATH = "bluetooth_directory_path";
	public static final String KEY_OBSERVER_REQUIRE_BT = "bluetooth_required";
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
	public static final int KEY_TEXT_BACKGROUND_SPAN_WIDTH = 10;
	public static final int KEY_MAX_TEXT_FONT_SIZE = 11;
	public static final int KEY_MAX_TEXT_CHARACTERS_PER_LINE = 12;
	public static final int KEY_MAX_TEXT_HEIGHT_WITH_IMAGE = 13;
	public static final int KEY_IMAGE_QUALITY = 14;
	public static final int KEY_AUDIO_RESOURCE_ID = 15;
	public static final int KEY_GENERATE_CONTAINER_ONLY = 16;
}
