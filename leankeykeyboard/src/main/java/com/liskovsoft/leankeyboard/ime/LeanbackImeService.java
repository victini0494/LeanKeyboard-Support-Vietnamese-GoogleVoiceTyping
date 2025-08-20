package com.liskovsoft.leankeyboard.ime;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

import androidx.core.text.BidiFormatter;

import com.liskovsoft.leankeyboard.addons.keyboards.KeyboardManager;
import com.liskovsoft.leankeyboard.addons.keyboards.intkeyboards.ResKeyboardInfo;
import com.liskovsoft.leankeyboard.ime.LeanbackKeyboardController.InputListener;
import com.liskovsoft.leankeyboard.utils.LeanKeyPreferences;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LeanbackImeService extends KeyMapperImeService {
    private static final String TAG = LeanbackImeService.class.getSimpleName();
    private static final boolean DEBUG = false;
    public static final String IME_CLOSE = "com.google.android.athome.action.IME_CLOSE";
    public static final String IME_OPEN = "com.google.android.athome.action.IME_OPEN";
    public static final int MAX_SUGGESTIONS = 10;
    static final int MODE_FREE_MOVEMENT = 1;
    static final int MODE_TRACKPAD_NAVIGATION = 0;
    private static final int MSG_SUGGESTIONS_CLEAR = 123;
    private static final int SUGGESTIONS_CLEAR_DELAY = 1000;
    private boolean mEnterSpaceBeforeCommitting;
    private View mInputView;
    private LeanbackKeyboardController mKeyboardController;
    private boolean mShouldClearSuggestions = true;
    private LeanbackSuggestionsFactory mSuggestionsFactory;
    public static final String COMMAND_RESTART = "restart";
    private boolean mForceShowKbd;
    private DatabaseHelper dbHelper;
    private ExecutorService executorService;
    private Handler mainThreadHandler;
    private Future<?> suggestionFuture;
    LeanbackKeyboardContainer mContainer;

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == MSG_SUGGESTIONS_CLEAR && mShouldClearSuggestions) {
                mSuggestionsFactory.clearSuggestions();
                mKeyboardController.updateSuggestions(mSuggestionsFactory.getSuggestions());
                mShouldClearSuggestions = false;
            }

        }
    };

    private InputListener mInputListener = this::handleTextEntry;

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    public LeanbackImeService() {
        if (VERSION.SDK_INT < 21 && !enableHardwareAcceleration()) {
            Log.w("LbImeService", "Could not enable hardware acceleration");
        }
    }

    @Override
    public void onCreate() {
        //setupDensity();
        super.onCreate();

        Log.d(TAG, "onCreate");
        initSettings();
        dbHelper = new DatabaseHelper(this);
        executorService = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    private void setupDensity() {
        if (LeanKeyPreferences.instance(this).getEnlargeKeyboard()) {
            DisplayMetrics metrics = LeanbackUtils.createMetricsFrom(this, 1.3f);

            if (metrics != null) {
                getResources().getDisplayMetrics().setTo(metrics);
            }
        }
    }

    private void initSettings() {
        LeanKeyPreferences prefs = LeanKeyPreferences.instance(this);
        mForceShowKbd = prefs.getForceShowKeyboard();

        if (mKeyboardController != null) {
            mKeyboardController.setSuggestionsEnabled(prefs.getSuggestionsEnabled());
        }
    }

    private void clearSuggestionsDelayed() {
//        if (!mSuggestionsFactory.shouldSuggestionsAmend()) {
//            mHandler.removeMessages(MSG_SUGGESTIONS_CLEAR);
//            mShouldClearSuggestions = true;
//            mHandler.sendEmptyMessageDelayed(MSG_SUGGESTIONS_CLEAR, SUGGESTIONS_CLEAR_DELAY);
//        }

    }

    public void handleTextEntry(final int type, final int keyCode, final CharSequence text) {
        final InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            boolean updateSuggestions = true;
            switch (type) {
                case InputListener.ENTRY_TYPE_STRING:
                    clearSuggestionsDelayed();
                    if (mEnterSpaceBeforeCommitting && mKeyboardController.enableAutoEnterSpace()) {
                        if (LeanbackUtils.isAlphabet(keyCode)) {
                            connection.commitText(" ", 1);
                        }

                        mEnterSpaceBeforeCommitting = false;
                    }

                    // user input from keyboard
                    String langCode = KeyboardManager.getGlobalCurrentLangCode();
                    if (langCode.equals("vi")) {
                        TelexProcessor.processCurrentWord(getCurrentInputConnection(),text);
                        fetchAutocompleteSuggestions();
                    } else {
                        connection.commitText(text, 1);
                    }


                    updateSuggestions = true;
                    if (keyCode == LeanbackKeyboardView.ASCII_PERIOD) {
                        mEnterSpaceBeforeCommitting = true;
                    }
                    break;
                case InputListener.ENTRY_TYPE_BACKSPACE:
                    clearSuggestionsDelayed();
                    connection.deleteSurroundingText(1, 0);
                    String langCode3 = KeyboardManager.getGlobalCurrentLangCode();
                    if (langCode3.equals("vi")) {
                        fetchAutocompleteSuggestions();
                    }
                    mEnterSpaceBeforeCommitting = false;
                    updateSuggestions = true;
                    break;
                case InputListener.ENTRY_TYPE_SUGGESTION:
                case InputListener.ENTRY_TYPE_VOICE:
                    clearSuggestionsDelayed();

                    String langCode2 = KeyboardManager.getGlobalCurrentLangCode();
                    if (langCode2.equals("vi")) {

                        String beforeCursor = Objects.requireNonNull(connection.getTextBeforeCursor(30, 0)).toString();
                        if (beforeCursor.isEmpty()) {
                            connection.commitText(text + " ", 1);
                            break;
                        }

                        char lastChar = beforeCursor.charAt(beforeCursor.length() - 1);
                        if (lastChar == ' ') {
                            connection.commitText(text + " ", 1);
                        } else {
                            int lastSpace = beforeCursor.lastIndexOf(' ');
                            if (lastSpace == -1) {
                                connection.deleteSurroundingText(beforeCursor.length(), 0);
                            } else {
                                int wordStart = lastSpace + 1;
                                int wordLength = beforeCursor.length() - wordStart;
                                connection.deleteSurroundingText(wordLength, 0);
                            }
                            connection.commitText(text + " ", 1);
                        }
                        fetchAutocompleteSuggestions();
                    } else {
//                        if (!mSuggestionsFactory.shouldSuggestionsAmend()) {
//                            connection.deleteSurroundingText(LeanbackUtils.getCharLengthBeforeCursor(connection), LeanbackUtils.getCharLengthAfterCursor(connection));
//                        } else {
//                            int location = LeanbackUtils.getAmpersandLocation(connection);
//                            connection.setSelection(location, location);
//                            connection.deleteSurroundingText(0, LeanbackUtils.getCharLengthAfterCursor(connection));
//                        }
                        connection.commitText(text + " ", 1);
                        mEnterSpaceBeforeCommitting = true;
                    }
                    break;
                case InputListener.ENTRY_TYPE_ACTION:  // User presses Go, Send, Search etc
                    boolean result = sendDefaultEditorAction(true);

                    if (result) {
                        hideWindow(); // SmartYouTubeTV: hide kbd on search page fix
                    } else {
                        LeanbackUtils.sendEnterKey(connection);
                    }

                    updateSuggestions = false;
                    break;
                case InputListener.ENTRY_TYPE_LEFT:
                case InputListener.ENTRY_TYPE_RIGHT:
                    BidiFormatter formatter = BidiFormatter.getInstance();

                    CharSequence textBeforeCursor = connection.getTextBeforeCursor(1000, 0);
                    int lenBefore = 0;
                    boolean isRtlBefore = false;
                    //int rtlLenBefore = 0;
                    if (textBeforeCursor != null) {
                        lenBefore = textBeforeCursor.length();
                        isRtlBefore = formatter.isRtl(textBeforeCursor);
                        //rtlLenBefore = LeanbackUtils.getRtlLenBeforeCursor(textBeforeCursor);
                    }

                    CharSequence textAfterCursor = connection.getTextAfterCursor(1000, 0);
                    int lenAfter = 0;
                    //int rtlLenAfter = 0;
                    boolean isRtlAfter = false;
                    if (textAfterCursor != null) {
                        lenAfter = textAfterCursor.length();
                        isRtlAfter = formatter.isRtl(textAfterCursor);
                        //rtlLenAfter = LeanbackUtils.getRtlLenAfterCursor(textAfterCursor);
                    }

                    int index = lenBefore;
                    if (type == InputListener.ENTRY_TYPE_LEFT) {
                        if (lenBefore > 0) {
                            if (!isRtlBefore) {
                                index = lenBefore - 1;
                            } else {
                                if (lenAfter == 0) {
                                    index = 1;
                                } else if (lenAfter == 1) {
                                    index = 0;
                                } else {
                                    index = lenBefore + 1;
                                }
                            }
                        }

                        //Log.d(TAG, String.format("direction key: before: lenBefore=%s, lenAfter=%s, rtlLenBefore=%s, rtlLenAfter=%s", lenBefore, lenAfter, rtlLenBefore, rtlLenAfter));
                        Log.d(TAG, String.format("direction key: before: lenBefore=%s, lenAfter=%s, isRtlBefore=%s", lenBefore, lenAfter, isRtlBefore));
                    } else {
                        if (lenAfter > 0) {
                            if (!isRtlAfter) {
                                index = lenBefore + 1;
                            } else {
                                if (lenBefore == 0) {
                                    index = lenAfter - 1;
                                } else if (lenBefore == 1) {
                                    index = lenAfter + 1;
                                } else {
                                    index = lenBefore - 1;
                                }
                            }
                        }

                        //Log.d(TAG, String.format("direction key: after: lenBefore=%s, lenAfter=%s, rtlLenBefore=%s, rtlLenAfter=%s", lenBefore, lenAfter, rtlLenBefore, rtlLenAfter));
                        Log.d(TAG, String.format("direction key: after: lenBefore=%s, lenAfter=%s, isRtlAfter=%s", lenBefore, lenAfter, isRtlAfter));
                    }

                    Log.d(TAG, "direction key: index: " + index);

                    connection.setSelection(index, index);
                    updateSuggestions = true;
                    break;
                case InputListener.ENTRY_TYPE_DISMISS:
                    connection.performEditorAction(EditorInfo.IME_ACTION_NONE);
                    updateSuggestions = false;
                    break;
                case InputListener.ENTRY_TYPE_VOICE_DISMISS:
                    connection.performEditorAction(EditorInfo.IME_ACTION_GO);
                    updateSuggestions = false;
                    break;
                case InputListener.ENTRY_TYPE_DELETE_WORD:

                    CharSequence textDelete = connection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
                    if (textDelete == null || textDelete == null) {
                        break;
                    }
                    connection.deleteSurroundingText(textDelete.length(), 0);
                    fetchAutocompleteSuggestions();
                    break;
                default:
                    updateSuggestions = true;
            }

            if (mKeyboardController.areSuggestionsEnabled() && updateSuggestions) {
                mKeyboardController.updateSuggestions(mSuggestionsFactory.getSuggestions());
            }
        }
    }

    @Override
    public View onCreateInputView() {
        mInputView = mKeyboardController.getView();
        mInputView.requestFocus();

        return mInputView;
    }
    @Override
    public void onDisplayCompletions(CompletionInfo[] infos) {
        if (mKeyboardController.areSuggestionsEnabled()) {
            mShouldClearSuggestions = false;
            mHandler.removeMessages(123);
//            mSuggestionsFactory.onDisplayCompletions(infos);
            mKeyboardController.updateSuggestions(this.mSuggestionsFactory.getSuggestions());
        }

    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // don't change it (true shows edit dialog above kbd)
    }

    /**
     * At this point, decision whether to show kbd taking place<br/>
     * <a href="https://stackoverflow.com/questions/7449283/is-it-possible-to-have-both-physical-keyboard-and-soft-keyboard-active-at-the-sa">More info</a>
     * @return whether to show kbd
     */
    @Override
    public boolean onEvaluateInputViewShown() {
        Log.d(TAG, "onEvaluateInputViewShown");
        return mForceShowKbd || super.onEvaluateInputViewShown();
    }

    // FireTV fix
    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        Log.d(TAG, "onShowInputRequested");
        return mForceShowKbd || super.onShowInputRequested(flags, configChange);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        sendBroadcast(new Intent(IME_CLOSE));
        mSuggestionsFactory.clearSuggestions();

        // NOTE: Trying to fix kbd without UI bug (telegram)
        reInitKeyboard();
    }

    @SuppressLint("NewApi")
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return isInputViewShown() &&
                (event.getSource() & InputDevice.SOURCE_TOUCH_NAVIGATION) == InputDevice.SOURCE_TOUCH_NAVIGATION &&
                mKeyboardController.onGenericMotionEvent(event) || super.onGenericMotionEvent(event);
    }

    public void hideIme() {
        requestHideSelf(InputMethodService.BACK_DISPOSITION_DEFAULT);
    }

    @Override
    public void onInitializeInterface() {
        mKeyboardController = new LeanbackKeyboardController(this, mInputListener);
        mKeyboardController.setHideWhenPhysicalKeyboardUsed(!mForceShowKbd);
        mEnterSpaceBeforeCommitting = false;
        mSuggestionsFactory = new LeanbackSuggestionsFactory();
        mContainer = mKeyboardController.getContainer();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //// DOESN'T WORK!!!
        //// Hide keyboard on ESC key: https://github.com/yuliskov/SmartYouTubeTV/issues/142
        //event = mapEscToBack(event);
        //keyCode = mapEscToBack(keyCode);

        // Hide keyboard on ESC key: https://github.com/yuliskov/SmartYouTubeTV/issues/142
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            hideIme();
            return true;
        }

        return isInputViewShown() && mKeyboardController.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        //// DOESN'T WORK!!!
        //// Hide keyboard on ESC key: https://github.com/yuliskov/SmartYouTubeTV/issues/142
        //event = mapEscToBack(event);
        //keyCode = mapEscToBack(keyCode);

        return isInputViewShown() && mKeyboardController.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
    }

    private KeyEvent mapEscToBack(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
            // pay attention, you must pass the same action
            event = new KeyEvent(event.getAction(), KeyEvent.KEYCODE_BACK);
        }
        return event;
    }

    private int mapEscToBack(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            keyCode = KeyEvent.KEYCODE_BACK;
        }
        return keyCode;
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent != null) {
            Log.d(TAG, "onStartCommand: " + intent.toUri(0));

            if (intent.getBooleanExtra(COMMAND_RESTART, false)) {
                Log.d(TAG, "onStartCommand: trying to restart service");

                reInitKeyboard();

                return Service.START_REDELIVER_INTENT;
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        mEnterSpaceBeforeCommitting = false;
//        mSuggestionsFactory.onStartInput(info);
        mKeyboardController.onStartInput(info);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        mKeyboardController.onStartInputView();
        sendBroadcast(new Intent(IME_OPEN));
        if (mKeyboardController.areSuggestionsEnabled()) {
//            mSuggestionsFactory.createSuggestions();
            mKeyboardController.updateSuggestions(mSuggestionsFactory.getSuggestions());

            // NOTE: FileManager+ rename item fix: https://t.me/LeanKeyboard/931
            // NOTE: Code below deletes text that has selection.
            //InputConnection connection = getCurrentInputConnection();
            //if (connection != null) {
            //    String text = LeanbackUtils.getEditorText(connection);
            //    connection.deleteSurroundingText(LeanbackUtils.getCharLengthBeforeCursor(connection), LeanbackUtils.getCharLengthAfterCursor(connection));
            //    connection.commitText(text, 1);
            //}
        }

        // when the keyboard interface is about to appear, commit text
        if (pendingVoiceText != null && getCurrentInputConnection() != null) {
            // getCurrentInputConnection().deleteSurroundingText(Integer.MAX_VALUE, Integer.MAX_VALUE);

            String text = pendingVoiceText;
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {

                CharSequence textBefore = ic.getTextBeforeCursor(1, 0);
                if (textBefore != null && textBefore.length() > 0 && !textBefore.toString().equals(" ")) {
                    text = " " + text;
                }

                getCurrentInputConnection().commitText(text, 1);
                pendingVoiceText = null;
                fetchAutocompleteSuggestions();
                mContainer.focusActionButton();
            }
        } else {
            int voiceKeyIndex = mContainer.findKeyIndexByCode(LeanbackKeyboardView.KEYCODE_VOICE);
            if (voiceKeyIndex != -1) {
                mContainer.moveFocusToIndex(voiceKeyIndex, LeanbackKeyboardContainer.KeyFocus.TYPE_MAIN);
            }
        }
    }

    private void fetchAutocompleteSuggestions() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;

        CharSequence textBeforeCursor = connection.getTextBeforeCursor(20, 0);
        if (textBeforeCursor == null) return;

        String text = textBeforeCursor.toString();
        String currentWord = "";
        int mode = 1;

        if (suggestionFuture != null && !suggestionFuture.isDone()) {
            suggestionFuture.cancel(true);
        }

        if (text.trim().isEmpty()) {
            mSuggestionsFactory.setSuggestions(Collections.emptyList());
            mKeyboardController.updateSuggestions(mSuggestionsFactory.getSuggestions());
            return;
        }

        if (text.endsWith(" ")) {
            String trimmedText = text.trim();
            int lastSpaceIndex = trimmedText.lastIndexOf(' ');
            currentWord = trimmedText.substring(lastSpaceIndex + 1);
            mode = 2;
        } else {
            int lastSpaceIndex = text.lastIndexOf(' ');
            currentWord = text.substring(lastSpaceIndex + 1);
            mode = 1;
        }

        final String wordToSuggest = currentWord;
        final int finalMode = mode;

        suggestionFuture = executorService.submit(() -> {
            final List<String> suggestions = dbHelper.getSuggestions(wordToSuggest, finalMode);
            Log.d(TAG, "fetchAutocompleteSuggestions: " + suggestions);
            mainThreadHandler.post(() -> {
                if (Thread.currentThread().isInterrupted()) return;
                mSuggestionsFactory.setSuggestions(suggestions);
                mKeyboardController.updateSuggestions(mSuggestionsFactory.getSuggestions());
            });
        });
    }

    public void onLanguageChanged() {
        Log.d(TAG, "Language changed, clearing suggestions.");

        if (suggestionFuture != null && !suggestionFuture.isDone()) {
            suggestionFuture.cancel(true);
        }

        if (mSuggestionsFactory != null && mKeyboardController != null) {
            mSuggestionsFactory.clearSuggestions();
            mKeyboardController.updateSuggestions(mSuggestionsFactory.getSuggestions());
        }
    }

    private void reInitKeyboard() {
        initSettings();

        if (mKeyboardController != null) {
            mKeyboardController.initKeyboards();
        }
    }
}
