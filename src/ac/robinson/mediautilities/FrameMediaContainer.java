package ac.robinson.mediautilities;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

import ac.robinson.util.IOUtilities;
import ac.robinson.util.StringUtilities;
import android.media.MediaPlayer;
import android.text.TextUtils;

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

	public void addText(String textContent, String mediaId, String mediaDuration) {
		mTextContent = textContent;
		getMediaDuration(mediaDuration);
	}

	public void addMedia(String mediaType, File mediaFile, String mediaId, String mediaDuration, String mediaRegion,
			boolean validateAudioLengths) {
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
