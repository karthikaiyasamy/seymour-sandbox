package com.healthcare.sandbox.repository;

import com.healthcare.sandbox.model.OAuthKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthKeyRepository extends JpaRepository<OAuthKey, String> {

    Optional<OAuthKey> findFirstByActiveTrueOrderByCreatedAtDesc();

    Optional<OAuthKey> findByKeyId(String keyId);
}
