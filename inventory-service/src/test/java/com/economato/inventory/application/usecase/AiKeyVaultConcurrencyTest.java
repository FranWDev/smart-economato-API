package com.economato.inventory.application.usecase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserApiKeyRepository;
import com.economato.inventory.infrastructure.config.ai.AiProviderProperties;
import com.economato.inventory.infrastructure.config.ai.AiRateLimitProperties;
import com.economato.inventory.infrastructure.config.ai.AiVaultProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiKeyVaultConcurrencyTest {

    @Mock
    private UserApiKeyRepository userApiKeyRepository;

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
                aiRateLimitProperties,
                new SimpleMeterRegistry(),
                Optional.empty()
        );
    }

    @Test
    void concurrentDecrypts_sameKey_consistent() throws Exception {
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

        service.saveKey(10, AiProvider.OPENAI, "sk-concurrent-1234");

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    synchronized (results) {
                        results.add(service.getDecryptedKey(10, AiProvider.OPENAI));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(threadCount, results.size());
        assertTrue(results.stream().allMatch("sk-concurrent-1234"::equals));
    }

    @Test
    void concurrentReEncryptAll_noDataLoss() throws Exception {
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
        when(userApiKeyRepository.findByEncryptionKeyVersion(1)).thenAnswer(invocation -> List.of(stored.get()));
        when(userApiKeyRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveKey(10, AiProvider.OPENAI, "sk-reencrypt-1234");

        CountDownLatch latch = new CountDownLatch(3);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    service.reEncryptAll(1, 2);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertNotNull(stored.get());
        assertEquals(2, stored.get().getEncryptionKeyVersion());
    }
}