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

package com.cyanogenmod.account.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Binder;
import android.text.TextUtils;
import android.util.Log;
import com.cyanogenmod.account.CMAccount;

import java.util.HashMap;

public class CMAccountProvider extends ContentProvider {

    private static String TAG = CMAccountProvider.class.getSimpleName();
    public static final String AUTHORITY = "com.cyanogenmod.account.store";
    private static final String SYMMETRIC_KEY_PATH = "symmetric_key";
    private static final String ECDH_KEY_PATH = "ecdh_key";
    public static final Uri SYMMETRIC_KEY_CONTENT_URI = Uri.parse("content://" + AUTHORITY).buildUpon().appendPath(SYMMETRIC_KEY_PATH).build();
    public static final Uri ECDH_CONTENT_URI = Uri.parse("content://" + AUTHORITY).buildUpon().appendPath(ECDH_KEY_PATH).build();

    private static final String TABLE_SYMMETRIC_KEYS = "symmetric_keys";
    private static final String TABLE_ECDH_KEYS = "ecdh_keys";
    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int SYMMETRIC_KEY = 1;
    private static final int SYMMETRIC_KEY_ID = 2;
    private static final int ECDH_KEY = 3;
    public static final int ECDH_KEY_ID = 4;

    private static HashMap<String, String> sSymmetricKeyProjectionMap;
    private static HashMap<String, String> sECDHKeyProjectionMap;

    static {
        URI_MATCHER.addURI(AUTHORITY, SYMMETRIC_KEY_PATH, SYMMETRIC_KEY);
        URI_MATCHER.addURI(AUTHORITY, SYMMETRIC_KEY_PATH + "/#", SYMMETRIC_KEY_ID);
        URI_MATCHER.addURI(AUTHORITY, ECDH_KEY_PATH, ECDH_KEY);
        URI_MATCHER.addURI(AUTHORITY, ECDH_KEY_PATH + "/#", ECDH_KEY_ID);

        sSymmetricKeyProjectionMap = new HashMap<String, String>();
        sSymmetricKeyProjectionMap.put(SymmetricKeyStoreColumns._ID, SymmetricKeyStoreColumns._ID);
        sSymmetricKeyProjectionMap.put(SymmetricKeyStoreColumns.KEY_ID, SymmetricKeyStoreColumns.KEY_ID);
        sSymmetricKeyProjectionMap.put(SymmetricKeyStoreColumns.KEY, SymmetricKeyStoreColumns.KEY);
        sSymmetricKeyProjectionMap.put(SymmetricKeyStoreColumns.LOCAL_SEQUENCE, SymmetricKeyStoreColumns.LOCAL_SEQUENCE);
        sSymmetricKeyProjectionMap.put(SymmetricKeyStoreColumns.REMOTE_SEQUENCE, SymmetricKeyStoreColumns.REMOTE_SEQUENCE);
        sSymmetricKeyProjectionMap.put(SymmetricKeyStoreColumns.EXPIRATION, SymmetricKeyStoreColumns.EXPIRATION);

        sECDHKeyProjectionMap = new HashMap<String, String>();
        sECDHKeyProjectionMap.put(ECDHKeyStoreColumns._ID, ECDHKeyStoreColumns._ID);
        sECDHKeyProjectionMap.put(ECDHKeyStoreColumns.KEY_ID, ECDHKeyStoreColumns.KEY_ID);
        sECDHKeyProjectionMap.put(ECDHKeyStoreColumns.PRIVATE, ECDHKeyStoreColumns.PRIVATE);
        sECDHKeyProjectionMap.put(ECDHKeyStoreColumns.PUBLIC, ECDHKeyStoreColumns.PUBLIC);
    }
    private SQLiteOpenHelper mOpenHelper;


    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        cleanUpExpiredSymmetricKeys();
        return true;
    }

    private void cleanUpExpiredSymmetricKeys() {
        if (CMAccount.DEBUG) Log.d(TAG, "Cleaning up expired symmetric keys");
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.execSQL("delete from " + TABLE_SYMMETRIC_KEYS + " where " + SymmetricKeyStoreColumns.EXPIRATION + " < datetime('now', 'localtime')");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (Binder.getCallingPid() != android.os.Process.myPid()) {
            throw new SecurityException("Cannot read from this provider");
        }
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (URI_MATCHER.match(uri)) {
            case SYMMETRIC_KEY:
                qb.setTables(TABLE_SYMMETRIC_KEYS);
                qb.setProjectionMap(sSymmetricKeyProjectionMap);
                break;
            case SYMMETRIC_KEY_ID:
                qb.setTables(TABLE_SYMMETRIC_KEYS);
                qb.setProjectionMap(sSymmetricKeyProjectionMap);
                qb.appendWhere(SymmetricKeyStoreColumns._ID + "=" + uri.getPathSegments().get(1));
                break;
            case ECDH_KEY:
                qb.setTables(TABLE_ECDH_KEYS);
                qb.setProjectionMap(sECDHKeyProjectionMap);
                break;
            case ECDH_KEY_ID:
                qb.setTables(TABLE_ECDH_KEYS);
                qb.setProjectionMap(sECDHKeyProjectionMap);
                qb.appendWhere(ECDHKeyStoreColumns._ID + "=" + uri.getPathSegments().get(1));
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        int type = URI_MATCHER.match(uri);
        switch (type) {
            case SYMMETRIC_KEY:
                return SymmetricKeyStoreColumns.CONTENT_TYPE;
            case SYMMETRIC_KEY_ID:
                return SymmetricKeyStoreColumns.CONTENT_ITEM_TYPE;
            case ECDH_KEY:
                return ECDHKeyStoreColumns.CONTENT_TYPE;
            case ECDH_KEY_ID:
                return ECDHKeyStoreColumns.CONTENT_TYPE_ITEM;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (Binder.getCallingPid() != android.os.Process.myPid()) {
            throw new SecurityException("Cannot insert into this provider");
        }
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        switch (URI_MATCHER.match(uri)) {
            case SYMMETRIC_KEY:
                long rowId = db.insert(TABLE_SYMMETRIC_KEYS, null, values);
                if (rowId != -1) {
                    Uri newUri = ContentUris.withAppendedId(uri, rowId);
                    getContext().getContentResolver().notifyChange(newUri, null);
                    return newUri;
                }
                break;
            case ECDH_KEY:
                rowId = db.insert(TABLE_ECDH_KEYS, null, values);
                if (rowId != -1) {
                    Uri newUri = ContentUris.withAppendedId(uri, rowId);
                    getContext().getContentResolver().notifyChange(newUri, null);
                    return newUri;
                }
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (Binder.getCallingPid() != android.os.Process.myPid()) {
            throw new SecurityException("Cannot delete from this provider");
        }
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = 0;
        switch (URI_MATCHER.match(uri)) {
            case SYMMETRIC_KEY:
                count = db.delete(TABLE_SYMMETRIC_KEYS, selection, selectionArgs);
                break;
            case SYMMETRIC_KEY_ID:
                count = db.delete(TABLE_SYMMETRIC_KEYS, SymmetricKeyStoreColumns._ID + "=" + ContentUris.parseId(uri)
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ")" : ""), selectionArgs);
                break;
            case ECDH_KEY:
                count = db.delete(TABLE_ECDH_KEYS, selection, selectionArgs);
                break;
            case ECDH_KEY_ID:
                count = db.delete(TABLE_ECDH_KEYS, ECDHKeyStoreColumns._ID + "=" + ContentUris.parseId(uri)
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ")" : ""), selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (Binder.getCallingPid() != android.os.Process.myPid()) {
            throw new SecurityException("Cannot insert into this provider");
        }
        switch (URI_MATCHER.match(uri)) {
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    public static void incrementSequence(Context context, String column, String keyId) {
        SQLiteOpenHelper openHelper = new DatabaseHelper(context.getApplicationContext());
        SQLiteDatabase db = openHelper.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_SYMMETRIC_KEYS + " SET " + column + " = " + column + " + 1 WHERE " + SymmetricKeyStoreColumns.KEY_ID + " = ?;", new String[]{ keyId });
        openHelper.close();
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        private static final String DATABASE_NAME = "cmaccount.db";
        private static final int DATABASE_VERSION = 7;

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_SYMMETRIC_KEYS
                    + " ("
                    + SymmetricKeyStoreColumns._ID + " INTEGER PRIMARY KEY, "
                    + SymmetricKeyStoreColumns.KEY + " TEXT NOT NULL, "
                    + SymmetricKeyStoreColumns.LOCAL_SEQUENCE + " INTEGER NOT NULL DEFAULT 1, "
                    + SymmetricKeyStoreColumns.REMOTE_SEQUENCE + " INTEGER NOT NULL DEFAULT 1, "
                    + SymmetricKeyStoreColumns.EXPIRATION + " DATETIME DEFAULT 0, "
                    + SymmetricKeyStoreColumns.KEY_ID + " TEXT NOT NULL UNIQUE);");

            db.execSQL("create trigger update_expiration after insert on " + TABLE_SYMMETRIC_KEYS +
                    " begin update " + TABLE_SYMMETRIC_KEYS + " set " + SymmetricKeyStoreColumns.EXPIRATION +
                    "= datetime('now', '+60 minutes', 'localtime') where expiration = 0" +
                    "; end");

            db.execSQL("CREATE TABLE " + TABLE_ECDH_KEYS
                    + " ("
                    + ECDHKeyStoreColumns._ID + " INTEGER PRIMARY KEY, "
                    + ECDHKeyStoreColumns.KEY_ID + " TEXT NOT NULL UNIQUE, "
                    + ECDHKeyStoreColumns.PRIVATE + " TEXT NOT NULL, "
                    + ECDHKeyStoreColumns.PUBLIC + " TEXT NOT NULL);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SYMMETRIC_KEYS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ECDH_KEYS);
            onCreate(db);
        }
    }

    public static interface SymmetricKeyStoreColumns {
        public static final String _ID = "_id";
        public static final String KEY_ID = "key_id";
        public static final String KEY = "symmetric_key";
        public static final String EXPIRATION = "expiration";
        public static final String LOCAL_SEQUENCE = "local_sequence";
        public static final String REMOTE_SEQUENCE = "remote_sequence";
        public static final String CONTENT_TYPE = "vnd.cyanogenmod.cursor.dir/symmetricKey";
        public static final String CONTENT_ITEM_TYPE = "vnd.cyanogenmod.cursor.item/symmetricKey";
    }

    public static interface ECDHKeyStoreColumns {
        public static final String _ID = "_id";
        public static final String KEY_ID = "key_id";
        public static final String PRIVATE = "private";
        public static final String PUBLIC = "public";
        public static final String CONTENT_TYPE = "vnd.cyanogenmod.cursor.dir/publicKey";
        public static final String CONTENT_TYPE_ITEM = "vnd.cyanogenmod.cursor.item/publicKey";
    }
}
