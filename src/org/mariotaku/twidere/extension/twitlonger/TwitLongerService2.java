package org.mariotaku.twidere.extension.twitlonger;

import java.lang.ref.WeakReference;

import org.mariotaku.twidere.IStatusShortener;
import org.mariotaku.twidere.extension.twitlonger.TwitLonger.TwitLongerException;
import org.mariotaku.twidere.extension.twitlonger.TwitLonger.TwitLongerResponse;
import org.mariotaku.twidere.model.Account;
import org.mariotaku.twidere.model.ParcelableStatusUpdate;
import org.mariotaku.twidere.model.StatusShortenResult;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * Tweet shortener example
 * 
 * @author mariotaku
 */
public class TwitLongerService2 extends Service implements Constants {

	private final StatusShortenerStub mBinder = new StatusShortenerStub(this);

	@Override
	public IBinder onBind(final Intent intent) {
		return mBinder;
	}

	/**
	 * @return Shortened tweet.
	 */
	public StatusShortenResult shorten(final ParcelableStatusUpdate status, final String overrideStatusText) {
		final TwitLonger tl = new TwitLonger(TWITLONGER_APP_NAME, TWITLONGER_API_KEY);
		try {
			final String text = overrideStatusText != null ? overrideStatusText : status.text;
			final Account account = status.accounts[0];
			final TwitLongerResponse response = tl.post(text, account.screen_name, status.in_reply_to_status_id, null);
			if (response != null) return new StatusShortenResult(response.content);
		} catch (final TwitLongerException e) {
			e.printStackTrace();
		}
		return null;
	}

	/*
	 * By making this a static class with a WeakReference to the Service, we
	 * ensure that the Service can be GCd even when the system process still has
	 * a remote reference to the stub.
	 */
	private static final class StatusShortenerStub extends IStatusShortener.Stub {

		final WeakReference<TwitLongerService2> mService;

		public StatusShortenerStub(final TwitLongerService2 service) {
			mService = new WeakReference<TwitLongerService2>(service);
		}

		@Override
		public StatusShortenResult shorten(final ParcelableStatusUpdate status, final String overrideStatusText)
				throws RemoteException {
			return mService.get().shorten(status, overrideStatusText);
		}

	}

}
