package com.opencode.teachingplatform.grading.enums;

import java.util.Locale;

public enum QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    FILL_BLANK,
    SUBJECTIVE;

    public static QuestionType fromAlias(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SINGLE", "SINGLE_CHOICE" -> SINGLE_CHOICE;
            case "MULTI", "MULTIPLE", "MULTIPLE_CHOICE" -> MULTIPLE_CHOICE;
            case "JUDGE", "TRUE_FALSE" -> TRUE_FALSE;
            case "FILL", "FILL_BLANK" -> FILL_BLANK;
            case "SHORT", "TEXT", "SUBJECTIVE", "CODE", "SHORT_ANSWER" -> SUBJECTIVE;
            default -> null;
        };
    }

    public boolean isObjective() {
        return this == SINGLE_CHOICE || this == MULTIPLE_CHOICE || this == TRUE_FALSE || this == FILL_BLANK;
    }

    public String legacyCode() {
        return switch (this) {
            case SINGLE_CHOICE -> "SINGLE";
            case MULTIPLE_CHOICE -> "MULTI";
            case TRUE_FALSE -> "JUDGE";
            case FILL_BLANK -> "FILL";
            case SUBJECTIVE -> "SHORT";
        };
    }
}
