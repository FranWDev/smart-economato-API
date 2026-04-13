package com.economato.inventory.infrastructure.adapter.out.persistence.repository;

import com.economato.inventory.domain.model.AiProvider;
import com.economato.inventory.domain.model.UserApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserApiKeyRepository extends JpaRepository<UserApiKey, Long> {

    Optional<UserApiKey> findByUserIdAndProviderAndActiveTrue(Integer userId, AiProvider provider);

    List<UserApiKey> findByUserIdAndActiveTrue(Integer userId);

    Optional<UserApiKey> findByIdAndUserId(Long id, Integer userId);

    boolean existsByUserIdAndProvider(Integer userId, AiProvider provider);

    List<UserApiKey> findByEncryptionKeyVersion(int version);
}
