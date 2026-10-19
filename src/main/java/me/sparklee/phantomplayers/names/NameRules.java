package me.sparklee.phantomplayers.names;

import java.util.Locale;
import java.util.regex.Pattern;

public final class NameRules {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 16;
    public static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private NameRules() {
    }

    public static boolean isValid(String username) {
        return username != null && VALID.matcher(username).matches();
    }

    public static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
