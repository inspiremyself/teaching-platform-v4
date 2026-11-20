package com.opencode.teachingplatform.grading;

import com.opencode.teachingplatform.grading.enums.QuestionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionTypeTests {

    @Test
    void fromAliasAcceptsAllFourteenAliases() {
        assertEquals(QuestionType.SINGLE_CHOICE, QuestionType.fromAlias("SINGLE"));
        assertEquals(QuestionType.SINGLE_CHOICE, QuestionType.fromAlias("SINGLE_CHOICE"));
        assertEquals(QuestionType.MULTIPLE_CHOICE, QuestionType.fromAlias("MULTI"));
        assertEquals(QuestionType.MULTIPLE_CHOICE, QuestionType.fromAlias("MULTIPLE"));
        assertEquals(QuestionType.MULTIPLE_CHOICE, QuestionType.fromAlias("MULTIPLE_CHOICE"));
        assertEquals(QuestionType.TRUE_FALSE, QuestionType.fromAlias("JUDGE"));
        assertEquals(QuestionType.TRUE_FALSE, QuestionType.fromAlias("TRUE_FALSE"));
        assertEquals(QuestionType.FILL_BLANK, QuestionType.fromAlias("FILL"));
        assertEquals(QuestionType.FILL_BLANK, QuestionType.fromAlias("FILL_BLANK"));
        assertEquals(QuestionType.SUBJECTIVE, QuestionType.fromAlias("SHORT"));
        assertEquals(QuestionType.SUBJECTIVE, QuestionType.fromAlias("TEXT"));
        assertEquals(QuestionType.SUBJECTIVE, QuestionType.fromAlias("SUBJECTIVE"));
        assertEquals(QuestionType.SUBJECTIVE, QuestionType.fromAlias("CODE"));
        assertEquals(QuestionType.SUBJECTIVE, QuestionType.fromAlias("SHORT_ANSWER"));
    }

    @Test
    void fromAliasHandlesNullBlankCaseAndWhitespace() {
        assertNull(QuestionType.fromAlias(null));
        assertNull(QuestionType.fromAlias(""));
        assertNull(QuestionType.fromAlias("   "));
        assertNull(QuestionType.fromAlias("UNKNOWN"));
        assertEquals(QuestionType.SINGLE_CHOICE, QuestionType.fromAlias(" single "));
    }

    @Test
    void isObjectiveUsesScoringSemantics() {
        assertTrue(QuestionType.SINGLE_CHOICE.isObjective());
        assertTrue(QuestionType.MULTIPLE_CHOICE.isObjective());
        assertTrue(QuestionType.TRUE_FALSE.isObjective());
        assertTrue(QuestionType.FILL_BLANK.isObjective());
        assertFalse(QuestionType.SUBJECTIVE.isObjective());
    }

    @Test
    void legacyCodeReturnsFiveCanonicalValues() {
        assertEquals("SINGLE", QuestionType.SINGLE_CHOICE.legacyCode());
        assertEquals("MULTI", QuestionType.MULTIPLE_CHOICE.legacyCode());
        assertEquals("JUDGE", QuestionType.TRUE_FALSE.legacyCode());
        assertEquals("FILL", QuestionType.FILL_BLANK.legacyCode());
        assertEquals("SHORT", QuestionType.SUBJECTIVE.legacyCode());
    }
}
