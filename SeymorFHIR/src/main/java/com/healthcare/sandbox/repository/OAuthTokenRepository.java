package com.healthcare.sandbox.repository;

import com.healthcare.sandbox.model.OAuthTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthTokenEntity, String> {
    Optional<OAuthTokenEntity> findByTokenAndExpiresAtAfter(String token, LocalDateTime now);
}
