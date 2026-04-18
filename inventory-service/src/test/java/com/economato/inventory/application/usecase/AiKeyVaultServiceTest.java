package com.economato.inventory.application.usecase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.economato.inventory.domain.model.AiProvider;
import com.economato.inventory.domain.model.UserApiKey;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.GlobalApiKeyRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserApiKeyRepository;
import com.economato.inventory.infrastructure.config.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.ai.AiRateLimitProperties;
import com.economato.inventory.infrastructure.config.ai.AiVaultProperties;

import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiKeyVaultServiceTest {

    @Mock
    private UserApiKeyRepository userApiKeyRepository;

    @Mock
    private GlobalApiKeyRepository globalApiKeyRepository;
    
    private String mockMessage;
    private I18nService i18nService;

    private AiKeyVaultService service;
    private AiVaultProperties aiVaultProperties;
    private AiProviderProperties aiProviderProperties;
    private AiRateLimitProperties aiRateLimitProperties;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        mockMessage = "Provider disabled Invalid API key format Maximum number of API keys Maximum number of AI keys not found Decryption failed Vault key invalid No global key API key not found Active API key not found API Key Resource not found Failed to decrypt API key No vault key configured";
        
        i18nService = new I18nService(null) {
            @Override
            public String getMessage(MessageKey key) { return mockMessage; }
            @Override
            public String getMessage(MessageKey key, Object... args) { return mockMessage; }
            @Override
            public String getMessage(MessageKey key, Locale locale) { return mockMessage; }
        };

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
                globalApiKeyRepository,
                aiRateLimitProperties,
                meterRegistry,
                Optional.empty(),
                i18nService
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
    void saveKey_storesCorrectHint() {
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveKey(10, AiProvider.OPENAI, "sk-test123456");

        ArgumentCaptor<UserApiKey> captor = ArgumentCaptor.forClass(UserApiKey.class);
        verify(userApiKeyRepository).save(captor.capture());
        assertEquals("****3456", captor.getValue().getKeyHint());
    }

    @Test
    void saveKey_storesCorrectVersion() {
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveKey(10, AiProvider.OPENAI, "sk-test123456");

        ArgumentCaptor<UserApiKey> captor = ArgumentCaptor.forClass(UserApiKey.class);
        verify(userApiKeyRepository).save(captor.capture());
        assertEquals(aiVaultProperties.getCurrentKeyVersion(), captor.getValue().getEncryptionKeyVersion());
    }

    @Test
    void saveKey_formatIsVersionIvCiphertext() {
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveKey(10, AiProvider.OPENAI, "sk-format1234");

        ArgumentCaptor<UserApiKey> captor = ArgumentCaptor.forClass(UserApiKey.class);
        verify(userApiKeyRepository).save(captor.capture());
        String encrypted = captor.getValue().getEncryptedKey();

        String[] parts = encrypted.split(":", 3);
        assertEquals(3, parts.length);
        assertEquals("1", parts[0]);
        assertTrue(Base64.getDecoder().decode(parts[1]).length > 0);
        assertTrue(Base64.getDecoder().decode(parts[2]).length > 0);
    }

    @Test
    void saveKey_disabledProvider_throwsException() {
        aiProviderProperties.getConfigs().get("OPENAI").setEnabled(false);

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
            () -> service.saveKey(10, AiProvider.OPENAI, "sk-test123456"));
        assertTrue(ex.getMessage().contains("Provider disabled"));
    }

    @Test
    void saveKey_invalidPrefix_throwsException() {
        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
            () -> service.saveKey(10, AiProvider.OPENAI, "invalid-key"));
        assertTrue(ex.getMessage().contains("Invalid API key format"));
    }

    @Test
    void saveKey_exceedsMaxKeysPerUser_throwsException() {
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of(new UserApiKey(), new UserApiKey()));

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
            () -> service.saveKey(10, AiProvider.OPENAI, "sk-test123456"));
        assertTrue(ex.getMessage().contains("Maximum number of API keys"));
    }

    @Test
    void saveKey_existingKey_replacesIt() {
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
    void getDecryptedKey_roundTrip() {
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
    void getDecryptedKey_specialCharacters() {
        AtomicReference<UserApiKey> stored = new AtomicReference<>();
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> {
            UserApiKey key = invocation.getArgument(0);
            stored.set(key);
            return key;
        });
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        String original = "sk-áßÇñ-+/=?$#";
        service.saveKey(10, AiProvider.OPENAI, original);

        assertEquals(original, service.getDecryptedKey(10, AiProvider.OPENAI));
    }

    @Test
    void getDecryptedKey_withNonExistentKey_throwsException() {
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
            () -> service.getDecryptedKey(10, AiProvider.OPENAI));
        assertTrue(ex.getMessage().contains("Active API key not found"));
    }

    @Test
    void updateKey_updatesEncryptedValue() {
        UserApiKey existing = new UserApiKey();
        existing.setProvider(AiProvider.OPENAI);
        existing.setEncryptedKey("old-encrypted");
        existing.setEncryptionKeyVersion(1);

        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI)).thenReturn(Optional.of(existing));
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateKey(10, AiProvider.OPENAI, "sk-updated-1234");

        assertNotEquals("old-encrypted", existing.getEncryptedKey());
        assertEquals("****1234", existing.getKeyHint());
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

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
            () -> service.getDecryptedKey(10, AiProvider.OPENAI));
        assertTrue(ex.getMessage().contains("Failed to decrypt API key")
            || ex.getMessage().contains("No vault key configured"));
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
    void reEncryptAll_rotatesKeys() {
        AtomicReference<UserApiKey> stored = new AtomicReference<>();
        when(userApiKeyRepository.findByUserIdAndProvider(10, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(userApiKeyRepository.findByUserIdAndActiveTrue(10)).thenReturn(List.of());
        when(userApiKeyRepository.save(any(UserApiKey.class))).thenAnswer(invocation -> {
            UserApiKey key = invocation.getArgument(0);
            stored.set(key);
            return key;
        });
        when(userApiKeyRepository.findByEncryptionKeyVersion(1)).thenAnswer(invocation -> List.of(stored.get()));
        when(userApiKeyRepository.findByUserIdAndProviderAndActiveTrue(10, AiProvider.OPENAI))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        String original = "sk-rotate-7788";
        service.saveKey(10, AiProvider.OPENAI, original);
        service.reEncryptAll(1, 2);

        assertEquals(2, stored.get().getEncryptionKeyVersion());
        assertEquals(original, service.getDecryptedKey(10, AiProvider.OPENAI));
    }

    @Test
    void deleteKey_verifiesOwnership() {
        when(userApiKeyRepository.findByIdAndUserId(1L, 10)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
            () -> service.deleteKey(10, 1L));
        assertTrue(ex.getMessage().contains("API key not found"));
    }

    @Test
    void deleteKey_removesFromDb() {
        UserApiKey key = new UserApiKey();
        key.setId(5L);
        key.setProvider(AiProvider.OPENAI);
        when(userApiKeyRepository.findByIdAndUserId(5L, 10)).thenReturn(Optional.of(key));

        service.deleteKey(10, 5L);

        verify(userApiKeyRepository).delete(key);
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
