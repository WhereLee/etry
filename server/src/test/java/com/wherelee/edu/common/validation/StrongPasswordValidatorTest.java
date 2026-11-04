package com.wherelee.edu.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void acceptsEightCharacterPasswordWithLetterAndDigit() {
        assertThat(validator.isValid("Flow1234", null)).isTrue();
    }

    @Test
    void rejectsShortOrSingleClassPassword() {
        assertThat(validator.isValid("Abc123", null)).isFalse();
        assertThat(validator.isValid("abcdefgh", null)).isFalse();
        assertThat(validator.isValid("12345678", null)).isFalse();
    }

    @Test
    void rejectsKnownWeakPassword() {
        assertThat(validator.isValid("admin123", null)).isFalse();
        assertThat(validator.isValid("student123", null)).isFalse();
    }

    @Test
    void leavesNullToNotBlankConstraint() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
