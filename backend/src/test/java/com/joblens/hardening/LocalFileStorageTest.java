package com.joblens.hardening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.common.storage.LocalFileStorage;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalFileStorageTest {

    @TempDir Path root;
    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(root.resolve("uploads").toString());
    }

    @Test
    void storesLoadsAndDeletes() {
        byte[] content = "pdf bytes".getBytes(StandardCharsets.UTF_8);
        storage.store("a.pdf", content);
        assertThat(storage.load("a.pdf")).isEqualTo(content);
        storage.delete("a.pdf");
        assertThatThrownBy(() -> storage.load("a.pdf")).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void deletingAMissingFileIsHarmless() {
        assertThatCode(() -> storage.delete("never-existed.pdf")).doesNotThrowAnyException();
    }

    @Test
    void createsItsDirectoryIfMissing() {
        assertThat(Files.isDirectory(root.resolve("uploads"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escape.pdf", "../../etc/passwd", "sub/../../escape.pdf", "/etc/passwd", ".", ""})
    void refusesKeysThatEscapeTheStorageRoot(String key) {
        assertThatThrownBy(() -> storage.store(key, new byte[] {1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(root.resolve("escape.pdf"))).isFalse();
    }
}
