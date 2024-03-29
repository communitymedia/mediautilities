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

package ac.robinson.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import java.util.Formatter;
import java.util.Locale;

import ac.robinson.mediautilities.R;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.BlendModeColorFilterCompat;
import androidx.core.graphics.BlendModeCompat;

// TODO: this class and CustomMediaController could be combined (lots of duplicate code, though recording mode is only here)
public class PlaybackController extends FrameLayout {

	private static final int UPDATE_INTERVAL_MILLIS = 250;

	private static final int PROGRESS_BAR_STEPS = 100;
	private static final int SEEK_FORWARD_MILLIS = 9000;
	private static final int SEEK_BACKWARD_MILLIS = 3000;

	private static final int PLAY_ICON = R.drawable.ic_menu_play;

	private MediaPlayerControl mPlayerControl;

	private View.OnClickListener mBackListener;
	private View.OnClickListener mShareListener;
	private SeekEndedListener mSeekEndListener;

	private StringBuilder mFormatBuilder;
	private Formatter mFormatter;

	private SeekBar mProgress;
	private TextView mEndTime;
	private TextView mCurrentTime;

	private ImageButton mPauseButton;
	private ImageButton mFfwdButton;
	private ImageButton mRewButton;

	private int mPlayIcon = PLAY_ICON;

	private ImageButton mBackButton;
	private ImageButton mShareButton;

	private boolean mUseCustomSeekButtons;
	private boolean mDragging;

	private ProgressBar mRecordIndicator;
	private boolean mRecording;

	public PlaybackController(Context context) {
		this(context, null);
	}

	public PlaybackController(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public PlaybackController(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		LayoutInflater.from(context).inflate(R.layout.playback_controller, PlaybackController.this, true);
	}

	@Override
	public void onFinishInflate() {
		super.onFinishInflate();

		mPauseButton = findViewById(R.id.pause);
		if (mPauseButton != null) {
			mPauseButton.requestFocus();
			mPauseButton.setOnClickListener(mPauseListener);
		}

		mFfwdButton = findViewById(R.id.ffwd);
		if (mFfwdButton != null) {
			mFfwdButton.setOnClickListener(mFfwdListener);
		}

		mRewButton = findViewById(R.id.rew);
		if (mRewButton != null) {
			mRewButton.setOnClickListener(mRewListener);
		}

		// by default these are hidden - they will be enabled when setButtonListeners() is called
		mBackButton = findViewById(R.id.back);
		mShareButton = findViewById(R.id.share);
		installButtonListeners();

		mProgress = findViewById(R.id.mediacontroller_progress);
		if (mProgress != null) {
			mProgress.setOnSeekBarChangeListener(mSeekListener);
			mProgress.setMax(PROGRESS_BAR_STEPS);
		}

		mEndTime = findViewById(R.id.time);
		mCurrentTime = findViewById(R.id.time_current);
		mFormatBuilder = new StringBuilder();
		mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (mPauseButton != null) {
			mPauseButton.setEnabled(enabled);
		}
		if (mFfwdButton != null) {
			mFfwdButton.setEnabled(enabled);
		}
		if (mRewButton != null) {
			mRewButton.setEnabled(enabled);
		}
		if (mShareButton != null) {
			mShareButton.setEnabled(enabled && mBackListener != null);
		}
		if (mBackButton != null) {
			mBackButton.setEnabled(enabled && mShareListener != null);
		}
		if (mProgress != null) {
			mProgress.setEnabled(enabled);
		}
		super.setEnabled(enabled);
	}

	public void setMediaPlayerControl(MediaPlayerControl player) {
		mPlayerControl = player;
		mProgressHandler.post(mProgressRunnable);
	}

	public void setUseCustomSeekButtons(boolean useCustomButtons) {
		mUseCustomSeekButtons = useCustomButtons;
	}

	public void refreshController() {
		updatePausePlay();
		mProgressHandler.post(mProgressRunnable);
	}

	public boolean isDragging() {
		return mDragging;
	}

	// handler and runnable for updating the progress bar
	Handler mProgressHandler = new Handler();
	Runnable mProgressRunnable = new Runnable() {
		@Override
		public void run() {
			if (mPlayerControl == null) {
				return;
			}
			// int pos = setProgress();
			setProgress();
			if (!mDragging && mPlayerControl.isPlaying()) {
				mProgressHandler.postDelayed(mProgressRunnable, UPDATE_INTERVAL_MILLIS); // was: 1000 - (pos % 1000)
			}
		}
	};

	private String stringForTime(int timeMs) {
		int totalSeconds = timeMs / 1000;

		int seconds = totalSeconds % 60;
		int minutes = (totalSeconds / 60) % 60;
		int hours = totalSeconds / 3600;

		mFormatBuilder.setLength(0);
		if (hours > 0) {
			return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
		} else {
			return mFormatter.format("%d:%02d", minutes, seconds).toString();
		}
	}

	private int setProgress() {
		if (mPlayerControl == null || mDragging) {
			return 0;
		}
		int position = mPlayerControl.getCurrentPosition();
		int duration = mPlayerControl.getDuration();
		if (mProgress != null) {
			if (duration > 0) {
				long pos = (long) PROGRESS_BAR_STEPS * position / duration; // use long to avoid overflow
				mProgress.setProgress((int) pos);
			}
			// int percent = mPlayer.getBufferPercentage();
			// mProgress.setSecondaryProgress(percent * 10);
		}

		if (mEndTime != null) {
			mEndTime.setText(stringForTime(duration));
		}
		if (mCurrentTime != null) {
			mCurrentTime.setText(stringForTime(position));
		}

		return position;
	}

	@SuppressLint("ClickableViewAccessibility") // we don't override - we always return super.onTouchEvent
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		refreshController();
		return super.onTouchEvent(event);
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		refreshController();
		return super.onTrackballEvent(event);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int keyCode = event.getKeyCode();
		if (event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN &&
				(keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
						keyCode == KeyEvent.KEYCODE_SPACE)) {
			doPauseResume();
			refreshController();
			if (mPauseButton != null) {
				mPauseButton.requestFocus();
			}
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
			if (mPlayerControl != null && mPlayerControl.isPlaying()) {
				mPlayerControl.pause();
				updatePausePlay();
			}
			return true;
		} else {
			refreshController();
		}
		return super.dispatchKeyEvent(event);
	}

	private final View.OnClickListener mPauseListener = v -> {
		doPauseResume();
		refreshController();
	};

	public void setRecordingMode(boolean recording) {
		if (mRecordIndicator == null) {
			mRecordIndicator = findViewById(R.id.record);
			if (mRecordIndicator != null) {
				Drawable progressDrawable = mRecordIndicator.getIndeterminateDrawable().mutate();
				progressDrawable.setColorFilter(BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
						ContextCompat.getColor(getContext(), R.color.media_controller_recording), BlendModeCompat.SRC_IN));
				mRecordIndicator.setIndeterminateDrawable(progressDrawable);
			}
		}
		if (recording) {
			mPlayIcon = R.drawable.ic_menu_record;
			mRecording = true;
		} else {
			mPlayIcon = PLAY_ICON;
			mRecording = false;
		}
		refreshController();
	}

	private void updatePausePlay() {
		if (mPlayerControl == null || mPauseButton == null) {
			return;
		}
		if (mPlayerControl.isPlaying()) {
			mPauseButton.setImageResource(R.drawable.ic_menu_pause);
			if (mRecording && mRecordIndicator != null) {
				mRecordIndicator.setVisibility(View.VISIBLE);
			}
		} else {
			mPauseButton.setImageResource(mPlayIcon);
			if (mRecordIndicator != null) {
				mRecordIndicator.setVisibility(View.GONE);
			}
		}
	}

	private void doPauseResume() {
		if (mPlayerControl == null) {
			return;
		}
		if (mPlayerControl.isPlaying()) {
			mPlayerControl.pause();
		} else {
			mPlayerControl.play();
		}
	}

	// there are two scenarios that can trigger the seekbar listener to trigger:
	// - the user using the touchpad to adjust the position of the seekbar's thumb; in this case onStartTrackingTouch
	// is called, followed by a number of onProgressChanged notifications, concluded by onStopTrackingTouch
	// we use mDragging for the duration of the dragging session to avoid jumps in position in case of ongoing playback
	// - the second scenario is the user operating the scroll ball; in this case there *won't* be onStartTrackingTouch
	// or onStopTrackingTouch notifications, we'll simply apply the updated position without suspending regular updates
	private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
		public void onStartTrackingTouch(SeekBar bar) {
			mDragging = true;

			// by removing these pending progress messages we make sure that a) we won't update the progress while the
			// user adjusts the seekbar and b) once the user is done dragging the thumb we will post one of these
			// messages to the queue again and this ensures that there will be exactly one message queued up
			mProgressHandler.removeCallbacks(mProgressRunnable);
		}

		public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
			if (!fromUser || mPlayerControl == null) {
				// we're not interested in programmatically generated changes to the progress bar's position.
				return;
			}

			long duration = mPlayerControl.getDuration();
			long newPosition = (duration * progress) / (long) PROGRESS_BAR_STEPS;
			mPlayerControl.seekTo((int) newPosition);
			if (mCurrentTime != null) {
				mCurrentTime.setText(stringForTime((int) newPosition));
			}
		}

		public void onStopTrackingTouch(SeekBar bar) {
			mDragging = false;
			setProgress();

			if (mSeekEndListener != null) {
				mSeekEndListener.seekEnded();
			}

			refreshController();
		}
	};

	private final View.OnClickListener mRewListener = new View.OnClickListener() {
		public void onClick(View v) {
			if (mPlayerControl == null) {
				return;
			}
			if (mUseCustomSeekButtons) {
				mPlayerControl.seekButton(-1);
			} else {
				int pos = mPlayerControl.getCurrentPosition() - SEEK_BACKWARD_MILLIS;
				mPlayerControl.seekTo(Math.max(pos, 0));
			}
			setProgress();

			if (!mUseCustomSeekButtons && mSeekEndListener != null) {
				mSeekEndListener.seekEnded();
			}

			refreshController();
		}
	};

	private final View.OnClickListener mFfwdListener = new View.OnClickListener() {
		public void onClick(View v) {
			if (mPlayerControl == null) {
				return;
			}
			if (mUseCustomSeekButtons) {
				mPlayerControl.seekButton(1);
			} else {
				int pos = mPlayerControl.getCurrentPosition() + SEEK_FORWARD_MILLIS;
				mPlayerControl.seekTo(pos > mPlayerControl.getDuration() ? mPlayerControl.getDuration() - 1 : pos);
			}
			setProgress();

			if (!mUseCustomSeekButtons && mSeekEndListener != null) {
				mSeekEndListener.seekEnded();
			}

			refreshController();
		}
	};

	private void installButtonListeners() {
		if (mShareButton != null) {
			mShareButton.setOnClickListener(mShareListener);
			mShareButton.setEnabled(mShareListener != null);
			mShareButton.setVisibility(mShareListener != null ? View.VISIBLE : View.GONE);
		}

		if (mBackButton != null) {
			mBackButton.setOnClickListener(mBackListener);
			mBackButton.setEnabled(mBackListener != null);
			mBackButton.setVisibility(mBackListener != null ? View.VISIBLE : View.GONE);
		}
	}

	public void setButtonListeners(View.OnClickListener back, View.OnClickListener share) {
		mBackListener = back;
		mShareListener = share;
		installButtonListeners();
	}

	public void setSeekEndedListener(SeekEndedListener seekEnded) {
		mSeekEndListener = seekEnded;
	}

	public interface SeekEndedListener {
		void seekEnded();
	}

	public interface MediaPlayerControl {
		void play();

		void pause();

		int getDuration();

		int getCurrentPosition();

		void seekTo(int pos);

		/**
		 * Note: seekTo will always be called instead of this unless you do setUseCustomSeekButtons(true)
		 */
		void seekButton(int direction);

		boolean isPlaying();
	}
}
