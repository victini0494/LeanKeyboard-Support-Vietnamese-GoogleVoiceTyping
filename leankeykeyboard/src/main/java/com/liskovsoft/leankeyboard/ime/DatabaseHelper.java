package com.liskovsoft.leankeyboard.ime;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

import java.util.ArrayList;
import java.util.List;


public class DatabaseHelper extends SQLiteAssetHelper {

    private static final String TAG = "DatabaseHelper";

    public static final int MODE_STARTS_WITH = 1;
    public static final int MODE_FIRST_SYLLABLE_OF_PHRASE = 2;
    private static final int MAX_SUGGESTIONS = 6;

    private static final String DATABASE_NAME = "dictionary_vn.db";
    private static final String TABLE_NAME = "DictionaryVN";
    private static final int DATABASE_VERSION = 1;
    private static final String COL_WORD = "word";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        setForcedUpgrade();
    }

    public List<String> getSuggestions(String prefix, int searchMode) {
        List<String> suggestions = new ArrayList<>();
        if (prefix == null || prefix.trim().isEmpty()) {
            return suggestions;
        }

        SQLiteDatabase db = getReadableDatabase();
        if (db == null) return suggestions;

        String query;
        String[] selectionArgs;

        switch (searchMode) {
            case MODE_FIRST_SYLLABLE_OF_PHRASE:
                query = "SELECT " + COL_WORD + " FROM " + TABLE_NAME +
                        " WHERE " + COL_WORD + " MATCH '^' || ? AND " + COL_WORD + " LIKE ? " +
                        "ORDER BY length(" + COL_WORD + ") LIMIT " + MAX_SUGGESTIONS;
                selectionArgs = new String[]{ prefix + "*", prefix + " %" };

                try (Cursor cursor = db.rawQuery(query, selectionArgs)) {
                    if (cursor != null) {
                        final int prefixLength = prefix.length();
                        while (cursor.moveToNext()) {
                            String fullPhrase = cursor.getString(0);
                            if (fullPhrase.length() > prefixLength + 1) {
                                suggestions.add(fullPhrase.substring(prefixLength + 1));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error when querying suggestions (Mode 2)", e);
                }
                break;

            case MODE_STARTS_WITH:
            default:
                query = "SELECT " + COL_WORD + " FROM " + TABLE_NAME +
                        " WHERE " + COL_WORD + " MATCH '^' || ? " +
                        "ORDER BY (instr(" + COL_WORD + ", ' ') > 0), length(" + COL_WORD + ") LIMIT " + MAX_SUGGESTIONS;
                selectionArgs = new String[]{ prefix + "*" };

                try (Cursor cursor = db.rawQuery(query, selectionArgs)) {
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            suggestions.add(cursor.getString(0));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error when querying suggestions (Mode 1)", e);
                }
                break;
        }

        return suggestions;
    }
}