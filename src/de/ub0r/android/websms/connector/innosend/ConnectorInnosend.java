/*
 * Copyright (C) 2010 Felix Bechstein
 * 
 * This file is part of WebSMS.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.android.websms.connector.innosend;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import de.ub0r.android.websms.connector.common.Connector;
import de.ub0r.android.websms.connector.common.ConnectorCommand;
import de.ub0r.android.websms.connector.common.ConnectorSpec;
import de.ub0r.android.websms.connector.common.ConnectorSpec.SubConnectorSpec;
import de.ub0r.android.websms.connector.common.Log;
import de.ub0r.android.websms.connector.common.Utils;
import de.ub0r.android.websms.connector.common.Utils.HttpOptions;
import de.ub0r.android.websms.connector.common.WebSMSException;

/**
 * AsyncTask to manage IO to Innosend.de API.
 * 
 * @author flx
 */
public class ConnectorInnosend extends Connector {
	/** Tag for output. */
	private static final String TAG = "inno";

	/** {@link SubConnectorSpec} ID: free. */
	private static final String ID_FREE = "free";
	/** {@link SubConnectorSpec} ID: type3. */
	private static final String ID_TYPE3 = "type3";
	/** {@link SubConnectorSpec} ID: with sender. */
	private static final String ID_W_SENDER = "w_sender";
	/** {@link SubConnectorSpec} ID: without sender. */
	private static final String ID_WO_SENDER = "wo_sender";
	/** Preference's name: hide free subcon. */
	private static final String PREFS_HIDE_FREE = "hide_free";
	/** Preference's name: hide with sender subcon. */
	private static final String PREFS_HIDE_W_SENDER = "hide_withsender";
	/** Preference's name: hide without sender subcon. */
	private static final String PREFS_HIDE_WO_SENDER = "hide_nosender";
	/** Preference's name: hide type3 subcon. */
	private static final String PREFS_HIDE_TYPE3 = "hide_type3";
	/** Preference's name: return mail. */
	private static final String PREFS_RETMAIL = "retmail";

	/** Maximal length. */
	private static final int MAX_LENGTH = 160;

	/** Innosend Gateway URL. */
	private static final String URL = "https://www.innosend.de/gateway/";

	/** Custom Dateformater. */
	private static final String DATEFORMAT = "dd.MM.yyyy-kk:mm";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec initSpec(final Context context) {
		final String name = context.getString(R.string.connector_innosend_name);
		ConnectorSpec c = new ConnectorSpec(name);
		c.setAuthor(context.getString(R.string.connector_innosend_author));
		c.setBalance(null);
		c.setCapabilities(ConnectorSpec.CAPABILITIES_UPDATE
				| ConnectorSpec.CAPABILITIES_SEND
				| ConnectorSpec.CAPABILITIES_PREFS);
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (!p.getBoolean(PREFS_HIDE_FREE, false)) {
			c.addSubConnector(ID_FREE, context.getString(R.string.free),
					SubConnectorSpec.FEATURE_NONE);
		}
		if (!p.getBoolean(PREFS_HIDE_TYPE3, false)) {
			c.addSubConnector(ID_TYPE3, context.getString(R.string.type3),
					SubConnectorSpec.FEATURE_NONE);
		}
		if (!p.getBoolean(PREFS_HIDE_WO_SENDER, false)) {
			c.addSubConnector(ID_WO_SENDER,
					context.getString(R.string.wo_sender),
					SubConnectorSpec.FEATURE_SENDLATER
							| SubConnectorSpec.FEATURE_FLASHSMS);
		}
		if (!p.getBoolean(PREFS_HIDE_W_SENDER, false)) {
			c.addSubConnector(ID_W_SENDER,
					context.getString(R.string.w_sender),
					SubConnectorSpec.FEATURE_CUSTOMSENDER
							| SubConnectorSpec.FEATURE_SENDLATER);
		}
		return c;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec updateSpec(final Context context,
			final ConnectorSpec connectorSpec) {
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (p.getBoolean(Preferences.PREFS_ENABLED, false)) {
			if (p.getString(Preferences.PREFS_USER, "").length() > 0
					&& p.getString(Preferences.PREFS_PASSWORD, "")// .
							.length() > 0) {
				connectorSpec.setReady();
			} else {
				connectorSpec.setStatus(ConnectorSpec.STATUS_ENABLED);
			}
		} else {
			connectorSpec.setStatus(ConnectorSpec.STATUS_INACTIVE);
		}
		return connectorSpec;
	}

	/**
	 * Check return code from innosend.de.
	 * 
	 * @param context
	 *            Context
	 * @param connector
	 *            ConnectorSpec
	 * @param ret
	 *            return code
	 * @param more
	 *            more text
	 * @param failOnError
	 *            fail if return code is not 10*
	 * @return true if no error code
	 */
	private static boolean checkReturnCode(final Context context,
			final ConnectorSpec connector, final int ret, final String more,
			final boolean failOnError) {
		switch (ret) {
		case 100:
		case 101:
			// this.pushMessage(WebSMS.MESSAGE_LOG, more + " "
			// + context.getString(R.string.log_remain_free));
			return true;
		case 161:
			if (!failOnError) {
				String balance = connector.getBalance();
				if (balance.length() > 0) {
					balance += "/";
				}
				balance += context.getString(R.string.innosend_next_free) + " "
						+ more;
				connector.setBalance(balance);
				return true;
			}
			throw new WebSMSException(context, R.string.error_innosend_161, " "
					+ more);
		default:
			throw new WebSMSException(context, R.string.error, " code: " + ret
					+ " " + more);
		}
	}

	/**
	 * Check return code from innosend.de.
	 * 
	 * @param context
	 *            Context
	 * @param ret
	 *            return code
	 * @return true if no error code
	 */
	private static boolean checkReturnCode(final Context context, final int ret) {
		switch (ret) {
		case 100:
		case 101:
			return true;
		case 111:
			throw new WebSMSException(context, R.string.error_innosend_111);
		case 112:
			throw new WebSMSException(context, R.string.error_pw);
		case 120:
			throw new WebSMSException(context, R.string.error_innosend_111);
		case 121:
			throw new WebSMSException(context, R.string.error_innosend_121);
		case 122:
			throw new WebSMSException(context, R.string.error_innosend_122);
		case 123:
			throw new WebSMSException(context, R.string.error_innosend_123);
		case 129:
			throw new WebSMSException(context, R.string.error_innosend_129);
		case 130:
			throw new WebSMSException(context, R.string.error_innosend_130);
		case 140:
			throw new WebSMSException(context, R.string.error_innosend_140);
		case 150:
			throw new WebSMSException(context, R.string.error_innosend_150);
		case 170:
			throw new WebSMSException(context, R.string.error_innosend_170);
		case 171:
			throw new WebSMSException(context, R.string.error_innosend_171);
		case 172:
			throw new WebSMSException(context, R.string.error_innosend_172);
		case 173:
			throw new WebSMSException(context, R.string.error_innosend_173);
		default:
			throw new WebSMSException(context, R.string.error, " code: " + ret);
		}
	}

	/**
	 * Send data.
	 * 
	 * @param context
	 *            Context
	 * @param command
	 *            ConnectorCommand
	 * @param updateFree
	 *            update free sms
	 * @throws IOException
	 *             IOException
	 */
	private void sendData(final Context context,
			final ConnectorCommand command, final boolean updateFree)
			throws IOException {
		// get Connection
		final ConnectorSpec cs = this.getSpec(context);
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		final StringBuilder url = new StringBuilder(URL);
		final ArrayList<BasicNameValuePair> d = // .
		new ArrayList<BasicNameValuePair>();
		final String text = command.getText();
		if (text != null && text.length() > 0) {
			final String subCon = command.getSelectedSubConnector();
			if (subCon.equals(ID_FREE)) {
				url.append("free.php");
				d.add(new BasicNameValuePair("app", "1"));
				d.add(new BasicNameValuePair("was", "iphone"));
			} else {
				url.append("sms.php");
			}
			d.add(new BasicNameValuePair("text", text));
			if (subCon.equals(ID_W_SENDER)) {
				d.add(new BasicNameValuePair("type", "4"));
				if (text.length() > MAX_LENGTH) {
					d.add(new BasicNameValuePair("maxi", "1"));
				}
			} else if (subCon.equals(ID_WO_SENDER)) {
				d.add(new BasicNameValuePair("type", "2"));
				if (text.length() > MAX_LENGTH) {
					throw new WebSMSException(context, R.string.error_length);
				}
			} else {
				d.add(new BasicNameValuePair("type", "3"));
				if (text.length() > MAX_LENGTH) {
					throw new WebSMSException(context, R.string.error_length);
				}
			}
			d.add(new BasicNameValuePair("empfaenger", Utils
					.joinRecipientsNumbers(command.getRecipients(), ",", true)));

			final String customSender = command.getCustomSender();
			if (customSender == null) {
				d.add(new BasicNameValuePair("absender", Utils
						.international2national(command.getDefPrefix(), Utils
								.getSender(context, command.getDefSender()))));
			} else {
				d.add(new BasicNameValuePair("absender", customSender));
			}
			final String rm = p.getString(PREFS_RETMAIL, "");
			if (rm != null && rm.length() > 2) {
				d.add(new BasicNameValuePair("reply_email", rm));
				d.add(new BasicNameValuePair("reply", "1"));
			}

			if (command.getFlashSMS()) {
				d.add(new BasicNameValuePair("flash", "1"));
			}
			long sendLater = command.getSendLater();
			if (sendLater > 0) {
				if (sendLater <= 0) {
					sendLater = System.currentTimeMillis();
				}
				d.add(new BasicNameValuePair("termin", DateFormat.format(
						DATEFORMAT, sendLater).toString()));
			}
		} else {
			if (updateFree) {
				url.append("free.php");
				d.add(new BasicNameValuePair("app", "1"));
				d.add(new BasicNameValuePair("was", "iphone"));
			} else {
				url.append("konto.php");
			}
		}
		d.add(new BasicNameValuePair("id", p.getString(Preferences.PREFS_USER,
				"")));
		d.add(new BasicNameValuePair("pw", p.getString(
				Preferences.PREFS_PASSWORD, "")));
		HttpOptions o = new HttpOptions();
		o.url = url.toString();
		o.addFormParameter(d);
		o.trustAll = true;
		HttpResponse response = Utils.getHttpClient(o);
		int resp = response.getStatusLine().getStatusCode();
		if (resp != HttpURLConnection.HTTP_OK) {
			throw new WebSMSException(context, R.string.error_http, " " + resp);
		}
		String htmlText = Utils.stream2str(response.getEntity().getContent())
				.trim();
		int i = htmlText.indexOf(',');
		if (i > 0 && !updateFree) {
			cs.setBalance(htmlText.substring(0, i + 3) + "\u20AC");
		} else {
			i = htmlText.indexOf("<br>");
			int ret;
			Log.d(TAG, url.toString());
			if (i < 0) {
				ret = Integer.parseInt(htmlText);
				if (!updateFree) {
					ConnectorInnosend.checkReturnCode(context, ret);
				}
			} else {
				ret = Integer.parseInt(htmlText.substring(0, i));
				ConnectorInnosend.checkReturnCode(context, cs, ret, htmlText
						.substring(i + 4).trim(), !updateFree);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doUpdate(final Context context, final Intent intent)
			throws IOException {
		final ConnectorCommand c = new ConnectorCommand(intent);
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (!p.getBoolean(PREFS_HIDE_TYPE3, false)
				|| !p.getBoolean(PREFS_HIDE_WO_SENDER, false)
				|| !p.getBoolean(PREFS_HIDE_W_SENDER, false)) {
			this.sendData(context, c, false);
		}
		if (!p.getBoolean(PREFS_HIDE_FREE, false)) {
			this.sendData(context, c, true);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doSend(final Context context, final Intent intent)
			throws IOException {
		this.sendData(context, new ConnectorCommand(intent), false);
	}
}
