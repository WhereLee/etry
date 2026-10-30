package com.wherelee.etry.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTypesTest {

    @Test
    void knownExtensionsMapToTypes() {
        assertThat(ContentTypes.resolve("note.txt")).isEqualTo("text/plain");
        assertThat(ContentTypes.resolve("photo.PNG")).isEqualTo("image/png");
        assertThat(ContentTypes.resolve("archive.tar.gz")).isEqualTo("application/gzip");
    }

    @Test
    void unknownOrMissingExtensionFallsBackToBinary() {
        assertThat(ContentTypes.resolve("noext")).isEqualTo("application/octet-stream");
        assertThat(ContentTypes.resolve("weird.qqq")).isEqualTo("application/octet-stream");
    }
}
