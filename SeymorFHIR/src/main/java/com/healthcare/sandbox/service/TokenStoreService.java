package com.healthcare.sandbox.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TokenStoreService {

    public record TokenMetadata(String token, String patientId, String clientId, LocalDateTime expiresAt) {}

    // In-memory active OAuth2 token registry
    private final Map<String, TokenMetadata> activeTokens = new ConcurrentHashMap<>();

    public void registerToken(String rawToken, String patientId, String clientId, int expiresInSeconds) {
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresInSeconds);
        TokenMetadata metadata = new TokenMetadata(rawToken, patientId, clientId, expiresAt);
        activeTokens.put(rawToken, metadata);
        log.info("[TOKEN_REGISTERED] Issued valid OAuth2 token for Client: {} with Patient Context: {}", clientId, patientId);
    }

    public boolean isValidToken(String rawToken) {
        if (rawToken == null || !activeTokens.containsKey(rawToken)) {
            return false;
        }

        TokenMetadata metadata = activeTokens.get(rawToken);
        if (LocalDateTime.now().isAfter(metadata.expiresAt())) {
            activeTokens.remove(rawToken); // Expired token cleanup
            log.warn("[TOKEN_EXPIRED] Expired OAuth2 token presented for Client: {}", metadata.clientId());
            return false;
        }

        return true;
    }

    public TokenMetadata getTokenMetadata(String rawToken) {
        return activeTokens.get(rawToken);
    }
}
