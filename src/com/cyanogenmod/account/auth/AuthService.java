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

package com.cyanogenmod.account.auth;

import com.cyanogenmod.account.CMAccount;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;


public class AuthService extends Service {
    private static final String TAG = "AuthService";
    private Authenticator mAuthenticator;

    @Override
    public void onCreate() {
        if (CMAccount.DEBUG) Log.d(TAG, "CMAccount Auth Service started.");
        mAuthenticator = new Authenticator(this);
    }

    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }
}
