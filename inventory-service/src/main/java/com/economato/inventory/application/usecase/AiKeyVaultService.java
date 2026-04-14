package com.economato.inventory.application.usecase;

import com.economato.inventory.application.dto.event.AiAuditEvent;
import com.economato.inventory.domain.model.AiProvider;
import com.economato.inventory.domain.model.User;
import com.economato.inventory.domain.model.UserApiKey;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.messaging.kafka.producer.AuditEventProducer;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserApiKeyRepository;
import com.economato.inventory.infrastructure.config.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.ai.AiRateLimitProperties;
import com.economato.inventory.infrastructure.config.ai.AiVaultProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AiKeyVaultService {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AiVaultProperties aiVaultProperties;
    private final AiProviderProperties aiProviderProperties;
    private final UserApiKeyRepository userApiKeyRepository;
    private final AiRateLimitProperties aiRateLimitProperties;
    private final MeterRegistry meterRegistry;
    private final Optional<AuditEventProducer> auditEventProducer;

    public void saveKey(Integer userId, AiProvider provider, String plainApiKey) {
        validateInputs(userId, provider, plainApiKey);
        validateProvider(provider, plainApiKey);

        UserApiKey existing = userApiKeyRepository.findByUserIdAndProvider(userId, provider).orElse(null);
        if (existing == null) {
            enforceUserApiKeyLimit(userId);
            UserApiKey created = UserApiKey.builder()
                    .build();
            applyEncryptedKey(created, plainApiKey);
            created.setUser(buildUserRef(userId));
            created.setProvider(provider);
            created.setActive(true);
            UserApiKey saved = userApiKeyRepository.save(created);
            log.info("API key saved: userId={}, provider={}, version={}", userId, provider, saved.getEncryptionKeyVersion());
            publishAudit(AiAuditEvent.builder()
                    .eventType("AI_KEY_ADDED")
                    .userId(userId)
                    .provider(provider.name())
                    .eventTimestamp(LocalDateTime.now())
                    .build());
            return;
        }

        applyEncryptedKey(existing, plainApiKey);
        existing.setActive(true);
        UserApiKey saved = userApiKeyRepository.save(existing);
        log.info("API key saved: userId={}, provider={}, version={}", userId, provider, saved.getEncryptionKeyVersion());
        publishAudit(AiAuditEvent.builder()
                .eventType("AI_KEY_UPDATED")
                .userId(userId)
                .provider(provider.name())
                .eventTimestamp(LocalDateTime.now())
                .build());
    }

    public void updateKey(Integer userId, AiProvider provider, String newPlainApiKey) {
        validateInputs(userId, provider, newPlainApiKey);
        validateProvider(provider, newPlainApiKey);

        UserApiKey existing = userApiKeyRepository
                .findByUserIdAndProviderAndActiveTrue(userId, provider)
                .orElseThrow(() -> new ResourceNotFoundException("Active API key not found for provider: " + provider));

        applyEncryptedKey(existing, newPlainApiKey);
        UserApiKey saved = userApiKeyRepository.save(existing);
        log.info("API key saved: userId={}, provider={}, version={}", userId, provider, saved.getEncryptionKeyVersion());
        publishAudit(AiAuditEvent.builder()
            .eventType("AI_KEY_UPDATED")
            .userId(userId)
            .provider(provider.name())
            .eventTimestamp(LocalDateTime.now())
            .build());
    }

    @Transactional(readOnly = true)
    public String getDecryptedKey(Integer userId, AiProvider provider) {
        if (userId == null || provider == null) {
            throw new InvalidOperationException("User and provider are required");
        }

        UserApiKey key = userApiKeyRepository
                .findByUserIdAndProviderAndActiveTrue(userId, provider)
                .orElseThrow(() -> new ResourceNotFoundException("Active API key not found for provider: " + provider));

        return decrypt(key.getEncryptedKey());
    }

    public void deleteKey(Integer userId, Long keyId) {
        if (userId == null || keyId == null) {
            throw new InvalidOperationException("User and key id are required");
        }

        UserApiKey key = userApiKeyRepository
                .findByIdAndUserId(keyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found"));

        AiProvider provider = key.getProvider();
        userApiKeyRepository.delete(key);
        publishAudit(AiAuditEvent.builder()
            .eventType("AI_KEY_REMOVED")
            .userId(userId)
            .provider(provider != null ? provider.name() : null)
            .eventTimestamp(LocalDateTime.now())
            .build());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyMetadata> listKeys(Integer userId) {
        if (userId == null) {
            throw new InvalidOperationException("User is required");
        }

        return userApiKeyRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(key -> new ApiKeyMetadata(
                        key.getId(),
                        key.getProvider(),
                        key.getKeyHint(),
                        key.isActive(),
                        key.getCreatedAt()
                ))
                .toList();
    }

    public void reEncryptAll(int fromVersion, int toVersion) {
        if (fromVersion < 1 || toVersion < 1) {
            throw new InvalidOperationException("Key versions must be >= 1");
        }
        if (fromVersion == toVersion) {
            return;
        }

        // Validate configured keys early before processing records.
        aiVaultProperties.getKeyForVersion(fromVersion);
        aiVaultProperties.getKeyForVersion(toVersion);

        List<UserApiKey> keys = userApiKeyRepository.findByEncryptionKeyVersion(fromVersion);
        for (UserApiKey key : keys) {
            String plain = decrypt(key.getEncryptedKey());
            key.setEncryptedKey(encrypt(plain, toVersion));
            key.setEncryptionKeyVersion(toVersion);
        }
        userApiKeyRepository.saveAll(keys);
        log.info("API key re-encryption: fromVersion={}, toVersion={}, count={}", fromVersion, toVersion, keys.size());
    }

    private void enforceUserApiKeyLimit(Integer userId) {
        List<UserApiKey> activeKeys = userApiKeyRepository.findByUserIdAndActiveTrue(userId);
        if (activeKeys.size() >= aiRateLimitProperties.getMaxApiKeysPerUser()) {
            throw new InvalidOperationException("Maximum number of API keys reached for this user");
        }
    }

    private void validateProvider(AiProvider provider, String plainApiKey) {
        Map<String, AiProviderProperties.ProviderConfig> configs = aiProviderProperties.getConfigs();
        AiProviderProperties.ProviderConfig config = configs != null ? configs.get(provider.name()) : null;
        if (config == null) {
            throw new InvalidOperationException("Provider config not found: " + provider);
        }
        if (Boolean.FALSE.equals(config.getEnabled())) {
            throw new InvalidOperationException("Provider disabled: " + provider);
        }

        String expectedPrefix = config.getKeyPrefix();
        if (expectedPrefix != null && !expectedPrefix.isBlank() && !plainApiKey.startsWith(expectedPrefix)) {
            throw new InvalidOperationException("Invalid API key format for provider: " + provider);
        }
    }

    private void validateInputs(Integer userId, AiProvider provider, String plainApiKey) {
        if (userId == null || provider == null) {
            throw new InvalidOperationException("User and provider are required");
        }
        if (plainApiKey == null || plainApiKey.isBlank()) {
            throw new InvalidOperationException("API key cannot be blank");
        }
    }

    private void applyEncryptedKey(UserApiKey userApiKey, String plainApiKey) {
        int keyVersion = aiVaultProperties.getCurrentKeyVersion();
        userApiKey.setEncryptedKey(encrypt(plainApiKey, keyVersion));
        userApiKey.setEncryptionKeyVersion(keyVersion);
        userApiKey.setKeyHint(extractKeyHint(plainApiKey));
    }

    private String encrypt(String plainText, int keyVersion) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(decodeHex(aiVaultProperties.getKeyForVersion(keyVersion)), "AES");
            GCMParameterSpec gcm = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcm);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            String ivPart = Base64.getEncoder().encodeToString(iv);
            String encryptedPart = Base64.getEncoder().encodeToString(encrypted);

            counter("ai.vault.encryptions.total").increment();
            return keyVersion + ":" + ivPart + ":" + encryptedPart;
        } catch (Exception ex) {
            throw new InvalidOperationException("Failed to encrypt API key");
        } finally {
            sample.stop(timer("ai.vault.crypto.duration"));
        }
    }

    private String decrypt(String encryptedPayload) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String[] parts = encryptedPayload != null ? encryptedPayload.split(":", 3) : new String[0];
            if (parts.length != 3) {
                throw new InvalidOperationException("Invalid encrypted payload format");
            }

            int version;
            try {
                version = Integer.parseInt(parts[0]);
            } catch (NumberFormatException ex) {
                throw new InvalidOperationException("Invalid encrypted payload version");
            }

            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] cipherText = Base64.getDecoder().decode(parts[2]);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(decodeHex(aiVaultProperties.getKeyForVersion(version)), "AES");
            GCMParameterSpec gcm = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcm);

            byte[] decoded = cipher.doFinal(cipherText);
            counter("ai.vault.decryptions.total").increment();
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (InvalidOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidOperationException("Failed to decrypt API key");
        } finally {
            sample.stop(timer("ai.vault.crypto.duration"));
        }
    }

    private User buildUserRef(Integer userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }

    private String extractKeyHint(String plainApiKey) {
        String suffix = plainApiKey.length() <= 4
                ? plainApiKey
                : plainApiKey.substring(plainApiKey.length() - 4);
        return "****" + suffix;
    }

    private byte[] decodeHex(String hex) {
        if (hex == null || hex.isBlank() || (hex.length() % 2 != 0)) {
            throw new InvalidOperationException("Invalid vault key format");
        }

        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new InvalidOperationException("Invalid vault key format");
            }
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    private Counter counter(String name) {
        return meterRegistry.counter(name);
    }

    private Timer timer(String name) {
        return meterRegistry.timer(name);
    }

    private void publishAudit(AiAuditEvent event) {
        auditEventProducer.ifPresent(producer -> producer.publishAiAudit(event));
    }

    public record ApiKeyMetadata(
            Long id,
            AiProvider provider,
            String keyHint,
            boolean active,
            LocalDateTime createdAt
    ) {
    }
}
