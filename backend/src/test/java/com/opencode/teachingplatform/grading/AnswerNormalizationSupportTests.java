package com.opencode.teachingplatform.grading;

import com.opencode.teachingplatform.grading.support.AnswerNormalizationSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AnswerNormalizationSupport} 的行为基线：从 AbstractObjectiveScoringSupport /
 * GradingAdapter / LabScoringSupport 收编的每个方法在边界输入上的语义保持不变。
 */
class AnswerNormalizationSupportTests {

    @Test
    void normalizeTokenHandlesNullCaseAndWhitespace() {
        assertThat(AnswerNormalizationSupport.normalizeToken(null)).isEmpty();
        assertThat(AnswerNormalizationSupport.normalizeToken(123)).isEqualTo("123");
        assertThat(AnswerNormalizationSupport.normalizeToken("  AbC ")).isEqualTo("abc");
    }

    @Test
    void normalizeTextConvertsFullWidthComma() {
        assertThat(AnswerNormalizationSupport.normalizeText("甲，乙")).isEqualTo("甲,乙");
        assertThat(AnswerNormalizationSupport.normalizeText(null)).isEmpty();
    }

    @Test
    void normalizeSetHandlesCollectionSingleValueAndBlanks() {
        assertThat(AnswerNormalizationSupport.normalizeSet(List.of(" A", "b ", "", "A")))
                .containsExactly("a", "b");
        assertThat(AnswerNormalizationSupport.normalizeSet("Only")).containsExactly("only");
        assertThat(AnswerNormalizationSupport.normalizeSet(null)).isEmpty();
    }

    @Test
    void asIntParsesNumbersStringsAndFallsBackToZero() {
        assertThat(AnswerNormalizationSupport.asInt(7)).isEqualTo(7);
        assertThat(AnswerNormalizationSupport.asInt("42")).isEqualTo(42);
        assertThat(AnswerNormalizationSupport.asInt(null)).isZero();
        assertThatThrownBy(() -> AnswerNormalizationSupport.asInt("not-a-number"))
                .isInstanceOf(NumberFormatException.class);
        assertThat(AnswerNormalizationSupport.asInt("  ")).isZero();
    }

    @Test
    void asDoubleOrZeroParsesAndFallsBackToZero() {
        assertThat(AnswerNormalizationSupport.asDoubleOrZero(2.5)).isEqualTo(2.5);
        assertThat(AnswerNormalizationSupport.asDoubleOrZero("3.25")).isEqualTo(3.25);
        assertThat(AnswerNormalizationSupport.asDoubleOrZero(null)).isZero();
        assertThatThrownBy(() -> AnswerNormalizationSupport.asDoubleOrZero("bad"))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void roundKeepsTwoDecimalPlaces() {
        assertThat(AnswerNormalizationSupport.round(2.675)).isEqualTo(2.68);
        assertThat(AnswerNormalizationSupport.round(10.0)).isEqualTo(10.0);
    }

    @Test
    void firstNonBlankReturnsFirstMeaningfulValueOrNull() {
        assertThat(AnswerNormalizationSupport.firstNonBlank(null, "  ", "a", "b")).isEqualTo("a");
        assertThat(AnswerNormalizationSupport.firstNonBlank("first")).isEqualTo("first");
        assertThat(AnswerNormalizationSupport.firstNonBlank(null, "   ")).isNull();
        assertThat(AnswerNormalizationSupport.firstNonBlank()).isNull();
    }

    @Test
    void splitAnswerTokensHandlesChineseCommaAndWhitespace() {
        assertThat(AnswerNormalizationSupport.splitAnswerTokens("a,b，c  d"))
                .containsExactly("a", "b", "c", "d");
        assertThat(AnswerNormalizationSupport.splitAnswerTokens("only")).containsExactly("only");
    }

    @Test
    void parseTextTokenSetKeepsInsertionOrderAndDropsBlanks() {
        assertThat(AnswerNormalizationSupport.parseTextTokenSet("B, A ，C  "))
                .containsExactly("B", "A", "C");
        assertThat(AnswerNormalizationSupport.parseTextTokenSet("   ")).isEmpty();
    }

    @Test
    void parseTextTokenListKeepsDuplicatesAndOrder() {
        assertThat(AnswerNormalizationSupport.parseTextTokenList("甲，乙 甲"))
                .containsExactly("甲", "乙", "甲");
        assertThat(AnswerNormalizationSupport.parseTextTokenList("")).isEmpty();
    }
}
