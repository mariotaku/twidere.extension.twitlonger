package org.mariotaku.twidere.extension.twitlonger;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * A helper class for using the Twitlonger API.
 * 
 * @author Aidan Follestad
 */
public class TwitLonger {

	private final String app_name, api_key;

	private static final String TAG_TWITLONGER = "twitlonger";
	private static final String TAG_ERROR = "error";
	private static final String TAG_POST = "post";
	private static final String TAG_ID = "id";
	private static final String TAG_LINK = "link";
	private static final String TAG_SHORT = "short";
	private static final String TAG_CONTENT = "content";
	private static final String TAG_USER = "user";

	private static final String TWITLONGER_API_POST = "http://www.twitlonger.com/api_post";
	private static final String TWITLONGER_API_CALLBACK = "http://www.twitlonger.com/api_set_id";
	private static final String TWITLONGER_API_READ = "http://www.twitlonger.com/api_read/";

	public TwitLonger(String app_name, String api_key) {
		this.app_name = app_name;
		this.api_key = api_key;
	}

	/**
	 * Performs the Twitlonger callback, should be done after successfully using
	 * the 'post' method.
	 * 
	 * @param status_id The ID of the tweet posted directly to Twitter by you
	 *            using the results of 'post'.
	 * @param response The TwitlongerPostResponse instance returned from your
	 *            previous use of 'post'.
	 * @return True if successful.
	 * @throws Exception
	 */
	public void callback(long status_id, String twitlonger_message_id) throws Exception {
		final ArrayList<NameValuePair> args = new ArrayList<NameValuePair>(2);
		args.add(new BasicNameValuePair("application", app_name));
		args.add(new BasicNameValuePair("api_key", api_key));
		args.add(new BasicNameValuePair("message_id", twitlonger_message_id));
		args.add(new BasicNameValuePair("twitter_id ", Long.toString(status_id)));
		try {
			final HttpClient httpclient = new DefaultHttpClient();
			final HttpPost httppost = new HttpPost(TWITLONGER_API_CALLBACK);
			httppost.setEntity(new UrlEncodedFormEntity(args));
			final HttpResponse response = httpclient.execute(httppost);
			final String responseStr = EntityUtils.toString(response.getEntity(), "UTF-8");

			final XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);

			final XmlPullParser parser = factory.newPullParser();
			parser.setInput(new StringReader(responseStr));

			int eventType = parser.getEventType();
			String tagName;
			boolean lookingForEndOfUnknownTag = false;
			String unknownTagName = null;

			// This loop will skip to the result start tag
			do {
				if (eventType == XmlPullParser.START_TAG) {
					tagName = parser.getName();
					if (TAG_TWITLONGER.equals(tagName)) {
						// Go to next tag
						eventType = parser.next();
						break;
					}
					throw new RuntimeException("Expecting " + TAG_TWITLONGER + " , got " + tagName);
				}
				eventType = parser.next();
			} while (eventType != XmlPullParser.END_DOCUMENT);

			boolean endOfDocument = false;
			while (!endOfDocument) {
				switch (eventType) {
					case XmlPullParser.START_TAG: {
						if (lookingForEndOfUnknownTag) {
							break;
						}
						tagName = parser.getName();
						if (TAG_POST.equalsIgnoreCase(tagName)) {

						} else if (TAG_ERROR.equalsIgnoreCase(tagName))
							throw new TwitLongerException("Server returned error response: " + parser.nextText());
						else {
							lookingForEndOfUnknownTag = true;
							unknownTagName = tagName;
						}
						break;
					}
					case XmlPullParser.END_TAG: {
						tagName = parser.getName();
						if (lookingForEndOfUnknownTag && tagName.equals(unknownTagName)) {
							lookingForEndOfUnknownTag = false;
							unknownTagName = null;
						}
						break;
					}
					case XmlPullParser.END_DOCUMENT: {
						endOfDocument = true;
					}
				}
				eventType = parser.next();
			}
		} catch (final IOException e) {
			throw new TwitLongerException(e);
		} catch (final XmlPullParserException e) {
			throw new TwitLongerException(e);
		}
	}

	/**
	 * Makes a post to Twitlonger, returning the text that should be posted in a
	 * tweet on Twitter.
	 * 
	 * @param status The full content of the tweet, should be over 140
	 *            characters since you're using Twitlonger.
	 * @param in_reply_to_status_id The optional ID of the tweet this post is in
	 *            reply to.
	 * @param in_reply_to_screen_name The optional username of the user that
	 *            posted the tweet that this post is in reply to.
	 * @return The text that should be posted on Twitter.
	 * @throws Exception
	 */
	public TwitLongerResponse post(String status, String user_name, long in_reply_to_status_id,
								   String in_reply_to_screen_name) throws TwitLongerException {
		final ArrayList<NameValuePair> args = new ArrayList<NameValuePair>(2);
		args.add(new BasicNameValuePair("application", app_name));
		args.add(new BasicNameValuePair("api_key", api_key));
		args.add(new BasicNameValuePair("username", user_name));
		args.add(new BasicNameValuePair("message", encodeToParam(status)));
		if (in_reply_to_status_id > 0) {
			args.add(new BasicNameValuePair("in_reply", Long.toString(in_reply_to_status_id)));
			if (in_reply_to_screen_name != null && in_reply_to_screen_name.trim().length() != 0) {
				args.add(new BasicNameValuePair("in_reply_user", in_reply_to_screen_name));
			}
		}
		try {
			final HttpClient httpclient = new DefaultHttpClient();
			final HttpPost httppost = new HttpPost(TWITLONGER_API_POST);
			httppost.setEntity(new UrlEncodedFormEntity(args));
			final HttpResponse response = httpclient.execute(httppost);
			final String response_string = EntityUtils.toString(response.getEntity(), "UTF-8");
			return parseTwitLongerResponse(response_string);
		} catch (final IOException e) {
			throw new TwitLongerException(e);
		}
	}

	/**
	 * Retrieves the full expanded content of a tweet longer post.
	 * 
	 * @param id The ID (at the end of a shortened URL such as tl.gd/id).
	 * @return The full expanded content.
	 * @throws Exception
	 */
	public TwitLongerResponse readPost(String id) throws TwitLongerException {
		try {
			final HttpClient httpclient = new DefaultHttpClient();
			final HttpGet httpget = new HttpGet(TWITLONGER_API_READ + id);
			final HttpResponse response = httpclient.execute(httpget);
			final String responseStr = EntityUtils.toString(response.getEntity(), "UTF-8");
			return parseTwitLongerResponse(responseStr);
		} catch (final IOException e) {
			throw new TwitLongerException(e);
		}
	}

	private TwitLongerResponse parseTwitLongerResponse(String response) throws TwitLongerException {
		try {
			final XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);

			final XmlPullParser parser = factory.newPullParser();
			parser.setInput(new StringReader(response));

			int eventType = parser.getEventType();
			String tagName;
			boolean lookingForEndOfUnknownTag = false;
			String unknownTagName = null;

			// This loop will skip to the result start tag
			do {
				if (eventType == XmlPullParser.START_TAG) {
					tagName = parser.getName();
					if (TAG_TWITLONGER.equals(tagName)) {
						// Go to next tag
						eventType = parser.next();
						break;
					}
					throw new RuntimeException("Expecting " + TAG_TWITLONGER + " , got " + tagName);
				}
				eventType = parser.next();
			} while (eventType != XmlPullParser.END_DOCUMENT);

			boolean endOfDocument = false;
			String id = null, link = null, short_link = null, content = null, user = null;
			while (!endOfDocument) {
				switch (eventType) {
					case XmlPullParser.START_TAG: {
						if (lookingForEndOfUnknownTag) {
							break;
						}
						tagName = parser.getName();
						if (TAG_POST.equalsIgnoreCase(tagName)) {

						} else if (TAG_ERROR.equalsIgnoreCase(tagName))
							throw new TwitLongerException("Server returned error response: " + parser.nextText());
						else if (TAG_ID.equalsIgnoreCase(tagName)) {
							id = parser.nextText();
						} else if (TAG_LINK.equalsIgnoreCase(tagName)) {
							link = parser.nextText();
						} else if (TAG_SHORT.equalsIgnoreCase(tagName)) {
							short_link = parser.nextText();
						} else if (TAG_CONTENT.equalsIgnoreCase(tagName)) {
							content = parser.nextText();
						} else if (TAG_USER.equalsIgnoreCase(tagName)) {
							user = parser.nextText();
						} else {
							lookingForEndOfUnknownTag = true;
							unknownTagName = tagName;
						}
						break;
					}
					case XmlPullParser.END_TAG: {
						tagName = parser.getName();
						if (lookingForEndOfUnknownTag && tagName.equals(unknownTagName)) {
							lookingForEndOfUnknownTag = false;
							unknownTagName = null;
						}
						break;
					}
					case XmlPullParser.END_DOCUMENT: {
						endOfDocument = true;
						if (id == null || content == null)
							throw new TwitLongerException("Server returned unknown reponse.");
					}
				}
				eventType = parser.next();
			}
			return new TwitLongerResponse(id, content, link, short_link, user);
		} catch (final IOException e) {
			throw new TwitLongerException(e);
		} catch (final XmlPullParserException e) {
			throw new TwitLongerException(e);
		}
	}

	public static class TwitLongerException extends Exception {

		private static final long serialVersionUID = 1020016463204888157L;

		public TwitLongerException() {
			super();
		}

		public TwitLongerException(String detailMessage) {
			super(detailMessage);
		}

		public TwitLongerException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
		}

		public TwitLongerException(Throwable throwable) {
			super(throwable);
		}

	}

	public static class TwitLongerResponse {

		public final String id, content, link, short_link, user;

		private TwitLongerResponse(String id, String content, String link, String short_link, String user) {
			this.id = id;
			this.content = content;
			this.link = link;
			this.short_link = short_link;
			this.user = user;
		}
	}
	
	/**
	 * @param value string to be encoded
	 * @return encoded string
	 * @see <a href="http://wiki.oauth.net/TestCases">OAuth / TestCases</a>
	 * @see <a
	 *      href="http://groups.google.com/group/oauth/browse_thread/thread/a8398d0521f4ae3d/9d79b698ab217df2?hl=en&lnk=gst&q=space+encoding#9d79b698ab217df2">Space
	 *      encoding - OAuth | Google Groups</a>
	 * @see <a href="http://tools.ietf.org/html/rfc3986#section-2.1">RFC 3986 -
	 *      Uniform Resource Identifier (URI): Generic Syntax - 2.1.
	 *      Percent-Encoding</a>
	 */
	private static String encodeToParam(String value) {
		String encoded = null;
		try {
			encoded = URLEncoder.encode(value, "UTF-8");
		} catch (final UnsupportedEncodingException ignore) {
		}
		final StringBuilder builder = new StringBuilder();
		char focus;
		for (int i = 0; i < encoded.length(); i++) {
			focus = encoded.charAt(i);
			if (focus == '*') {
				builder.append("%2A");
			} else if (focus == '+') {
				builder.append("%20");
			} else if (focus == '%' && i + 1 < encoded.length() && encoded.charAt(i + 1) == '7'
					&& encoded.charAt(i + 2) == 'E') {
				builder.append('~');
				i += 2;
			} else if (focus == '\n') {
				builder.append("%0A");
			} else {
				builder.append(focus);
			}
		}
		return builder.toString();
	}

}
