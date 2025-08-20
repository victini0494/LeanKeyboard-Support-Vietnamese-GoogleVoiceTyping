package com.liskovsoft.leankeyboard.ime;

import android.util.Log;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.Array;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelexProcessor {

    // type tone mark: sắc (´), huyền (`), hỏi (?), ngã (~), nặng (.)
    private static final ArrayList<String> typeToneMark = new ArrayList<>(Arrays.asList("s", "f", "r", "x", "j"));
    private static final Pattern TONE_PATTERN = Pattern.compile("[\\u0300\\u0301\\u0309\\u0303\\u0323]");

    // type diacritical mark: aa (â), ee (ê), oo (ô), aw (ă), ow (ơ), uw (ư), dd (đ)
    private static final ArrayList<String> typeDiacriticalMark = new ArrayList<>(Arrays.asList("a", "e", "o", "w", "d"));

    // Vietnamese syllable = initialConsonant(optional) + medialGlide(optional) + Vowel + finalConsonant(optional)
    // sorting the array by length in descending order has a significant impact on Vietnamese syllable segmentation.
    private static final ArrayList<String> initialConsonant = new ArrayList<>(Arrays.asList("ngh", "qu", "ch", "gh", "gi", "kh", "ng", "nh", "ph", "th", "tr", "b", "c", "d", "đ", "g", "h", "k", "l", "m", "n", "p", "q", "r", "s", "t", "v", "x")); // add p, qu
    private static final ArrayList<String> medialGlide = new ArrayList<>(Arrays.asList("o", "u"));
    private static final ArrayList<String> vowel = new ArrayList<>(Arrays.asList("ia", "ua", "ưa", "ya", "iê", "yê", "ươ", "uơ", "ưo", "ie", "ye", "uô", "uo", "ưô", "i", "y", "e", "ê", "a", "ă", "â", "u", "ư", "o", "ơ", "ô")); // add ie, ye, ưo, uơ, ưô
    private static final ArrayList<String> finalConsonant = new ArrayList<>(Arrays.asList("ch", "ng", "nh", "p", "t", "c", "m", "n", "u", "o", "i", "y"));
    // simple case for diacritical mark: a,ă + a = â, â + a = a, ...
    private static final Map<Character, Map<Character, Character>> simpleMarkRule = new HashMap<>();
    static {
        simpleMarkRule.put('a', Map.of('a','â'));
        simpleMarkRule.put('ă', Map.of('a','â'));
        simpleMarkRule.put('â', Map.of('a','a'));
        simpleMarkRule.put('e', Map.of('e','ê'));
        simpleMarkRule.put('ê', Map.of('e','e'));
        simpleMarkRule.put('o', Map.of('o','ô'));
        simpleMarkRule.put('ơ', Map.of('o','ô'));
        simpleMarkRule.put('ô', Map.of('o','o'));
    }

    public static void processCurrentWord(InputConnection ic, CharSequence input) {

        if (ic == null || input == null) return;
        // user input more than 1 character, ignore it
        if (input.length() != 1)  { ic.commitText(input, 1); return; }

        String charInput = input.toString();

        int mode;  // 1 is type tone mark, 2 is type diacritical mark, 3 is remove tone mark
        if (typeToneMark.contains(charInput.toLowerCase())) {
            mode = 1;
        } else if (typeDiacriticalMark.contains(charInput.toLowerCase())) {
            mode = 2;
        } else if (charInput.equalsIgnoreCase("z")) {
            mode = 3;
        } else {
            mode = 4; // rearrange tone mark
        }

        // get a maximum of 10 characters before the cursor
        CharSequence beforeCursor = ic.getTextBeforeCursor(10, 0);
        if (beforeCursor == null || beforeCursor.length() == 0) {
            ic.commitText(input, 1);
            return;
        }
        // if the last character is a space, then commit text
        char lastChar = beforeCursor.charAt(beforeCursor.length() - 1);
        if (lastChar == ' ') {
            ic.commitText(input, 1);
            return;
        }

        // get the first word before the cursor
        StringBuilder reverseWord = new StringBuilder();
        for (int i = beforeCursor.length() - 1; i >= 0; i--) {
            char tempChar = beforeCursor.charAt(i);
            if (tempChar == ' ') {
                break;
            } else {
                reverseWord.append(tempChar);
            }
        }
        String word = reverseWord.reverse().toString();

        // ignore word if it's too long
        if (word.length() >= 8) {
            ic.commitText(input, 1);
            return;
        }

        // case: word is only d or đ and user input is d
        if (charInput.equalsIgnoreCase("d") && word.equalsIgnoreCase("d")) {
            ic.beginBatchEdit();
            try {
                ic.deleteSurroundingText(1, 0);
                if (Character.isUpperCase(word.charAt(0))) {
                    ic.commitText("Đ", 1);
                } else {
                    ic.commitText("đ", 1);
                }
            } finally {
                ic.endBatchEdit();
            }
            return;
        }
        if (charInput.equalsIgnoreCase("d") && word.equalsIgnoreCase("đ")) {
            ic.beginBatchEdit();
            try {
                ic.deleteSurroundingText(1, 0);
                if (Character.isUpperCase(word.charAt(0))) {
                    ic.commitText("D", 1);
                } else {
                    ic.commitText("d", 1);
                }
                ic.commitText(charInput, 1);
            } finally {
                ic.endBatchEdit();
            }
            return;
        }

        // extract tone mark from word
        String[] result = extractTone(word);
        String wordWithoutTone = result[0];
        String toneMark = result[1];

        // segment and validate the word
        ArrayList<String> wordSegment = null;
        if (mode != 4) {
            wordSegment = segmentAndValidate(wordWithoutTone);
            if (wordSegment == null) {
                ic.commitText(input, 1);
                return;
            }
        }

        // delete old word and add new word without tone mark
        if (mode == 3 && !toneMark.equals("")) {
            ic.beginBatchEdit();
            try {
                String newText = wordSegment.get(0) + wordSegment.get(1) + wordSegment.get(2) + wordSegment.get(3);
                ic.deleteSurroundingText(newText.length(), 0);
                ic.commitText(newText, 1);
            } finally {
                ic.endBatchEdit();
            }
            return;
        }

        // add tone mark
        if (mode == 1) {
            String newToneMark;
            switch (charInput.toLowerCase()) {
                case "s":   newToneMark = "\u0301"; break;
                case "f": newToneMark = "\u0300"; break;
                case "r":   newToneMark = "\u0309"; break;
                case "x":   newToneMark = "\u0303"; break;
                case "j":  newToneMark = "\u0323"; break;
                default:      newToneMark = ""; break;
            }
            String newVowel = addTone(wordSegment.get(2), newToneMark, toneMark);
            ic.beginBatchEdit();
            try {
                String newText = wordSegment.get(0) + wordSegment.get(1) + newVowel + wordSegment.get(3);
                ic.deleteSurroundingText(newText.length(), 0);
                // if new tone mark is the same as old tone mark, remove old text and add new text without tone mark + user input
                if (newToneMark.equals(toneMark)) {
                    newText += charInput;
                }
                ic.commitText(newText, 1);
            } finally {
                ic.endBatchEdit();
            }
            return;
        }

        // add diacritical mark
        if (mode == 2) {

            String newVowel = wordSegment.get(2);
            String isRemove = "f";
            String newInitial = wordSegment.get(0);

            // case: d + d = đ and reverse
            if (charInput.equalsIgnoreCase("d")) {

                int indexOfd = newInitial.toLowerCase().indexOf("d");
                int indexOfdd = newInitial.toLowerCase().indexOf("đ");

                if (indexOfd != -1) {
                    char charInVowel = newInitial.charAt(indexOfd);
                    if (Character.isUpperCase(charInVowel)) {
                        newInitial = newInitial.replace(charInVowel, 'Đ');
                    } else {
                        newInitial= newInitial.replace(charInVowel, 'đ');
                    }
                } else if (indexOfdd != -1) {
                    char charInVowel = newInitial.charAt(indexOfdd);
                    if (Character.isUpperCase(charInVowel)) {
                        newInitial = newInitial.replace(charInVowel, 'D');
                    } else {
                        newInitial= newInitial.replace(charInVowel, 'd');
                    }
                    isRemove = "t";
                }
                if (toneMark != "") {
                    newVowel = addTone(wordSegment.get(2),toneMark,"");
                }
            } else {
                String[] output  = addDiacriticalMark(wordSegment.get(2), charInput);
                if (output == null && toneMark.equals("")) {
                    ic.commitText(input, 1);
                    return;
                } else if (output == null && !toneMark.equals("")) {
                    mode = 4;
                } else {
                    if (toneMark != "") {
                        newVowel = addTone(output[0],toneMark,"");
                    } else {
                        newVowel = output[0];
                    }
                    isRemove = output[1];
                }
            }

            if (mode != 4) {
                ic.beginBatchEdit();
                try {
                    String newText = newInitial + wordSegment.get(1) + newVowel + wordSegment.get(3);
                    ic.deleteSurroundingText(newText.length(), 0);
                    if (isRemove.equals("t")) {
                        newText += charInput;
                    }
                    ic.commitText(newText, 1);
                } finally {
                    ic.endBatchEdit();
                }
                return;
            }
        }

        // case: rearrange tone mark
        if (mode == 4 && !toneMark.equals("")) {

            String newWordWithoutTone = wordWithoutTone + charInput;
            ArrayList<String> newWordSegment = segmentAndValidate(newWordWithoutTone);
            if (newWordSegment == null) {
                ic.commitText(input, 1);
                return;
            }

            String newVowel = addTone(newWordSegment.get(2), toneMark, "");
            String reArrangeToneWord = newWordSegment.get(0) + newWordSegment.get(1) + newVowel + newWordSegment.get(3);
            String newWord = word + charInput;

            if(!reArrangeToneWord.equals(newWord)) {
                ic.beginBatchEdit();
                try {
                    ic.deleteSurroundingText(word.length(), 0);
                    ic.commitText(reArrangeToneWord, 1);
                } finally {
                    ic.endBatchEdit();
                }
                return;
            } else {
                ic.commitText(input, 1);
                return;
            }

        }

        ic.commitText(input, 1);
    }

    public static String[] extractTone(String word) {
        String normalizedText = Normalizer.normalize(word, Normalizer.Form.NFD);
        Matcher matcher = TONE_PATTERN.matcher(normalizedText);
        String toneMark = "";
        if (matcher.find()) {
            toneMark = matcher.group();
        }
        String wordWithoutToneNFD = TONE_PATTERN.matcher(normalizedText).replaceAll("");
        String wordWithoutTone = Normalizer.normalize(wordWithoutToneNFD, Normalizer.Form.NFC);
        return new String[]{wordWithoutTone, toneMark};
    }

    private static ArrayList<String> segmentAndValidate(String word) {

        // if word has more than 3 vowels, then return null
        int vowelCount = 0;
        String vowels = "aeiouyăâêôơư";
        for (char c : word.toLowerCase().toCharArray()) {
            if (vowels.indexOf(c) != -1) {
                vowelCount++;
            }
        }
        if (vowelCount > 3) {
            return null;
        }

        ArrayList<String> wordSegment = new ArrayList<>();

        // find initial consonant
        String initialPart = "";
        for (String i : initialConsonant) {
            if (word.toLowerCase().startsWith(i)) {
                initialPart = word.substring(0, i.length());
                break;
            }
        }
        wordSegment.add(initialPart);

        // find remaining part then segment and validate it
        String remaining = word.substring(initialPart.length());
        if (parseRemaining(remaining, wordSegment)) {
            return wordSegment;
        }

        // fall back case if initial consonant "gi" or "qu" isn't satisfied. Check if initial consonant is "g"
        if (initialPart.equalsIgnoreCase("gi") || initialPart.equalsIgnoreCase("qu")) {
            wordSegment.set(0, initialPart.split("")[0]);
            String fallBackRemaining = word.substring(1);
            if (parseRemaining(fallBackRemaining, wordSegment)) {
                return wordSegment;
            }
        }

        return null;
    }

    private static boolean parseRemaining(String remaining, ArrayList<String> wordSegment) {

        if (remaining == null || remaining.isEmpty()) {
            return false;
        }

        // find leftmost vowel as a priority. There can be multiple case
        for (int i = 0; i < remaining.length(); i++) {

            int startIndex = -1;
            int endIndex = -1;

            for (String element : vowel) {
                if (remaining.toLowerCase().startsWith(element, i)) {
                    startIndex = i;
                    endIndex = i + element.length();
                    break;
                }
            }

            if (startIndex != -1) {

                String vowelPart = remaining.substring(startIndex, endIndex);
                String medialPart = "";
                String finalPart = "";

                // medialPart is in the left of vowelPart and finalPart is in the right of vowelPart
                medialPart = remaining.substring(0, startIndex);
                finalPart = remaining.substring(endIndex);

                // find valid medialPart and finalPart
                boolean isMedialValid = medialPart.isEmpty() || medialGlide.contains(medialPart.toLowerCase());
                boolean isFinalValid = finalPart.isEmpty() || finalConsonant.contains(finalPart.toLowerCase());

                // if medialPart is "u" and vowelPart is u/ư + something, then return false
                if (medialPart.equalsIgnoreCase("u") && vowelPart.length() == 2
                        && (Character.toLowerCase(vowelPart.charAt(0)) == 'u' || Character.toLowerCase(vowelPart.charAt(0)) == 'ư')) {
                    return false;
                }

                // if all part are satisfied, then add to wordSegment
                if (isMedialValid && isFinalValid) {
                    wordSegment.addAll(Arrays.asList(medialPart, vowelPart, finalPart));
                    return true;
                }
            }
        }

        // if all part are not satisfied, then return false
        return false;
    }

    public static String addTone(String vowel, String newToneMark, String oldToneMark) {

        // if tone mark is not changed, then return vowel wihtout tone
        if (newToneMark.equals(oldToneMark)) {
            return vowel;
        }

        int targetIndex = -1;

        // if vowel has only one letter, tone mark is placed on that letter.
        if (vowel.length() == 1) {
            targetIndex = 0;
        } else {
            // diphthongs with 2 characters, tone mark is placed on the second character.
            String[] group1 = {"iê", "yê", "uô", "ươ", "uơ", "ưo", "ie", "ye", "uo"};
            for (String v : group1) {
                if (vowel.equalsIgnoreCase(v)) {
                    targetIndex =  1;
                    break;
                }
            }

            // diphthongs with 2 characters, tone mark is placed on the first character.
            if (targetIndex == -1) {
                String[] group2 = {"ia", "ua", "ưa", "ya"};
                for (String v : group2) {
                    if (vowel.equalsIgnoreCase(v)) {
                        targetIndex =  0;
                        break;
                    }
                }
            }

        }

        if (targetIndex == -1) {
            return vowel;
        }

        // add tone mark to vowel
        char targetChar = vowel.charAt(targetIndex);
        String nfdString = targetChar + newToneMark;
        String composedChar = Normalizer.normalize(nfdString, Normalizer.Form.NFC);
        StringBuilder result = new StringBuilder(vowel);
        result.replace(targetIndex, targetIndex + 1, composedChar);

        return result.toString();
    }

    public static String[] addDiacriticalMark(String vowel, String diacriticalMark) {

        char mark = diacriticalMark.toLowerCase().charAt(0);
        String newVowel = vowel;

        if (mark == 'a' && (vowel.equalsIgnoreCase("ia") || vowel.equalsIgnoreCase("ưa"))) {
            return new String[]{newVowel, "t"};
        }
        if (mark == 'o' && vowel.equalsIgnoreCase("ưo")) {
            return new String[]{newVowel, "t"};
        }

        // case: vowel can combine with "w"
        if (mark == 'w') {
            switch (vowel.toLowerCase()) {
                case "ia":
                    return new String[]{newVowel, "t"};
                case "a":
                    newVowel = Character.isUpperCase(vowel.charAt(0)) ? vowel.replace(vowel.charAt(0), 'Ă') : vowel.replace(vowel.charAt(0), 'ă');
                    return new String[]{newVowel, "f"};
                case "â":
                    newVowel = Character.isUpperCase(vowel.charAt(0)) ? vowel.replace(vowel.charAt(0), 'Ă') : vowel.replace(vowel.charAt(0), 'ă');
                    return new String[]{newVowel, "f"};
                case "ă":
                    newVowel = Character.isUpperCase(vowel.charAt(0)) ? vowel.replace(vowel.charAt(0), 'A') : vowel.replace(vowel.charAt(0), 'a');
                    return new String[]{newVowel, "t"};
                case "u":
                    newVowel = Character.isUpperCase(vowel.charAt(0)) ? vowel.replace(vowel.charAt(0), 'Ư') : vowel.replace(vowel.charAt(0), 'ư');
                    return new String[]{newVowel, "f"};
                case "ư":
                    newVowel = Character.isUpperCase(vowel.charAt(0)) ? vowel.replace(vowel.charAt(0), 'U') : vowel.replace(vowel.charAt(0), 'u');
                    return new String[]{newVowel, "t"};
                case "o":
                    newVowel = Character.isUpperCase(vowel.charAt(0)) ? vowel.replace(vowel.charAt(0), 'Ơ') : vowel.replace(vowel.charAt(0), 'ơ');
                    return new String[]{newVowel, "f"};
                case "ô":
                    newVowel = Character.isUpperCase(vowel.charAt(0)) ? vowel.replace(vowel.charAt(0), 'Ơ') : vowel.replace(vowel.charAt(0), 'ơ');
                    return new String[]{newVowel, "f"};
                case "ơ":
                    newVowel = Character.isUpperCase(vowel.charAt(0)) ? vowel.replace(vowel.charAt(0), 'O') : vowel.replace(vowel.charAt(0), 'o');
                    return new String[]{newVowel, "t"};
                case "ua":
                    if (Character.isUpperCase(vowel.charAt(0))) {
                        newVowel = vowel.replace(vowel.charAt(0), 'Ư');
                    } else {
                        newVowel = vowel.replace(vowel.charAt(0), 'ư');
                    }
                    return new String[]{newVowel, "f"};
                case "ưa":
                    if (Character.isUpperCase(vowel.charAt(0))) {
                        newVowel = vowel.replace(vowel.charAt(0), 'U');
                    } else {
                        newVowel = vowel.replace(vowel.charAt(0), 'u');
                    }
                    return new String[]{newVowel, "t"};
                case "ươ":
                    newVowel = Character.isUpperCase(vowel.charAt(0)) ? vowel.replace(vowel.charAt(0), 'U') : vowel.replace(vowel.charAt(0), 'u');
                    newVowel = Character.isUpperCase(newVowel.charAt(1)) ? newVowel.replace(newVowel.charAt(1), 'O') : newVowel.replace(newVowel.charAt(1), 'o');
                    return new String[]{newVowel, "t"};
                case "uo":
                case "uơ":
                case "ưo":
                case "uô":
                case "ưô":
                    newVowel = Character.isUpperCase(vowel.charAt(0)) ? vowel.replace(vowel.charAt(0), 'Ư') : vowel.replace(vowel.charAt(0), 'ư');
                    newVowel = Character.isUpperCase(newVowel.charAt(1)) ? newVowel.replace(newVowel.charAt(1), 'Ơ') : newVowel.replace(newVowel.charAt(1), 'ơ');
                    return new String[]{newVowel, "f"};
            }

        // simple case: a,ă + a = â, â + a = a, ...
        } else {
            for (Map.Entry<Character, Map<Character, Character>> entry : simpleMarkRule.entrySet()) {

                Map<Character, Character> value = entry.getValue();
                int index = vowel.toLowerCase().indexOf(entry.getKey());

                if (index != -1 && value.containsKey(mark)) {

                    char charInVowel = vowel.charAt(index);
                    if (Character.isUpperCase(charInVowel)) {
                        newVowel = vowel.replace(charInVowel, Character.toUpperCase(value.get(mark)));
                    } else {
                        newVowel = vowel.replace(charInVowel, value.get(mark));
                    }

                    char lowerChar = Character.toLowerCase(charInVowel);
                    if (lowerChar == 'â' || lowerChar == 'ê' || lowerChar == 'ô') {
                        return new String[]{newVowel, "t"};
                    }
                    return new String[]{newVowel, "f"};
                }
            }
        }

        return null;
    }

}
