package com.wherelee.etry.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectKeysTest {

    @Test
    void fileKeyIsScopedToUserAndKeepsSafeExtension() {
        String key = ObjectKeys.file(7L, "报告.PDF");

        assertThat(key).startsWith("u/7/files/");
        assertThat(key).endsWith(".pdf");
    }

    @Test
    void suspiciousExtensionIsDropped() {
        assertThat(ObjectKeys.extension("a.tar.gz")).isEqualTo(".gz");
        assertThat(ObjectKeys.extension("noext")).isEmpty();
        assertThat(ObjectKeys.extension("evil.p/../x")).isEmpty();
        assertThat(ObjectKeys.extension(null)).isEmpty();
    }

    @Test
    void keysAreUniqueEvenForSameName() {
        String first = ObjectKeys.file(1L, "a.txt");
        String second = ObjectKeys.file(1L, "a.txt");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void avatarKeyIsScopedToUser() {
        assertThat(ObjectKeys.avatar(9L)).startsWith("u/9/avatar/");
    }
}
