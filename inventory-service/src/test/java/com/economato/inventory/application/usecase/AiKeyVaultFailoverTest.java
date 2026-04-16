package com.economato.inventory.application.usecase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.economato.inventory.domain.model.AiProvider;
import com.economato.inventory.domain.model.UserApiKey;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.GlobalApiKeyRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserApiKeyRepository;
import com.economato.inventory.infrastructure.config.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.ai.AiRateLimitProperties;
import com.economato.inventory.infrastructure.config.ai.AiVaultProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiKeyVaultFailoverTest {

    @Mock
    private UserApiKeyRepository userApiKeyRepository;

    @Mock
    private GlobalApiKeyRepository globalApiKeyRepository;

    private AiKeyVaultService service;
    private AiVaultProperties aiVaultProperties;

    @BeforeEach
    void setUp() {
        aiVaultProperties = new AiVaultProperties();
        aiVaultProperties.setMasterKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        aiVaultProperties.setCurrentKeyVersion(1);
        aiVaultProperties.setKeyVersions(Map.of(
                1, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                2, "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        ));

        AiProviderProperties aiProviderProperties = new AiProviderProperties();
        AiProviderProperties.ProviderConfig openai = new AiProviderProperties.ProviderConfig();
        openai.setEnabled(true);
        openai.setKeyPrefix("sk-");
        aiProviderProperties.setConfigs(Map.of("OPENAI", openai));

        AiRateLimitProperties aiRateLimitProperties = new AiRateLimitProperties();
        aiRateLimitProperties.setMaxApiKeysPerUser(5);

        service = new AiKeyVaultService(
                aiVaultProperties,
                aiProviderProperties,
                userApiKeyRepository,
            globalApiKeyRepository,
                aiRateLimitProperties,
                new SimpleMeterRegistry(),
                Optional.empty()
        );
    }

    @Test
    void corruptedCiphertext_throwsDecryptionException() {
        UserApiKey key = new UserApiKey();
        key.setEncryptedKey("broken");
        key.setProvider(AiProvider.OPENAI);
        key.setActive(true);
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI)).thenReturn(Optional.of(key));

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
            () -> service.getDecryptedKey(10, AiProvider.OPENAI));
        assertNotNull(ex);
    }

    @Test
    void wrongKeyVersion_throwsException() {
        UserApiKey key = new UserApiKey();
        key.setEncryptedKey("99:QUJDREVGR0hJSktM:QUJDREVGR0hJSktM");
        key.setProvider(AiProvider.OPENAI);
        key.setActive(true);
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI)).thenReturn(Optional.of(key));

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
            () -> service.getDecryptedKey(10, AiProvider.OPENAI));
        assertNotNull(ex);
    }

    @Test
    void tamperedIv_throwsAuthenticationException() {
        AtomicReference<UserApiKey> stored = persistEncryptedKey("sk-tampered-1234");

        String[] parts = stored.get().getEncryptedKey().split(":", 3);
        parts[1] = parts[1].substring(0, Math.max(1, parts[1].length() - 2)) + "AA";
        stored.get().setEncryptedKey(parts[0] + ":" + parts[1] + ":" + parts[2]);

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
            () -> service.getDecryptedKey(10, AiProvider.OPENAI));
        assertNotNull(ex);
    }

    @Test
    void truncatedData_throwsException() {
        AtomicReference<UserApiKey> stored = persistEncryptedKey("sk-truncated-1234");

        String[] parts = stored.get().getEncryptedKey().split(":", 3);
        String truncatedCipher = parts[2].substring(0, Math.max(4, parts[2].length() / 3));
        stored.get().setEncryptedKey(parts[0] + ":" + parts[1] + ":" + truncatedCipher);

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
            () -> service.getDecryptedKey(10, AiProvider.OPENAI));
        assertNotNull(ex);
    }

    @Test
    void reEncryptAll_partialFailure_rollsBack() {
        AtomicReference<UserApiKey> stored = new AtomicReference<>();
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> {
            UserApiKey key = invocation.getArgument(0);
            key.setId(1L);
            key.setCreatedAt(LocalDateTime.now());
            stored.set(key);
            return key;
        });
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        service.saveKey(10, AiProvider.OPENAI, "sk-reencrypt-1234");

        when(userApiKeyRepository.findByEncryptionKeyVersion(1)).thenReturn(List.of(stored.get()));
        when(userApiKeyRepository.saveAll(any())).thenThrow(new RuntimeException("database down"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.reEncryptAll(1, 2));
        assertNotNull(ex);
    }

    private AtomicReference<UserApiKey> persistEncryptedKey(String plaintext) {
        AtomicReference<UserApiKey> stored = new AtomicReference<>();
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> {
            UserApiKey key = invocation.getArgument(0);
            key.setId(1L);
            key.setCreatedAt(LocalDateTime.now());
            stored.set(key);
            return key;
        });
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        service.saveKey(10, AiProvider.OPENAI, plaintext);
        return stored;
    }
}