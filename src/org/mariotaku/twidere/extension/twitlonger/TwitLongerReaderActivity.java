package org.mariotaku.twidere.extension.twitlonger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mariotaku.twidere.Twidere;
import org.mariotaku.twidere.extension.twitlonger.TwitLonger.TwitLongerException;
import org.mariotaku.twidere.extension.twitlonger.TwitLonger.TwitLongerResponse;
import org.mariotaku.twidere.model.ParcelableStatus;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class TwitLongerReaderActivity extends Activity implements Constants, OnClickListener {

	private TextView mPreview;
	private ImageButton mActionButton;
	private ProgressBar mProgress;
	private String mResult;
	private ParcelableStatus mStatus;
	private TwitLongerReaderTask mTwitLongerPostTask;
	private static final Pattern PATTERN_TWITLONGER = Pattern.compile("(tl\\.gd\\/([\\w\\d]+))",
			Pattern.CASE_INSENSITIVE);
	private static final int GROUP_TWITLONGER_ID = 2;

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.action: {
				if (mResult == null) {
					if (mTwitLongerPostTask != null) {
						mTwitLongerPostTask.cancel(true);
					}
					final Matcher m = PATTERN_TWITLONGER.matcher(mStatus.text);
					if (m.find()) {
						mTwitLongerPostTask = new TwitLongerReaderTask(m.group(GROUP_TWITLONGER_ID));
						mTwitLongerPostTask.execute();
					}
				} else {
					final Intent intent = new Intent(Intent.ACTION_SEND);
					intent.setType("text/plain");
					intent.putExtra(Intent.EXTRA_TEXT, "@" + mStatus.screen_name + ": " + mResult);
					startActivity(Intent.createChooser(intent, getString(R.string.share)));
				}
				break;
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		mPreview = (TextView) findViewById(R.id.text);
		mActionButton = (ImageButton) findViewById(R.id.action);
		mProgress = (ProgressBar) findViewById(R.id.progress);
		mStatus = Twidere.getStatusFromIntent(getIntent());
		if (mStatus == null || mStatus.text_html == null) {
			finish();
			return;
		}
		mResult = savedInstanceState != null ? savedInstanceState.getString(Intent.EXTRA_TEXT) : null;
		mPreview.setText(mResult != null ? mResult : Html.fromHtml(mStatus.text_plain));
		final Matcher m = PATTERN_TWITLONGER.matcher(mStatus.text_html);
		mActionButton.setEnabled(m.find());
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString(Intent.EXTRA_TEXT, mResult);
		super.onSaveInstanceState(outState);
	}

	public final class TwitLongerReaderTask extends AsyncTask<Void, Void, Object> {

		private final String id;

		public TwitLongerReaderTask(String id) {
			this.id = id;
		}

		@Override
		protected Object doInBackground(Void... args) {
			final TwitLonger tl = new TwitLonger(TWITLONGER_APP_NAME, TWITLONGER_API_KEY);
			try {
				return tl.readPost(id);
			} catch (final TwitLongerException e) {
				return e;
			}
		}

		@Override
		protected void onPostExecute(Object result) {
			mProgress.setVisibility(View.GONE);
			mActionButton.setVisibility(View.VISIBLE);
			mActionButton.setImageResource(result instanceof TwitLongerResponse ? R.drawable.ic_menu_share
					: R.drawable.ic_menu_send);
			if (result instanceof TwitLongerResponse) {
				mResult = ((TwitLongerResponse) result).content;
				mPreview.setText(mResult);
			} else if (result instanceof TwitLongerException) {
				Toast.makeText(TwitLongerReaderActivity.this,
						getString(R.string.error_message, ((TwitLongerException) result).getMessage()),
						Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(TwitLongerReaderActivity.this, R.string.error_unknown_error, Toast.LENGTH_LONG).show();
			}
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			mProgress.setVisibility(View.VISIBLE);
			mActionButton.setVisibility(View.GONE);
			super.onPreExecute();
		}

	}
}
