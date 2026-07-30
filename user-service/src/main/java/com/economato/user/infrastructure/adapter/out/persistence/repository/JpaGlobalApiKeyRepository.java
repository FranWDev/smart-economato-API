package com.economato.user.infrastructure.adapter.out.persistence.repository;

import com.economato.user.domain.model.AiProvider;
import com.economato.user.domain.model.GlobalApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaGlobalApiKeyRepository extends JpaRepository<GlobalApiKey, Long> {
    Optional<GlobalApiKey> findByProviderAndActiveTrue(AiProvider provider);
    Optional<GlobalApiKey> findByProvider(AiProvider provider);
    List<GlobalApiKey> findAllByActiveTrue();
    List<GlobalApiKey> findByEncryptionKeyVersion(int version);
}
