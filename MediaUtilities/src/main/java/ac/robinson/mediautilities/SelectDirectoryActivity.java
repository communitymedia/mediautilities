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

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ac.robinson.util.UIUtilities;

// TODO: use a better picker option? (such as: https://github.com/iPaulPro/aFileChooser or ScopedDirectoryAccess)
public class SelectDirectoryActivity extends ListActivity {

	private static final String ITEM_KEY = "key";
	private static final File ROOT = Environment.getExternalStorageDirectory();

	public static final String START_PATH = "start_path";
	public static final String RESULT_PATH = "result_path";

	private static final String[] SYSTEM_PATHS = {
			"/storage/emulated/0" // TODO: are there others?
	};

	private HorizontalScrollView mPathViewHolder;
	private TextView mPathView;
	private final ArrayList<HashMap<String, String>> mFileList = new ArrayList<>();
	private final List<String> mPaths = new ArrayList<>();

	private File mCurrentPath = ROOT;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(Activity.RESULT_CANCELED, getIntent());

		setContentView(R.layout.select_directory_main);
		mPathViewHolder = findViewById(R.id.path_bar);
		mPathView = findViewById(R.id.path);

		((ImageView) findViewById(R.id.path_bar_icon)).setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);

		String startPath = getIntent().getStringExtra(START_PATH);
		getDirectory(startPath != null ? new File(startPath) : ROOT);
	}

	private void getDirectory(File dirPath) {
		mCurrentPath = dirPath;
		mFileList.clear();
		mPaths.clear();

		File[] files = mCurrentPath.listFiles();
		if (files == null) {
			mCurrentPath = ROOT;
			files = mCurrentPath.listFiles();
		}

		if (files == null) {
			UIUtilities.showToast(SelectDirectoryActivity.this, R.string.directory_browser_error);
			finish();
			return;
		}

		Arrays.sort(files, (o1, o2) -> {
			if (o1.isDirectory()) {
				if (o2.isDirectory()) {
					return o1.getName().compareToIgnoreCase(o2.getName());
				} else {
					return -1;
				}
			} else if (o2.isDirectory()) {
				return 1;
			} else {
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});

		mPathView.setText(getString(R.string.current_location, stripSystemPath(mCurrentPath)));
		mPathViewHolder.post(() -> mPathViewHolder.fullScroll(View.FOCUS_RIGHT));

		if (!mCurrentPath.equals(ROOT)) {
			addItem("/bi/" + getString(R.string.parent_directory));
			mPaths.add(mCurrentPath.getParent());
		}

		// list is pre-sorted
		for (File file : files) {
			String fileName = file.getName();
			if (!fileName.startsWith(".")) {
				if (file.isDirectory()) {
					addItem("/b/" + fileName);
				} else {
					addItem("/i/" + fileName);
				}
				mPaths.add(file.getAbsolutePath());
			}
		}

		StyledSimpleAdapter fileList = new StyledSimpleAdapter(this, mFileList, R.layout.select_directory_row, new String[]{
				ITEM_KEY
		}, new int[]{ R.id.directory_selector_row });

		setListAdapter(fileList);
	}

	private String stripSystemPath(File currentFile) {
		String path = currentFile.getAbsolutePath();
		for (String systemPath : SYSTEM_PATHS) {
			if (path.startsWith(systemPath)) {
				String strippedPath = path.substring(systemPath.length());
				return TextUtils.isEmpty(strippedPath) ? "/" : strippedPath; // return so we only strip once
			}
		}
		return path;
	}

	private void addItem(String text) {
		HashMap<String, String> item = new HashMap<>();
		item.put(ITEM_KEY, text);
		mFileList.add(item);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		File file = new File(mPaths.get(position));
		if (file.isDirectory()) {
			if (file.canRead()) {
				getDirectory(file);
			} else {
				UIUtilities.showToast(SelectDirectoryActivity.this, R.string.directory_browser_error);
			}
		}
	}

	public void handleButtonClicks(View currentButton) {
		int buttonId = currentButton.getId();
		if (buttonId == R.id.select_directory) {
			if (mCurrentPath != null) {
				getIntent().putExtra(RESULT_PATH, mCurrentPath.getAbsolutePath());
				setResult(RESULT_OK, getIntent());
				finish();
			}
		} else if (buttonId == R.id.cancel_select || buttonId == R.id.path_bar_icon) {
			finish();
		}
	}

	private class StyledSimpleAdapter extends SimpleAdapter {
		StyledSimpleAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
			super(context, data, resource, from, to);
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				LayoutInflater layoutInflater = LayoutInflater.from(SelectDirectoryActivity.this);
				convertView = layoutInflater.inflate(R.layout.select_directory_row, parent, false);
			}
			TextView textView = ((TextView) convertView);
			textView.setTypeface(null, Typeface.NORMAL);
			String text = mFileList.get(position).get(ITEM_KEY);
			if (!TextUtils.isEmpty(text)) {
				if (text.startsWith("/bi/")) { // root directory
					textView.setTypeface(null, Typeface.BOLD);
					text = "▲   " + text.substring(4); // TODO: will this arrow symbol work on every device?
				}
				if (text.startsWith("/b/")) { // folders
					text = "\uD83D\uDCC1   " + text.substring(3); // TODO: will this folder symbol work on every device?
				}
				if (text.startsWith("/i/")) { // files
					textView.setTypeface(null, Typeface.ITALIC);
					text = text.substring(3);
				}
			}
			textView.setText(text);
			return convertView;
		}
	}
}
