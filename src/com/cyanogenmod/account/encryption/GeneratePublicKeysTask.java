/*
 * Copyright (C) 2013 The CyanogenMod Project
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
package com.cyanogenmod.account.encryption;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.cyanogenmod.account.CMAccount;
import com.cyanogenmod.account.api.request.AddPublicKeysRequestBody;
import com.cyanogenmod.account.api.response.AddPublicKeysResponse;
import com.cyanogenmod.account.auth.AuthClient;
import com.cyanogenmod.account.provider.CMAccountProvider;
import com.cyanogenmod.account.util.CMAccountUtils;
import com.cyanogenmod.account.util.EncryptionUtils;

import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.math.ec.ECPoint;

import java.util.ArrayList;
import java.util.List;

public class GeneratePublicKeysTask implements Response.ErrorListener, Response.Listener<AddPublicKeysResponse> {
    private static final String TAG = GeneratePublicKeysTask.class.getSimpleName();
    private static Object mNetworkRequestLock = new Object();
    private static boolean mNetworkRequestInProgress = false;
    private static final int MINIMUM_KEYS = 25;

    private final Context mContext;
    private final AuthClient mAuthClient;
    private Intent mIntent;

    public GeneratePublicKeysTask(Context context) {
        mContext = context;
        mAuthClient = AuthClient.getInstance(context);
    }

    protected void start(Intent intent) {
        mIntent = intent;
        boolean retry = intent.getBooleanExtra(ECDHKeyService.EXTRA_RETRY, false);
        if (retry && CMAccount.DEBUG) Log.d(TAG, "Scheduled retry");

        boolean upload = intent.getBooleanExtra(ECDHKeyService.EXTRA_UPLOAD, true);

        int keyCount = getKeyCount();
        if (keyCount < MINIMUM_KEYS && !retry) {
            generateKeyPairs(MINIMUM_KEYS - keyCount);
        }

        if (upload) uploadKeyPairs();
    }

    private int getKeyCount() {
        Cursor cursor = mContext.getContentResolver().query(CMAccountProvider.ECDH_CONTENT_URI, null, null, null, null);
        int count = cursor.getCount();
        if (CMAccount.DEBUG) Log.d(TAG, "Total ECDH keys: " + count);
        cursor.close();
        return count;
    }

    private List<ECKeyPair> generateKeyPairs(int totalKeys) {
        if (CMAccount.DEBUG) Log.d(TAG, "Generating " + totalKeys + " ECDH keys");

        List<ECKeyPair> keyPairs = new ArrayList<ECKeyPair>();

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < totalKeys; i++) {
            if (CMAccount.DEBUG) Log.d(TAG, "Generating key " + i + "/" + totalKeys);
            ECKeyPair keyPair = generateKeyPair();
            keyPairs.add(keyPair);
            storeKeyPair(keyPair);
        }
        long endTime = System.currentTimeMillis();

        if (CMAccount.DEBUG) Log.d(TAG, "Generated " + totalKeys + " keys in " + (endTime - startTime) + " ms.");

        return keyPairs;
    }

    private ECKeyPair generateKeyPair() {
        ECKeyPair keyPair = EncryptionUtils.ECDH.generateKeyPair();
        return keyPair;
    }

    private void storeKeyPair(ECKeyPair keyPair) {
        String privateKey = CMAccountUtils.encodeHex(keyPair.getPrivateKey().getD());
        ECPoint publicKey = keyPair.getPublicKey().getQ();

        ContentValues values = new ContentValues();
        values.put(CMAccountProvider.ECDHKeyStoreColumns.PRIVATE, privateKey.toString());
        values.put(CMAccountProvider.ECDHKeyStoreColumns.PUBLIC, CMAccountUtils.encodeHex(publicKey.getEncoded()));
        values.put(CMAccountProvider.ECDHKeyStoreColumns.KEY_ID, keyPair.getKeyId());
        mContext.getContentResolver().insert(CMAccountProvider.ECDH_CONTENT_URI, values);
    }

    private List<ECKeyPair> getKeyPairs() {
        List<ECKeyPair> keyPairs = new ArrayList<ECKeyPair>();
        Cursor cursor = mContext.getContentResolver().query(CMAccountProvider.ECDH_CONTENT_URI, null, null, null, null);
        while (cursor.moveToNext()) {
            String publicKeyHex = cursor.getString(cursor.getColumnIndex(CMAccountProvider.ECDHKeyStoreColumns.PUBLIC));
            String keyId = cursor.getString(cursor.getColumnIndex(CMAccountProvider.ECDHKeyStoreColumns.KEY_ID));

            ECPublicKeyParameters publicKey = EncryptionUtils.ECDH.getPublicKey(publicKeyHex);
            ECKeyPair keyPair = new ECKeyPair(publicKey, keyId);
            keyPairs.add(keyPair);
        }
        cursor.close();
        return keyPairs;
    }

    private void uploadKeyPairs() {
        List<ECKeyPair> keyPairs = getKeyPairs();
        if (keyPairs.size() == 0) {
            CMAccountUtils.resetBackoff(mAuthClient.getEncryptionPreferences());
            if (CMAccount.DEBUG) Log.d(TAG, "No keys to upload.");
            return;
        }

        if (CMAccountUtils.getCMAccountAccount(mContext) == null) {
            CMAccountUtils.resetBackoff(mAuthClient.getEncryptionPreferences());
            if (CMAccount.DEBUG) Log.d(TAG, "No CMAccount Configured!");
            return;
        }

        synchronized (mNetworkRequestLock) {
            if (mNetworkRequestInProgress) {
                if (CMAccount.DEBUG) Log.d(TAG, "Another network request is in progress, scheduling retry.");
                scheduleRetry();
                return;
            }
            mNetworkRequestInProgress = true;
        }
        AddPublicKeysRequestBody requestBody = new AddPublicKeysRequestBody(mContext, keyPairs);
        mAuthClient.addPublicKeys(requestBody, this, this);
    }

    private void removePublicKeys(AddPublicKeysResponse response) {
        List<ECKeyPair> keyPairs = getKeyPairs();
        for (ECKeyPair keyPair : keyPairs) {
            String keyId = keyPair.getKeyId();
            if (!response.getKeyIds().contains(keyId)) {
                if (CMAccount.DEBUG) Log.d(TAG, "Removing public key_id " + keyId);
                String selection = CMAccountProvider.ECDHKeyStoreColumns.KEY_ID + " = ?";
                String[] selectionArgs = new String[] { keyId };
                mContext.getContentResolver().delete(CMAccountProvider.ECDH_CONTENT_URI, selection, selectionArgs);
            }
        }

        // If after removing public keys, we are left with no keys, generate some more.
        if (getKeyCount() < MINIMUM_KEYS) {
            if (CMAccount.DEBUG) Log.d(TAG, "Left without enough keys after removing stale keys, generating more.");
            start(mIntent);
        }
    }

    @Override
    public void onErrorResponse(VolleyError volleyError) {
        synchronized (mNetworkRequestLock) {
            mNetworkRequestInProgress = false;
        }
        if (CMAccount.DEBUG) volleyError.printStackTrace();
        handleError();
    }

    @Override
    public void onResponse(AddPublicKeysResponse response) {
        if (response.statusCode == 200) {
            removePublicKeys(response);
            CMAccountUtils.resetBackoff(mAuthClient.getEncryptionPreferences());
        } else {
            handleError();
        }
        synchronized (mNetworkRequestLock) {
            mNetworkRequestInProgress = false;
        }
    }

    private void handleError() {
        scheduleRetry();
    }

    private void scheduleRetry() {
        final Context context = mContext.getApplicationContext();
        mIntent.putExtra(ECDHKeyService.EXTRA_RETRY, true);
        CMAccountUtils.scheduleRetry(context, mAuthClient.getEncryptionPreferences(), mIntent);
    }
}
