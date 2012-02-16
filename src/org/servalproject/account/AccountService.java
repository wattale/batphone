package org.servalproject.account;

import java.util.ArrayList;

import org.servalproject.Main;
import org.servalproject.dna.SubscriberId;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

public class AccountService extends Service {
	private static AccountAuthenticator authenticator=null;
	public static final String ACTION_ADD = "org.servalproject.account.add";
	public static final String TYPE = "org.servalproject.account";

	public static final String SID_FIELD_MIMETYPE = "vnd.android.cursor.item/org.servalproject.insecureSid";

	public static long getContact(ContentResolver resolver, SubscriberId sid) {
		long ret = -1;
		Cursor cursor = resolver.query(ContactsContract.Data.CONTENT_URI,
				new String[] { ContactsContract.Data.CONTACT_ID },
				ContactsContract.Data.DATA1 + " = ? AND "
						+ ContactsContract.Data.MIMETYPE + " = ?",
				new String[] { sid.toString(), SID_FIELD_MIMETYPE }, null);
		if (cursor.moveToNext())
			ret = cursor.getLong(0);
		cursor.close();
		return ret;
	}

	public static long getContact(ContentResolver resolver, String did) {
		long ret = -1;
		Cursor cursor = resolver
				.query(ContactsContract.Data.CONTENT_URI,
						new String[] { ContactsContract.Data.CONTACT_ID },
						ContactsContract.CommonDataKinds.Phone.NUMBER
								+ " = ? AND " + ContactsContract.Data.MIMETYPE
								+ " = ?",
						new String[] {
								did,
								ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE },
						null);
		if (cursor.moveToNext())
			ret = cursor.getLong(0);
		cursor.close();
		return ret;
	}

	public static String getContactName(ContentResolver resolver, long contactId) {
		Cursor cursor = resolver.query(ContactsContract.Contacts.CONTENT_URI,
				new String[] { ContactsContract.Contacts.DISPLAY_NAME },
				"_ID = ?", new String[] { Long.toString(contactId) }, null);

		try {
			if (!cursor.moveToNext()) {
				Log.w("BatPhone", "Could not find contact name for "
						+ contactId);
				return null;
			}

			return cursor.getString(0);
		} finally {
			cursor.close();
		}
	}

	public static SubscriberId getContactSid(ContentResolver resolver,
			long contactId) {
		Cursor cursor = resolver.query(ContactsContract.Data.CONTENT_URI,
				new String[] { ContactsContract.Data.DATA1 },
				ContactsContract.Data.CONTACT_ID + " = ? AND "
						+ ContactsContract.Data.MIMETYPE + " = ?",
				new String[] { Long.toString(contactId), SID_FIELD_MIMETYPE },
				null);
		try {
			if (cursor.moveToNext())
				return new SubscriberId(cursor.getString(0));
		} finally {
			cursor.close();
		}
		return null;
	}

	public static void addContact(ContentResolver resolver, String name,
			SubscriberId sid, String did) {
		String username = "Serval Mesh";
		Account account = new Account(username, AccountService.TYPE);
		addContact(resolver, account, name, sid, did);
	}

	public static void addContact(ContentResolver resolver, Account account,
			String name, SubscriberId sid, String did) {
		Log.i("BatPhone", "Adding contact: " + name);
		ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

		// Create our RawContact
		ContentProviderOperation.Builder builder = ContentProviderOperation
				.newInsert(RawContacts.CONTENT_URI);
		builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
		builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
		builder.withValue(RawContacts.VERSION, 1);
		operationList.add(builder.build());

		// Create a Data record of common type 'StructuredName' for our
		// RawContact
		builder = ContentProviderOperation
				.newInsert(ContactsContract.Data.CONTENT_URI);
		builder.withValueBackReference(
				ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID,
				0);
		builder.withValue(
				ContactsContract.Data.MIMETYPE,
				ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
		if (name != null && !name.equals(""))
			builder.withValue(
					ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
					name);
		operationList.add(builder.build());

		// Create a Data record for the subscriber id
		builder = ContentProviderOperation
				.newInsert(ContactsContract.Data.CONTENT_URI);
		builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
		builder.withValue(ContactsContract.Data.MIMETYPE, SID_FIELD_MIMETYPE);
		String sidText = sid.toString();
		builder.withValue(ContactsContract.Data.DATA1, sidText);
		builder.withValue(ContactsContract.Data.DATA2, "Public Key");
		builder.withValue(ContactsContract.Data.DATA3, sidText.substring(0, 16)
				+ "...");
		operationList.add(builder.build());

		// Create a Data record for their phone number
		builder = ContentProviderOperation
				.newInsert(ContactsContract.Data.CONTENT_URI);
		builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
		builder.withValue(ContactsContract.Data.MIMETYPE,
				ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
		builder.withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, did);
		builder.withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
				ContactsContract.CommonDataKinds.Phone.TYPE_MAIN);
		operationList.add(builder.build());

		try {
			resolver.applyBatch(ContactsContract.AUTHORITY,
					operationList);
		} catch (Exception e) {
			Log.e("BatPhone", e.getMessage(), e);
		}
	}

	private class AccountAuthenticator extends AbstractAccountAuthenticator {
		Context context;

		public AccountAuthenticator(Context context) {
			super(context);
			this.context = context;
		}

		@Override
		public Bundle addAccount(AccountAuthenticatorResponse response,
				String accountType, String authTokenType,
				String[] requiredFeatures, Bundle options)
				throws NetworkErrorException {

			Intent intent = new Intent(context, AccountAuthActivity.class);
			intent.setAction(ACTION_ADD);
			intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
					response);
			Bundle reply = new Bundle();
			reply.putParcelable(AccountManager.KEY_INTENT, intent);
			return reply;
		}

		@Override
		public Bundle confirmCredentials(AccountAuthenticatorResponse response,
				Account account, Bundle options) throws NetworkErrorException {
			return null;
		}

		@Override
		public Bundle editProperties(AccountAuthenticatorResponse response,
				String accountType) {
			Intent intent = new Intent(context, Main.class);
			Bundle reply = new Bundle();
			reply.putParcelable(AccountManager.KEY_INTENT, intent);
			return reply;
		}

		@Override
		public Bundle getAuthToken(AccountAuthenticatorResponse response,
				Account account, String authTokenType, Bundle options)
				throws NetworkErrorException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getAuthTokenLabel(String authTokenType) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Bundle hasFeatures(AccountAuthenticatorResponse response,
				Account account, String[] features)
				throws NetworkErrorException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Bundle updateCredentials(AccountAuthenticatorResponse response,
				Account account, String authTokenType, Bundle options)
				throws NetworkErrorException {
			// TODO Auto-generated method stub
			return null;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		if (intent.getAction().equals(AccountManager.ACTION_AUTHENTICATOR_INTENT)){
			if (authenticator==null)
				authenticator = new AccountAuthenticator(this);
			return authenticator.getIBinder();
		}
		return null;
	}

}
