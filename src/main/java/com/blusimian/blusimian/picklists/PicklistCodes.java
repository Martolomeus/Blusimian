package com.blusimian.blusimian.picklists;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Code rules, and deriving a code from a label (spec §10.3). Pure functions. */
public final class PicklistCodes {

    public static final int MAX_LENGTH = 50;
    public static final String FALLBACK = "value";

    // Mirrors ck_picklist_values_code.
    private static final Pattern CODE = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_CODE = Pattern.compile("[^a-z0-9]+");

    private PicklistCodes() {
    }

    public static boolean matchesPattern(String code) {
        return CODE.matcher(code).matches();
    }

    /** "Food & Beverage" -> food_beverage, "Café" -> cafe, "日本" -> value; then _2, _3... while taken. */
    public static String derive(String label, Set<String> taken) {
        String stripped = MARKS.matcher(Normalizer.normalize(label, Normalizer.Form.NFD)).replaceAll("");
        String base = trimUnderscores(NOT_CODE.matcher(stripped.toLowerCase(Locale.ROOT)).replaceAll("_"));
        base = cut(base, MAX_LENGTH);
        if (base.isEmpty()) {
            base = FALLBACK;
        }
        String code = base;
        for (int n = 2; taken.contains(code); n++) {
            String suffix = "_" + n;
            code = cut(base, MAX_LENGTH - suffix.length()) + suffix;
        }
        return code;
    }

    private static String cut(String code, int length) {
        return code.length() <= length ? code : trimUnderscores(code.substring(0, length));
    }

    private static String trimUnderscores(String code) {
        int start = 0;
        int end = code.length();
        while (start < end && code.charAt(start) == '_') {
            start++;
        }
        while (end > start && code.charAt(end - 1) == '_') {
            end--;
        }
        return code.substring(start, end);
    }
}
