package com.liskovsoft.leankeyboard.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import com.liskovsoft.leankeyboard.addons.keyboards.KeyboardManager;

import java.util.ArrayList;
import java.util.Locale;

public class GoogleVoiceInputActivity extends Activity {
    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startVoiceRecognition();
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String langCode = normalizeLanguageCode(KeyboardManager.getGlobalCurrentLangCode());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode);
        try {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Google Voice Typing not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                sendVoiceTextToIme(text);
            }
        }
        finish();
    }

    private void sendVoiceTextToIme(String text) {
        Intent intent = new Intent("inputmethod.ACTION_VOICE_TO_TEXT");
        intent.putExtra("inputmethod.EXTRA_VOICE_TO_TEXT", text);
        sendBroadcast(intent);
    }

    public static String normalizeLanguageCode(String shortCode) {
        if (shortCode == null) {
            return "en-US";
        }
        switch (shortCode.toLowerCase()) {
            case "vi":
                return "vi-VN";
            case "ru":
                return "ru-RU";
            case "uk":
                return "uk-UA";
            case "de":
                return "de-DE";
            case "bg":
                return "bg-BG";
            case "nl":
                return "nl-NL";
            case "fr":
                return "fr-FR";
            case "da":
                return "da-DK";
            case "el":
                return "el-GR";
            case "is":
                return "is-IS";
            case "it":
                return "it-IT";
            case "sv":
                return "sv-SE";
            case "es_us":
                return "es-US";
            case "ro":
                return "ro-RO";
            case "sl":
                return "sl-SI";
            case "ar":
                return "ar-SA";
            case "he":
                return "he-IL";
            case "fa":
                return "fa-IR";
            case "th":
                return "th-TH";
            case "tr":
                return "tr-TR";
            case "ko_kr":
                return "ko-KR";
            case "ka":
                return "ka-GE";
            default:
                return "en-US";
        }
    }

}
