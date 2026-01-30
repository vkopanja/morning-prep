package com.usergems.morningprep.util;

/**
 * Utility class for ordinal number formatting.
 */
public final class OrdinalUtils {

    private OrdinalUtils() {
        // Utility class
    }

    /**
     * Convert a number to its ordinal form (e.g., 1 -> "1st", 2 -> "2nd", 12 -> "12th").
     *
     * @param n The number to convert
     * @return The ordinal string
     */
    public static String toOrdinal(int n) {
        if (n <= 0) {
            return String.valueOf(n);
        }

        // Special cases for 11, 12, 13
        int lastTwoDigits = n % 100;
        if (lastTwoDigits >= 11 && lastTwoDigits <= 13) {
            return n + "th";
        }

        // Regular cases based on last digit
        int lastDigit = n % 10;
        return switch (lastDigit) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }

    /**
     * Format meeting count as ordinal meeting (e.g., 12 -> "12th Meeting").
     *
     * @param count The meeting count
     * @return Formatted string like "12th Meeting"
     */
    public static String toOrdinalMeeting(int count) {
        return toOrdinal(count) + " Meeting";
    }
}
