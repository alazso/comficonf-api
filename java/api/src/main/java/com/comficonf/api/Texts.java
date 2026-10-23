package com.comficonf.api;

/** Text helpers shared by the value types of this package. */
final class Texts {

    private Texts() {
    }

    /**
     * Returns {@code text} if it has at most {@code max} chars, otherwise its start followed by {@code ...}, in
     * {@code max} chars or one fewer. The cut never separates the two chars of a surrogate pair.
     */
    static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int end = max - 3;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "...";
    }
}
