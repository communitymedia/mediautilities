/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ac.robinson.view;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import java.util.Formatter;
import java.util.Locale;

import ac.robinson.mediautilities.R;

/**
 * A view containing controls for a MediaPlayer. Typically contains the buttons like "Play/Pause", "Rewind",
 * "Fast Forward" and a progress slider. It takes care of synchronizing the controls with the state of the MediaPlayer.
 * <p>
 * The way to use this class is to instantiate it programatically. The MediaController will create a default set of
 * controls and put them in a window floating above your application. Specifically, the controls will float above the
 * view specified with setAnchorView(). The window will disappear if left idle for three seconds and reappear when the
 * user touches the anchor view.
 * <p>
 * Functions like show() and hide() have no effect when MediaController is created in an xml layout.
 * <p>
 * MediaController will hide and show the buttons according to these rules:
 * <ul>
 * <li>The "previous" and "next" buttons are hidden until setPrevNextListeners() has been called
 * <li>The "previous" and "next" buttons are visible but disabled if setPrevNextListeners() was called with null
 * listeners
 * <li>The "rewind" and "fastforward" buttons are shown unless requested otherwise by using the MediaController(Context,
 * boolean) constructor with the boolean set to false
 * </ul>
 */

public class CustomMediaController extends FrameLayout {

	public static final int DEFAULT_VISIBILITY_TIMEOUT = 3000;

	private MediaPlayerControl mPlayer;
	private Context mContext;
	private View mAnchor;
	private View mRoot;
	private SeekBar mProgress;
	private TextView mEndTime, mCurrentTime;
	private boolean mShowing;
	private boolean mDragging;
	private int mDefaultTimeout = DEFAULT_VISIBILITY_TIMEOUT;
	private static final int FADE_OUT = 1;
	private static final int SHOW_PROGRESS = 2;
	private boolean mUseFastForward;
	private boolean mFromXml;
	private boolean mListenersSet;
	private View.OnClickListener mNextListener, mPrevListener;
	StringBuilder mFormatBuilder;
	Formatter mFormatter;
	private ImageButton mPauseButton;
	private ImageButton mFfwdButton;
	private ImageButton mRewButton;
	private ImageButton mNextButton;
	private ImageButton mPrevButton;

	public CustomMediaController(Context context, AttributeSet attrs) {
		super(context, attrs);
		mRoot = this;
		mContext = context;
		mUseFastForward = true;
		mFromXml = true;
	}

	@Override
	public void onFinishInflate() {
		super.onFinishInflate();
		if (mRoot != null) {
			initControllerView(mRoot);
		}
	}

	public CustomMediaController(Context context, boolean useFastForward) {
		super(context);
		mContext = context;
		mUseFastForward = useFastForward;
	}

	public CustomMediaController(Context context) {
		super(context);
		mContext = context;
		mUseFastForward = true;
	}

	public void setMediaPlayer(MediaPlayerControl player) {
		boolean show = (mPlayer == null);
		mPlayer = player;
		if (show) {
			show();
		}
		// if we encountered an error then progress updates will have stopped - try to resume
		mHandler.sendMessage(mHandler.obtainMessage(SHOW_PROGRESS, CustomMediaController.this));
	}

	/**
	 * Set the view that acts as the anchor for the control view. This can for example be a VideoView, or your
	 * Activity's main view.
	 *
	 * @param view The view to which to anchor the controller when it is visible.
	 */
	public void setAnchorView(View view) {
		mAnchor = view;

		FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup
				.LayoutParams.MATCH_PARENT);

		removeAllViews();
		View v = makeControllerView();
		addView(v, frameParams);
	}

	/**
	 * Create the view that holds the widgets that control playback. Derived classes can override this to create their
	 * own.
	 *
	 * @return The controller view.
	 * @hide This doesn't work as advertised
	 */
	protected View makeControllerView() {
		LayoutInflater inflate = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mRoot = inflate.inflate(R.layout.custom_media_controller, null);

		initControllerView(mRoot);

		return mRoot;
	}

	private void initControllerView(View v) {
		mPauseButton = (ImageButton) v.findViewById(R.id.pause);
		if (mPauseButton != null) {
			mPauseButton.requestFocus();
			mPauseButton.setOnClickListener(mPauseListener);
		}

		mFfwdButton = (ImageButton) v.findViewById(R.id.ffwd);
		if (mFfwdButton != null) {
			mFfwdButton.setOnClickListener(mFfwdListener);
			if (!mFromXml) {
				mFfwdButton.setVisibility(mUseFastForward ? View.VISIBLE : View.GONE);
			}
		}

		mRewButton = (ImageButton) v.findViewById(R.id.rew);
		if (mRewButton != null) {
			mRewButton.setOnClickListener(mRewListener);
			if (!mFromXml) {
				mRewButton.setVisibility(mUseFastForward ? View.VISIBLE : View.GONE);
			}
		}

		// by default these are hidden - they will be enabled when setPrevNextListeners() is called
		mNextButton = (ImageButton) v.findViewById(R.id.next);
		if (mNextButton != null && !mFromXml && !mListenersSet) {
			mNextButton.setVisibility(View.GONE);
		}
		mPrevButton = (ImageButton) v.findViewById(R.id.prev);
		if (mPrevButton != null && !mFromXml && !mListenersSet) {
			mPrevButton.setVisibility(View.GONE);
		}

		mProgress = (SeekBar) v.findViewById(R.id.mediacontroller_progress);
		if (mProgress != null) {
			if (mProgress instanceof SeekBar) {
				SeekBar seeker = (SeekBar) mProgress;
				seeker.setOnSeekBarChangeListener(mSeekListener);
			}
			mProgress.setMax(1000);
		}

		mEndTime = (TextView) v.findViewById(R.id.time);
		mCurrentTime = (TextView) v.findViewById(R.id.time_current);
		mFormatBuilder = new StringBuilder();
		mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());

		installPrevNextListeners();
	}

	/**
	 * Disable pause or seek buttons if the stream cannot be paused or seeked. This requires the control interface to be
	 * a MediaPlayerControlExt
	 */
	private void disableUnsupportedButtons() {
		try {
			if (mPauseButton != null && !mPlayer.canPause()) {
				mPauseButton.setEnabled(false);
			}
			if (mRewButton != null && !mPlayer.canSeekBackward()) {
				mRewButton.setEnabled(false);
			}
			if (mFfwdButton != null && !mPlayer.canSeekForward()) {
				mFfwdButton.setEnabled(false);
			}
		} catch (IncompatibleClassChangeError ex) {
			// we were given an old version of the interface, that doesn't have the canPause/canSeekXYZ methods.
			// this is ok, it just means we assume the media can be paused and seeked, and so we don't disable buttons.
		} catch (NullPointerException e) {
			// most likely we're shutting down, and mPlayer is null...
		}
	}

	/**
	 * Show the controller on screen. It will go away automatically after DEFAULT_TIMEOUT seconds of inactivity.
	 */
	public void show() {
		show(mDefaultTimeout);
	}

	/**
	 * Show the controller on screen. It will go away automatically after 'timeout' milliseconds of inactivity.
	 *
	 * @param timeout The timeout in milliseconds. Use 0 to show the controller until hide() is called.
	 */
	public void show(int timeout) {
		if (timeout >= 0) {
			mDefaultTimeout = timeout; // so we continue showing forever if we were started with 0
		}

		if (!mShowing && mAnchor != null) {
			setProgress();
			if (mPauseButton != null) {
				mPauseButton.requestFocus();
			}
			disableUnsupportedButtons();
			if (mRoot != null) {
				mRoot.clearAnimation();
				mRoot.setVisibility(View.VISIBLE);
				if (mPlayer != null) {
					mPlayer.onControllerVisibilityChange(true);
				}
			}
			mShowing = true;
		} else {
			updatePausePlay();
		}

		// cause the progress bar to be updated even if mShowing was already true - this happens, for example, if we're
		// paused with the progress bar showing and the user hits play
		mHandler.sendMessage(mHandler.obtainMessage(SHOW_PROGRESS, CustomMediaController.this));

		// check separately due to negative values
		if (timeout > 0) {
			refreshShowTimeout();
		} else {
			mHandler.removeMessages(FADE_OUT);
			updatePausePlay();
		}
	}

	public void refreshShowTimeout() {
		mHandler.removeMessages(FADE_OUT);
		if (mDefaultTimeout > 0) {
			Message msg = mHandler.obtainMessage(FADE_OUT, CustomMediaController.this);
			mHandler.sendMessageDelayed(msg, mDefaultTimeout);
		}
	}

	public boolean isShowing() {
		return mShowing;
	}

	public boolean isDragging() {
		return mDragging;
	}

	/**
	 * Remove the controller from the screen.
	 */
	public void hide() {
		if (mAnchor == null) return;

		if (mShowing) {
			if (mRoot != null) {
				mRoot.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_out));
				mRoot.setVisibility(View.GONE); // gone, rather than invisible, so we don't register button clicks
				if (mPlayer != null) {
					mPlayer.onControllerVisibilityChange(false);
				}
			}
			mHandler.removeMessages(SHOW_PROGRESS);
			mShowing = false;
		}
	}

	private void handleProgress(Message msg) {
		if (mPlayer == null) {
			return;
		}
		setProgress(); // int pos = setProgress();
		if (!mDragging && mShowing && (mPlayer.isPlaying() || mPlayer.isLoading())) {
			msg = mHandler.obtainMessage(SHOW_PROGRESS, CustomMediaController.this);
			mHandler.sendMessageDelayed(msg, 100); // was: 1000 - (pos % 1000);
		}
	}

	private static Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case FADE_OUT:
					((CustomMediaController) msg.obj).hide();
					break;
				case SHOW_PROGRESS:
					((CustomMediaController) msg.obj).handleProgress(msg);
					break;
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

	public int setProgress() {
		if (mPlayer == null || mDragging) {
			return 0;
		}
		int position = mPlayer.getCurrentPosition();
		int duration = mPlayer.getDuration();
		if (mProgress != null) {
			if (duration > 0) {
				// use long to avoid overflow
				long pos = 1000L * position / duration;
				mProgress.setProgress((int) pos);
			}
			int percent = mPlayer.getBufferPercentage();
			mProgress.setSecondaryProgress(percent * 10);
		}

		if (mEndTime != null) mEndTime.setText(stringForTime(duration));
		if (mCurrentTime != null) mCurrentTime.setText(stringForTime(position));

		return position;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		show();
		return true;
	}

	@Override
	public boolean onTrackballEvent(MotionEvent ev) {
		show();
		return false;
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int keyCode = event.getKeyCode();
		if (event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
				|| keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_SPACE)) {
			show();
			doPauseResume();
			if (mPauseButton != null) {
				mPauseButton.requestFocus();
			}
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
			if (mPlayer != null && (mPlayer.isPlaying() || mPlayer.isLoading())) {
				mPlayer.pause();
				updatePausePlay();
			}
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			// don't show the controls for volume adjustment
			return super.dispatchKeyEvent(event);
		} else {
			show();
		}
		return super.dispatchKeyEvent(event);
	}

	private View.OnClickListener mPauseListener = new View.OnClickListener() {
		public void onClick(View v) {
			show();
			doPauseResume();
		}
	};

	private void updatePausePlay() {
		if (mRoot == null || mPauseButton == null) return;

		if (mPlayer == null || mPlayer.isPlaying() || mPlayer.isLoading()) {
			mPauseButton.setImageResource(R.drawable.ic_menu_pause);
		} else {
			mPauseButton.setImageResource(R.drawable.ic_menu_play);
		}
	}

	private void doPauseResume() {
		if (mPlayer == null) {
			return;
		}
		if (mPlayer.isPlaying() || mPlayer.isLoading()) {
			mPlayer.pause();
		} else {
			mPlayer.start();
		}
		updatePausePlay();
	}

	// there are two scenarios that can trigger the seekbar listener to trigger:
	// - the user using the touchpad to adjust the posititon of the seekbar's thumb; in this case onStartTrackingTouch
	// is called, followed by a number of onProgressChanged notifications, concluded by onStopTrackingTouch
	// we use mDragging for the duration of the dragging session to avoid jumps in position in case of ongoing playback
	// - the second scenario is the user operating the scroll ball; in this case there *won't* be onStartTrackingTouch
	// or onStopTrackingTouch notifications, we'll simply apply the updated position without suspending regular updates
	private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
		public void onStartTrackingTouch(SeekBar bar) {
			mDragging = true;

			show(-1); // will be counted as 0, but not set to the default
			if (mRoot != null || mPlayer != null || mPauseButton != null) { // we play by default on scroll
				mPauseButton.setImageResource(R.drawable.ic_menu_pause);
			}

			// by removing these pending progress messages we make sure that a) we won't update the progress while the
			// user adjusts the seekbar and b) once the user is done dragging the thumb we will post one of these
			// messages to the queue again and this ensures that there will be exactly one message queued up
			mHandler.removeMessages(SHOW_PROGRESS);
		}

		public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
			if (!fromuser || mPlayer == null) {
				// we're not interested in programmatically generated changes to the progress bar's position.
				return;
			}

			long duration = mPlayer.getDuration();
			long newposition = (duration * progress) / 1000L;
			mPlayer.seekTo((int) newposition);
			if (mCurrentTime != null) mCurrentTime.setText(stringForTime((int) newposition));
		}

		public void onStopTrackingTouch(SeekBar bar) {
			mDragging = false;
			setProgress();
			refreshShowTimeout();

			// ensure that progress is properly updated in the future - the call to refreshShowTimeout() does not
			// guarantee this because it is a no-op if we are already showing.
			mHandler.sendMessage(mHandler.obtainMessage(SHOW_PROGRESS, CustomMediaController.this));
		}
	};

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
		if (mNextButton != null) {
			mNextButton.setEnabled(enabled && mNextListener != null);
		}
		if (mPrevButton != null) {
			mPrevButton.setEnabled(enabled && mPrevListener != null);
		}
		if (mProgress != null) {
			mProgress.setEnabled(enabled);
		}
		disableUnsupportedButtons();
		super.setEnabled(enabled);
	}

	private View.OnClickListener mRewListener = new View.OnClickListener() {
		public void onClick(View v) {
			if (mPlayer == null) {
				return;
			}
			int pos = mPlayer.getCurrentPosition();
			pos -= 5000; // milliseconds
			mPlayer.seekTo(pos < 0 ? 0 : pos);
			setProgress();

			show();
		}
	};

	private View.OnClickListener mFfwdListener = new View.OnClickListener() {
		public void onClick(View v) {
			if (mPlayer == null) {
				return;
			}
			int pos = mPlayer.getCurrentPosition();
			pos += 15000; // milliseconds
			mPlayer.seekTo(pos > mPlayer.getDuration() ? mPlayer.getDuration() - 1 : pos);
			setProgress();

			show();
		}
	};

	private void installPrevNextListeners() {
		if (mNextButton != null) {
			mNextButton.setOnClickListener(mNextListener);
			mNextButton.setEnabled(mNextListener != null);
		}

		if (mPrevButton != null) {
			mPrevButton.setOnClickListener(mPrevListener);
			mPrevButton.setEnabled(mPrevListener != null);
			mPrevButton.setVisibility(mPrevListener == null ? View.GONE : View.VISIBLE);
		}
	}

	public void setPrevNextListeners(View.OnClickListener next, View.OnClickListener prev) {
		mNextListener = next;
		mPrevListener = prev;
		mListenersSet = true;

		if (mRoot != null) {
			installPrevNextListeners();

			if (mNextButton != null && !mFromXml) {
				mNextButton.setVisibility(View.VISIBLE);
			}
			if (mPrevListener != null && mPrevButton != null && !mFromXml) {
				mPrevButton.setVisibility(View.VISIBLE);
			}
		}
	}

	public interface MediaPlayerControl {
		void start();

		void pause();

		int getDuration();

		int getCurrentPosition();

		void seekTo(int pos);

		boolean isPlaying();

		boolean isLoading();

		int getBufferPercentage();

		boolean canPause();

		boolean canSeekBackward();

		boolean canSeekForward();

		void onControllerVisibilityChange(boolean visible);
	}
}
