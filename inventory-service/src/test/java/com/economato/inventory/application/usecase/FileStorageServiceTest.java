package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        I18nService i18nService = mock(I18nService.class);
        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MessageKey.class).getKey());

        service = new FileStorageService(
                tempDir.toString(),
                1024 * 1024,
                "image/jpeg,image/png,application/pdf",
                i18nService
        );
    }

    @Test
    void store_WithValidImage_ShouldSaveFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        String relativePath = service.store(10L, 20L, file);
        Path fullPath = tempDir.resolve(relativePath);

        assertTrue(Files.exists(fullPath));
        assertTrue(relativePath.contains("incidents/10/20_test.jpg"));
    }

    @Test
    void store_WithDisallowedMimeType_ShouldThrow() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malware.exe",
                "application/x-msdownload",
                "bin".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(InvalidOperationException.class, () -> service.store(10L, 21L, file));
    }

    @Test
    void store_WithPathTraversalFilename_ShouldSanitize() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../etc/passwd",
                "image/jpeg",
                "safe".getBytes(StandardCharsets.UTF_8)
        );

        String relativePath = null;
        try {
            relativePath = service.store(11L, 22L, file);
        } catch (RuntimeException ex) {
            // Keep this assertion as a business expectation: traversal names should be sanitized, not crash.
            throw new AssertionError("Store should sanitize traversal filename and persist safely", ex);
        }
        Path fullPath = tempDir.resolve(relativePath).normalize();

        assertNotNull(relativePath);
        assertTrue(fullPath.startsWith(tempDir));
        assertFalse(relativePath.contains(".."));
    }

    @Test
    void load_WhenFileExists_ShouldReturnResource() throws Exception {
        Path path = tempDir.resolve("incidents/33/44_test.jpg");
        Files.createDirectories(path.getParent());
        Files.write(path, "content".getBytes(StandardCharsets.UTF_8));

        Resource resource = service.load("incidents/33/44_test.jpg");

        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }
}
