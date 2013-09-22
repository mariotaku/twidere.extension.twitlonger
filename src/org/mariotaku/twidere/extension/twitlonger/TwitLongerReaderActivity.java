package org.mariotaku.twidere.extension.twitlonger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mariotaku.twidere.Twidere;
import org.mariotaku.twidere.extension.twitlonger.BDTranslate.BDTranslateException;
import org.mariotaku.twidere.extension.twitlonger.BDTranslate.TranslateResponse;
import org.mariotaku.twidere.extension.twitlonger.TwitLonger.TwitLongerException;
import org.mariotaku.twidere.extension.twitlonger.TwitLonger.TwitLongerResponse;
import org.mariotaku.twidere.model.ParcelableStatus;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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
	private String mResult, mUser;
	private ParcelableStatus mStatus;
	private TwitLongerReaderTask mTwitLongerPostTask;
	private BDTranslateTask translateTask;
	private static final Pattern PATTERN_TWITLONGER = Pattern.compile("((tl\\.gd|www.twitlonger.com\\/show)\\/([\\w\\d]+))",
			Pattern.CASE_INSENSITIVE);
	private static final int GROUP_TWITLONGER_ID = 3;
	private boolean isTranslated = false;
	private boolean isOrignal = true;
	private String ORIGINAL_STRING = "";
	private String TRANSLATED_STRING = "";

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.action: {
				
				if (mResult == null) {
					if (mStatus == null) return;
					if (mTwitLongerPostTask != null) {
						mTwitLongerPostTask.cancel(true);
					}
					final Matcher m = PATTERN_TWITLONGER.matcher(mStatus.text);
					if (m.find()) {
						mTwitLongerPostTask = new TwitLongerReaderTask(m.group(GROUP_TWITLONGER_ID));
						mTwitLongerPostTask.execute();
					}
				} else {
					if (mUser == null || mResult == null) return;
					final Intent intent = new Intent(Intent.ACTION_SEND);
					intent.setType("text/plain");
					intent.putExtra(Intent.EXTRA_TEXT, "@" + mUser + ": " + mResult);
					startActivity(Intent.createChooser(intent, getString(R.string.share)));
				}
				break;
			}
			case R.id.translate:{
				if (mPreview.getText() != null) {
						if (translateTask != null) {
							translateTask.cancel(true);
						}else {
							if (!isTranslated) {
								translateTask = new BDTranslateTask(ORIGINAL_STRING);
								translateTask.execute();
							}else {
								if (isOrignal) {
									mPreview.setText(TRANSLATED_STRING);
									isOrignal = false;
								}else {
									mPreview.setText(ORIGINAL_STRING);
									isOrignal = true;
								}
							}
						}
				}else {
					Toast.makeText(getApplicationContext(), "没有推文内容...", Toast.LENGTH_SHORT).show();
					return;
				}
				break;
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent();
		final Uri data = intent.getData();
		final String action = intent.getAction(); 
		setContentView(R.layout.main);
		mPreview = (TextView) findViewById(R.id.text);
		mActionButton = (ImageButton) findViewById(R.id.action);
		mProgress = (ProgressBar) findViewById(R.id.progress);
		mResult = savedInstanceState != null ? savedInstanceState.getString(Twidere.INTENT_KEY_TEXT) : null;
		mUser = savedInstanceState != null ? savedInstanceState.getString(Twidere.INTENT_KEY_USER) : null;
		if (mResult == null || mUser == null) {
			if (Twidere.INTENT_ACTION_EXTENSION_OPEN_STATUS.equals(action)) {
				mStatus = Twidere.getStatusFromIntent(getIntent());
				if (mStatus == null || mStatus.text_html == null) {
					finish();
					return;
				}
				mUser = mStatus.screen_name;
				mPreview.setText(Html.fromHtml(mStatus.text_plain));
				ORIGINAL_STRING = mPreview.getText().toString();
				final Matcher m = PATTERN_TWITLONGER.matcher(mStatus.text_html);
				mActionButton.setEnabled(m.find());
			} else if (Intent.ACTION_VIEW.equals(action) && data != null) {
				mPreview.setText(data.toString());
				ORIGINAL_STRING = data.toString();
				final Matcher m = PATTERN_TWITLONGER.matcher(data.toString());
				if (m.find()) {
					if (mTwitLongerPostTask != null) {
						mTwitLongerPostTask.cancel(true);
					}
					mTwitLongerPostTask = new TwitLongerReaderTask(m.group(GROUP_TWITLONGER_ID));
					mTwitLongerPostTask.execute();
				} else {
					finish();
					return;
				}
			}
		} else {
			mPreview.setText(mResult);
		}
		
		
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString(Twidere.INTENT_KEY_TEXT, mResult);
		outState.putString(Twidere.INTENT_KEY_USER, mUser);
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
				if (mStatus == null) {
					mUser = ((TwitLongerResponse) result).user;
				}
				mPreview.setText(mResult);
				ORIGINAL_STRING = mResult;
				isTranslated = false;
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
	
	public final class BDTranslateTask extends AsyncTask<Void, Void, Object> {

		private final String srcContent;

		public BDTranslateTask(String srcContent) {
			this.srcContent = srcContent;
		}

		@Override
		protected Object doInBackground(Void... args) {
			final BDTranslate translate = new BDTranslate();
			try {
				return translate.postTranslate(srcContent);
			} catch (Exception e) {
				// TODO: handle exception
				return e;
			}
		}

		@Override
		protected void onPostExecute(Object result) {
			if (result instanceof TranslateResponse ) {
				TRANSLATED_STRING = ((TranslateResponse ) result).translateResult;
				mPreview.setText(TRANSLATED_STRING);
				isTranslated = true;
				isOrignal = false;
			} else if (result instanceof BDTranslateException) {
				Toast.makeText(TwitLongerReaderActivity.this,
						getString(R.string.error_message, ((BDTranslateException) result).getMessage()),
						Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(TwitLongerReaderActivity.this, R.string.error_unknown_error, Toast.LENGTH_LONG).show();
			}
			translateTask = null;
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			Toast.makeText(getApplicationContext(), "准备获取翻译结果", Toast.LENGTH_LONG).show();
			super.onPreExecute();
		}
	}
}
