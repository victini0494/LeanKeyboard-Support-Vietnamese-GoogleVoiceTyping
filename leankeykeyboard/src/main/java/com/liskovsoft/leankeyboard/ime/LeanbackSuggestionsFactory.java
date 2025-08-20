package com.liskovsoft.leankeyboard.ime;

import java.util.ArrayList;
import java.util.List;

public class LeanbackSuggestionsFactory {

    private final ArrayList<String> mSuggestions = new ArrayList<>();

    public LeanbackSuggestionsFactory() {
        clearSuggestions();
    }

    public void setSuggestions(List<String> newSuggestions) {
        clearSuggestions();

        if (newSuggestions != null && !newSuggestions.isEmpty()) {
            mSuggestions.addAll(newSuggestions);
        }
    }

    public ArrayList<String> getSuggestions() {
        return mSuggestions;
    }

    public void clearSuggestions() {
        mSuggestions.clear();
        mSuggestions.add(null);
    }
}