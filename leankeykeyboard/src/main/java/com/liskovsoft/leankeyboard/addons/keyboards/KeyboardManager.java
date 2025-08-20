package com.liskovsoft.leankeyboard.addons.keyboards;

import android.content.Context;
import android.inputmethodservice.Keyboard;

import com.liskovsoft.leankeyboard.addons.keyboards.intkeyboards.ResKeyboardFactory;
import com.liskovsoft.leankeyboard.addons.keyboards.intkeyboards.ResKeyboardFactory.ResKeyboardBuilder;

import java.util.ArrayList;
import java.util.List;

public class KeyboardManager {
    private final Context mContext;
    private final KeyboardStateManager mStateManager;
    private List<? extends KeyboardBuilder> mKeyboardBuilders;
    private List<KeyboardData> mAllKeyboards;
    private final KeyboardFactory mKeyboardFactory;
    private int mKeyboardIndex = 0;

    // global lang code
    private static String sCurrentLangCode;
    public static String getGlobalCurrentLangCode() {
        return sCurrentLangCode;
    }
    private static void setGlobalCurrentLangCode(String code) {
        sCurrentLangCode = code;
    }

    public static class KeyboardData {
        public Keyboard abcKeyboard;
        public Keyboard symKeyboard;
        public Keyboard numKeyboard;
        public String langCode;
    }

    public KeyboardManager(Context ctx) {
        mContext = ctx;
        mStateManager = new KeyboardStateManager(mContext, this);
        mKeyboardFactory = new ResKeyboardFactory(mContext);
        mStateManager.restore();
    }

    public void load() {
        mKeyboardBuilders = mKeyboardFactory.getAllAvailableKeyboards(mContext);
        mAllKeyboards = buildAllKeyboards();
        updateLangFromIndex();
    }

    private List<KeyboardData> buildAllKeyboards() {
        List<KeyboardData> keyboards = new ArrayList<>();
        if (mKeyboardBuilders != null && !mKeyboardBuilders.isEmpty()) {
            for (KeyboardBuilder builder : mKeyboardBuilders) {
                KeyboardData data = new KeyboardData();
                data.abcKeyboard = builder.createAbcKeyboard();
                data.symKeyboard = builder.createSymKeyboard();
                data.numKeyboard = builder.createNumKeyboard();

                if (builder instanceof ResKeyboardBuilder) {
                    data.langCode = ((ResKeyboardBuilder) builder).getLangCode();
                }

                keyboards.add(data);
            }
        }
        return keyboards;
    }

    private void onNextKeyboard() {
        mStateManager.onNextKeyboard();
    }

    public KeyboardData next() {
        if (mKeyboardFactory.needUpdate() || mAllKeyboards == null) {
            load();
        }

        ++mKeyboardIndex;
        if (mAllKeyboards != null && mKeyboardIndex >= mAllKeyboards.size()) {
            mKeyboardIndex = 0;
        }

        KeyboardData kbd = mAllKeyboards.get(mKeyboardIndex);
        if (kbd == null) {
            throw new IllegalStateException(String.format("Keyboard %s not initialized", mKeyboardIndex));
        }

        onNextKeyboard();

        updateLangFromIndex();
        return kbd;
    }

    public int getIndex() {
        return mKeyboardIndex;
    }

    public void setIndex(int idx) {
        mKeyboardIndex = idx;
        if (mAllKeyboards != null && !mAllKeyboards.isEmpty()) {
            if (mKeyboardIndex < 0 || mKeyboardIndex >= mAllKeyboards.size()) {
                mKeyboardIndex = 0;
            }
        }
        updateLangFromIndex();
    }


    public KeyboardData get() {
        if (mAllKeyboards == null) {
            load();
        }
        if (mAllKeyboards.size() <= mKeyboardIndex) {
            mKeyboardIndex = 0;
        }
        KeyboardData current = mAllKeyboards.get(mKeyboardIndex);
        updateLangFromIndex();
        return current;
    }


    private void updateLangFromIndex() {
        if (mKeyboardBuilders == null || mKeyboardBuilders.isEmpty()) return;
        if (mKeyboardIndex < 0 || mKeyboardIndex >= mKeyboardBuilders.size()) return;

        KeyboardBuilder builder = mKeyboardBuilders.get(mKeyboardIndex);
        String code = null;

        if (builder instanceof ResKeyboardBuilder) {
            code = ((ResKeyboardBuilder) builder).getLangCode();
        } else if (mAllKeyboards != null
                && mKeyboardIndex < mAllKeyboards.size()
                && mAllKeyboards.get(mKeyboardIndex) != null) {

            code = mAllKeyboards.get(mKeyboardIndex).langCode;
        }

        if (code != null) {
            setGlobalCurrentLangCode(code);
        }
    }
}
