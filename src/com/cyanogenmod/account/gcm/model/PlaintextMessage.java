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

package com.cyanogenmod.account.gcm.model;

import com.google.gson.Gson;

public class PlaintextMessage implements Message {
    public static final String COMMAND_BEGIN_LOCATE = "begin_locate";
    public static final String COMMAND_BEGIN_WIPE = "begin_wipe";
    public static final String COMMAND_KEY_EXCHANGE_FAILED = "key_exchange_failed";
    public static final String COMMAND_PASSWORD_RESET = "password_reset";
    public static final String COMMAND_PUBLIC_KEYS_EXHAUSTED = "public_keys_exhausted";

    private String command;

    public PlaintextMessage(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public String getKeyId() {
        // noop
        return null;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
