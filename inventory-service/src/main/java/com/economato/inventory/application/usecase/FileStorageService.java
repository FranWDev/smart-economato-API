package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileStorageService {

    private final Path basePath;
    private final long maxFileSize;
    private final Set<String> allowedTypes;
    private final I18nService i18nService;
    @Autowired(required = false)
    private SystemConfigService systemConfigService;

    public FileStorageService(@Value("${app.uploads.base-path:uploads}") String basePath,
                              @Value("${app.uploads.max-file-size:10485760}") long maxFileSize,
                              @Value("${app.uploads.allowed-types:image/jpeg,image/png,image/gif,image/webp,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}") String allowedTypes,
                              I18nService i18nService) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
        this.allowedTypes = Arrays.stream(allowedTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        this.i18nService = i18nService;

        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new RuntimeException(i18nService.getMessage(MessageKey.ERROR_FILE_STORAGE_INIT), e);
        }
    }

    public String store(Long incidentId, Long messageId, MultipartFile file) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeFilename = sanitizeFilename(originalFilename);
        String fileName = messageId + "_" + safeFilename;
        Path incidentDirectory = basePath.resolve("incidents").resolve(String.valueOf(incidentId));

        try {
            Files.createDirectories(incidentDirectory);
            Path target = incidentDirectory.resolve(fileName).normalize();
            if (!target.startsWith(incidentDirectory)) {
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return basePath.relativize(target).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new RuntimeException(i18nService.getMessage(MessageKey.ERROR_FILE_STORAGE_SAVE), e);
        }
    }

    public Resource load(String relativePath) {
        try {
            Path fullPath = basePath.resolve(relativePath).normalize();
            if (!fullPath.startsWith(basePath)) {
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
            }
            Resource resource = new UrlResource(fullPath.toUri());
            if (!resource.exists()) {
                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_RESOURCE_NOT_FOUND));
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new RuntimeException(i18nService.getMessage(MessageKey.ERROR_FILE_STORAGE_LOAD), e);
        }
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        Path fullPath = basePath.resolve(relativePath).normalize();
        if (!fullPath.startsWith(basePath)) {
            return;
        }

        try {
            Files.deleteIfExists(fullPath);
        } catch (IOException e) {
            log.warn("Unable to delete incident attachment at {}", relativePath, e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_CHAT_EMPTY_MESSAGE));
        }

        if (file.getSize() > resolveMaxFileSize()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_FILE_TOO_LARGE));
        }

        String contentType = file.getContentType();
        if (contentType == null || !resolveAllowedTypes().contains(contentType)) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_INCIDENT_FILE_TYPE_NOT_ALLOWED));
        }
    }

    private long resolveMaxFileSize() {
        if (systemConfigService == null) {
            return maxFileSize;
        }
        try {
            return systemConfigService.getMaxUploadFileSizeBytes();
        } catch (Exception ignored) {
            return maxFileSize;
        }
    }

    private Set<String> resolveAllowedTypes() {
        if (systemConfigService == null) {
            return allowedTypes;
        }
        try {
            Set<String> cfg = systemConfigService.getAllowedFileTypes();
            return cfg.isEmpty() ? allowedTypes : cfg;
        } catch (Exception ignored) {
            return allowedTypes;
        }
    }

    private String sanitizeFilename(String filename) {
        String cleaned = StringUtils.cleanPath(filename).replace('\\', '/');
        int slashIndex = cleaned.lastIndexOf('/');
        String baseName = slashIndex >= 0 ? cleaned.substring(slashIndex + 1) : cleaned;

        String normalized = baseName
                .replace("..", "")
                .replaceAll("[\r\n]", "")
                .replaceAll("[^A-Za-z0-9._-]", "_");

        return normalized.isBlank() ? "file" : normalized;
    }
}
