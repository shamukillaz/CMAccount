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

package com.cyanogenmod.account.api;

import com.cyanogenmod.account.api.request.SendChannelRequestBody;
import com.cyanogenmod.account.util.CMAccountUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.cyanogenmod.account.CMAccount;
import com.cyanogenmod.account.auth.AuthClient;

import android.accounts.Account;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

public class DeviceFinderService extends Service implements LocationListener,
        GooglePlayServicesClient.ConnectionCallbacks,  GooglePlayServicesClient.OnConnectionFailedListener,
        Response.Listener<Integer>, Response.ErrorListener {

    private static final String TAG = DeviceFinderService.class.getSimpleName();
    private static PowerManager.WakeLock sWakeLock;

    private static final String EXTRA_ACCOUNT = "account";
    private static final String EXTRA_KEY_ID = "key_id";

    private static final int LOCATION_UPDATE_INTERVAL = 5000;
    private static final int MAX_LOCATION_UPDATES = 10;
    private static final int LOCATION_ACCURACY_THRESHOLD = 5; //meters

    private LocationClient mLocationClient;
    private Location mLastLocationUpdate;
    private AuthClient mAuthClient;
    private String mKeyId;

    private int mUpdateCount = 0;

    private boolean mIsRunning = false;

    public static void reportLocation(Context context, Account account, final String keyId) {
        if (sWakeLock == null) {
            PowerManager pm = (PowerManager)
                    context.getSystemService(Context.POWER_SERVICE);
            sWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }
        if (!sWakeLock.isHeld()) {
            if (CMAccount.DEBUG) Log.v(TAG, "Acquiring wakelock");
            sWakeLock.acquire();
        }
        Intent intent = new Intent(context, DeviceFinderService.class);
        intent.putExtra(EXTRA_ACCOUNT, account);
        intent.putExtra(EXTRA_KEY_ID, keyId);
        context.startService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mIsRunning) {
            final Context context = getApplicationContext();
            mIsRunning = true;
            final ContentResolver contentResolver = getContentResolver();
            boolean gpsEnabled = Settings.Secure.isLocationProviderEnabled(
                    contentResolver, LocationManager.GPS_PROVIDER);
            if (!gpsEnabled) {
                Settings.Secure.setLocationProviderEnabled(contentResolver,
                    LocationManager.GPS_PROVIDER, true);
            }
            mAuthClient = AuthClient.getInstance(context);
            mLocationClient = new LocationClient(context, this, this);
            mLocationClient.connect();
        }

        // Reset the session
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) mKeyId = extras.getString(EXTRA_KEY_ID);
        }

        if (mLocationClient.isConnected()) {
            restartLocationUpdates();
        }

        return START_STICKY;
    }

    private LocationRequest getLocationRequest() {
        return LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(LOCATION_UPDATE_INTERVAL)
                .setNumUpdates(MAX_LOCATION_UPDATES);
    }

    private void restartLocationUpdates() {
        mUpdateCount = 0;
        if (CMAccount.DEBUG) Log.d(TAG, "Starting new LocationRequest");

        Location lastLocation = mLocationClient.getLastLocation();
        if (lastLocation != null) {
            onLocationChanged(lastLocation, true);
        }
        mLocationClient.requestLocationUpdates(getLocationRequest(), this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sWakeLock != null) {
            if (CMAccount.DEBUG) Log.v(TAG, "Releasing wakelock");
            sWakeLock.release();
        }
        mIsRunning = false;
    }

    @Override
    public void onLocationChanged(final Location location) {
        onLocationChanged(location, false);
    }

    private void onLocationChanged(final Location location, boolean fromLastLocation) {
        if (CMAccount.DEBUG) Log.v(TAG, "onLocationChanged() " + location.toString());
        mLastLocationUpdate = location;
        if (!fromLastLocation) mUpdateCount++;

        SendChannelRequestBody sendChannelRequestBody = new SendChannelRequestBody(getApplicationContext(), mKeyId, location);
        mAuthClient.sendChannel(sendChannelRequestBody, this, this);
    }

    @Override
    public void onConnected(Bundle bundle) {
        restartLocationUpdates();
    }

    @Override
    public void onDisconnected() {}

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        stopSelf();
    }

    @Override
    public void onResponse(Integer status) {
        if (CMAccount.DEBUG) Log.v(TAG, "Successfully posted location");
        if (mLastLocationUpdate != null) {
            maybeStopLocationUpdates(mLastLocationUpdate.getAccuracy());
        }
    }

    @Override
    public void onErrorResponse(VolleyError volleyError) {
        if (volleyError.networkResponse != null) {
            int statusCode = volleyError.networkResponse.statusCode;
            if (CMAccount.DEBUG) Log.v(TAG, "Location post error status = "+ statusCode);
        } else {
            if (CMAccount.DEBUG) Log.v(TAG, "Location post error, no network response");
        }
        volleyError.printStackTrace();
        mLocationClient.disconnect();
        stopSelf();
    }

    private void maybeStopLocationUpdates(float accuracy) {
        if (CMAccount.DEBUG) Log.v(TAG, "Update count = "+ mUpdateCount);
        // if mUpdateCount, then this is a case we have the last known location. Don't stop in that case.
        if ((mUpdateCount != 0) && (accuracy <= LOCATION_ACCURACY_THRESHOLD || mUpdateCount == MAX_LOCATION_UPDATES)) {
            stopUpdates();
        }
    }

    private void stopUpdates() {
        if (CMAccount.DEBUG) Log.v(TAG, "Stopping location updates");
        mLocationClient.removeLocationUpdates(this);
        mLocationClient.disconnect();
        stopSelf();
    }
}
