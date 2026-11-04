package com.wherelee.edu.config;

import com.wherelee.edu.common.validation.StrongPasswordValidator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DataInitializerPasswordTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void generatedPasswordsSatisfyStrongPasswordRuleAndAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String password = DataInitializer.generatePassword();
            assertThat(password).hasSize(16);
            assertThat(password).matches(".*[A-Za-z].*");
            assertThat(password).matches(".*[0-9].*");
            assertThat(validator.isValid(password, null)).isTrue();
            seen.add(password);
        }
        assertThat(seen).hasSize(500);
    }

    @Test
    void generatedPasswordAvoidsAmbiguousCharacters() {
        for (int i = 0; i < 200; i++) {
            assertThat(DataInitializer.generatePassword())
                    .doesNotContain("I", "O", "l", "o", "0", "1");
        }
    }
}
