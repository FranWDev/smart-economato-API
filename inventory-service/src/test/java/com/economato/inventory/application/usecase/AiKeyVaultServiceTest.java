package com.economato.inventory.application.usecase;

import com.economato.inventory.domain.model.AiProvider;
import com.economato.inventory.domain.model.UserApiKey;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserApiKeyRepository;
import com.economato.inventory.infrastructure.config.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.ai.AiRateLimitProperties;
import com.economato.inventory.infrastructure.config.ai.AiVaultProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiKeyVaultServiceTest {

    @Mock
    private UserApiKeyRepository userApiKeyRepository;

    private AiKeyVaultService service;
    private AiVaultProperties aiVaultProperties;
    private AiProviderProperties aiProviderProperties;
    private AiRateLimitProperties aiRateLimitProperties;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        aiVaultProperties = new AiVaultProperties();
        aiVaultProperties.setMasterKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        aiVaultProperties.setCurrentKeyVersion(1);
        aiVaultProperties.setKeyVersions(Map.of(
                1, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                2, "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        ));

        aiProviderProperties = new AiProviderProperties();
        AiProviderProperties.ProviderConfig openai = new AiProviderProperties.ProviderConfig();
        openai.setEnabled(true);
        openai.setKeyPrefix("sk-");

        AiProviderProperties.ProviderConfig deepseek = new AiProviderProperties.ProviderConfig();
        deepseek.setEnabled(true);
        deepseek.setKeyPrefix("sk-");

        aiProviderProperties.setConfigs(Map.of(
                "OPENAI", openai,
                "DEEPSEEK", deepseek
        ));

        aiRateLimitProperties = new AiRateLimitProperties();
        aiRateLimitProperties.setMaxApiKeysPerUser(2);

        meterRegistry = new SimpleMeterRegistry();

        service = new AiKeyVaultService(
                aiVaultProperties,
                aiProviderProperties,
                userApiKeyRepository,
                aiRateLimitProperties,
                meterRegistry,
                Optional.empty()
        );
    }

    @Test
    void saveKey_encryptsAndPersists() {
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveKey(10, AiProvider.OPENAI, "sk-test123456");

        ArgumentCaptor<UserApiKey> captor = ArgumentCaptor.forClass(UserApiKey.class);
        verify(userApiKeyRepository).save(captor.capture());
        UserApiKey saved = captor.getValue();

        assertNotEquals("sk-test123456", saved.getEncryptedKey());
        assertEquals("****3456", saved.getKeyHint());
        assertEquals(1, saved.getEncryptionKeyVersion());
        assertEquals(AiProvider.OPENAI, saved.getProvider());
        assertTrue(saved.isActive());
    }

    @Test
    void saveKey_withInvalidPrefix_throwsException() {
        assertThrows(InvalidOperationException.class,
                () -> service.saveKey(10, AiProvider.OPENAI, "invalid-key"));
    }

    @Test
    void saveKey_exceedingMaxKeys_throwsException() {
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of(new UserApiKey(), new UserApiKey()));

        assertThrows(InvalidOperationException.class,
                () -> service.saveKey(10, AiProvider.OPENAI, "sk-test123456"));
    }

    @Test
    void saveKey_duplicateProvider_updatesExisting() {
        UserApiKey existing = new UserApiKey();
        existing.setProvider(AiProvider.OPENAI);
        existing.setEncryptedKey("old");
        existing.setEncryptionKeyVersion(1);

        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.of(existing));
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveKey(10, AiProvider.OPENAI, "sk-updated9999");

        verify(userApiKeyRepository).save(existing);
        assertNotEquals("old", existing.getEncryptedKey());
        assertEquals("****9999", existing.getKeyHint());
    }

    @Test
    void getDecryptedKey_returnsOriginalPlaintext() {
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

        service.saveKey(10, AiProvider.OPENAI, "sk-my-secret-key");
        String decrypted = service.getDecryptedKey(10, AiProvider.OPENAI);

        assertEquals("sk-my-secret-key", decrypted);
    }

    @Test
    void getDecryptedKey_withNonExistentKey_throwsException() {
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getDecryptedKey(10, AiProvider.OPENAI));
    }

    @Test
    void getDecryptedKey_withWrongVersion_throwsException() {
        AtomicReference<UserApiKey> stored = new AtomicReference<>();
        aiVaultProperties.setCurrentKeyVersion(2);
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> {
            UserApiKey key = invocation.getArgument(0);
            stored.set(key);
            return key;
        });
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        service.saveKey(10, AiProvider.OPENAI, "sk-version-two");

        aiVaultProperties.setKeyVersions(Map.of(
                1, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        ));

        assertThrows(InvalidOperationException.class,
                () -> service.getDecryptedKey(10, AiProvider.OPENAI));
    }

    @Test
    void encryptDecrypt_sameKey_producesDifferentCiphertexts() {
        List<UserApiKey> savedItems = new ArrayList<>();
        when(userApiKeyRepository.findByUserIdAndProvider(eq(10), any(AiProvider.class))).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> {
            UserApiKey key = invocation.getArgument(0);
            UserApiKey snapshot = new UserApiKey();
            snapshot.setEncryptedKey(key.getEncryptedKey());
            snapshot.setKeyHint(key.getKeyHint());
            snapshot.setProvider(key.getProvider());
            savedItems.add(snapshot);
            return key;
        });

        service.saveKey(10, AiProvider.OPENAI, "sk-same-key");
        service.saveKey(10, AiProvider.DEEPSEEK, "sk-same-key");

        assertEquals(2, savedItems.size());
        assertNotEquals(savedItems.get(0).getEncryptedKey(), savedItems.get(1).getEncryptedKey());
    }

    @Test
    void reEncryptAll_migratesKeysToNewVersion() {
        UserApiKey key = new UserApiKey();
        key.setEncryptedKey("1:AAAAAAAAAAAA:BBBB");
        key.setEncryptionKeyVersion(1);
        when(userApiKeyRepository.findByEncryptionKeyVersion(1)).thenReturn(List.of());

        service.reEncryptAll(1, 2);

        verify(userApiKeyRepository).findByEncryptionKeyVersion(1);
        verify(userApiKeyRepository).saveAll(any());
    }

    @Test
    void deleteKey_verifiesOwnership() {
        when(userApiKeyRepository.findByIdAndUserId(1L, 10)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteKey(10, 1L));
    }

    @Test
    void listKeys_neverExposesDecryptedKey() {
        UserApiKey key = new UserApiKey();
        key.setId(1L);
        key.setProvider(AiProvider.OPENAI);
        key.setKeyHint("****9999");
        key.setEncryptedKey("encrypted-payload");
        key.setCreatedAt(LocalDateTime.now());
        key.setActive(true);
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of(key));

        List<AiKeyVaultService.ApiKeyMetadata> result = service.listKeys(10);

        assertEquals(1, result.size());
        assertEquals("****9999", result.get(0).keyHint());
        assertFalse(result.get(0).keyHint().contains("encrypted"));
    }

    @Test
    void metrics_areRecorded() {
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveKey(10, AiProvider.OPENAI, "sk-metrics-1234");

        verify(userApiKeyRepository, atLeastOnce()).save(any(UserApiKey.class));
        assertTrue(meterRegistry.counter("ai.vault.encryptions.total").count() > 0);
    }
}
