package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.economato.inventory.domain.model.AiProvider;
import com.economato.inventory.domain.model.GlobalApiKey;

public interface GlobalApiKeyRepository extends JpaRepository<GlobalApiKey, Long> {

    Optional<GlobalApiKey> findByProviderAndActiveTrue(AiProvider provider);

    Optional<GlobalApiKey> findByProvider(AiProvider provider);

    List<GlobalApiKey> findAllByActiveTrue();

    List<GlobalApiKey> findByEncryptionKeyVersion(int version);
}