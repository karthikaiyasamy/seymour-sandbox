package com.healthcare.sandbox.repository;

import com.healthcare.sandbox.model.OAuthCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthCodeRepository extends JpaRepository<OAuthCodeEntity, String> {
    Optional<OAuthCodeEntity> findByCodeAndIsConsumedFalse(String code);
}
