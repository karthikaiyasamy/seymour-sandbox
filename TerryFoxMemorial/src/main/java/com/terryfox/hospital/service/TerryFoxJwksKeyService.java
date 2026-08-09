package com.terryfox.hospital.service;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TerryFoxJwksKeyService {

    @Value("${seymour.auth.jwks-url:http://localhost:8090/.well-known/jwks.json}")
    private String jwksUrl;

    private final RestTemplate restTemplate;
    private final Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();

    /**
     * Verifies an incoming RS256 Bearer JWT token against Seymour Auth Server's
     * public JWKS keys.
     * 
     * @param token Bearer JWT token string
     * @return ClaimsSet if signature is cryptographically valid and token is
     *         active, null otherwise
     */
    public JWTClaimsSet verifySignedJwt(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String keyId = signedJWT.getHeader().getKeyID();

            RSAPublicKey publicKey = getPublicKey(keyId);
            if (publicKey == null) {
                log.warn("[JWKS_KEY_NOT_FOUND] Could not resolve public key for Key ID: {} from JWKS endpoint: {}",
                        keyId, jwksUrl);
                return null;
            }

            RSASSAVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                log.warn("[AUTH_INVALID_SIGNATURE] RS256 JWT signature verification failed for Key ID: {}", keyId);
                return null;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration != null && new Date().after(expiration)) {
                log.warn("[AUTH_EXPIRED_TOKEN] RS256 JWT has expired for Subject: {}", claims.getSubject());
                return null;
            }

            log.info("[JWKS_AUTH_SUCCESS] Statistically verified RS256 Bearer JWT for Subject: {} (Client: {})",
                    claims.getSubject(), claims.getClaim("client_id"));
            return claims;
        } catch (Exception e) {
            log.error("[JWKS_AUTH_ERROR] Exception occurred during RS256 JWT verification", e);
            return null;
        }
    }

    /**
     * Retrieves public RSA key from cache; fetches from Seymour JWKS endpoint if
     * missing.
     */
    public RSAPublicKey getPublicKey(String keyId) {
        if (keyId != null && keyCache.containsKey(keyId)) {
            return keyCache.get(keyId);
        }

        log.warn(
                "[JWKS_CACHE_EVICT] Unknown keyId [{}] presented in JWT header. Evicting cache and re-fetching JWKS from Seymour Auth Server...",
                keyId);
        refreshJwksCache();

        if (keyId != null) {
            return keyCache.get(keyId);
        }

        return keyCache.values().stream().findFirst().orElse(null);
    }

    /**
     * Fetches public JWKS JSON from Seymour Auth Server at
     * http://localhost:8090/.well-known/jwks.json
     */
    public synchronized void refreshJwksCache() {
        try {
            log.info("[JWKS_FETCH_INIT] Fetching public RSA keys from Seymour Auth Server: {}", jwksUrl);
            String jwksJson = restTemplate.getForObject(jwksUrl, String.class);
            if (jwksJson != null) {
                JWKSet jwkSet = JWKSet.parse(jwksJson);
                for (JWK jwk : jwkSet.getKeys()) {
                    if (jwk instanceof RSAKey rsaJwk) {
                        RSAPublicKey publicKey = rsaJwk.toRSAPublicKey();
                        if (rsaJwk.getKeyID() != null) {
                            keyCache.put(rsaJwk.getKeyID(), publicKey);
                        }
                    }
                }
                log.info("[JWKS_CACHE_UPDATED] Successfully cached {} public RSA key(s) from Seymour Auth Server",
                        keyCache.size());
            }
        } catch (Exception e) {
            log.error("[JWKS_FETCH_FAILED] Failed to fetch public keys from Seymour Auth Server at {}", jwksUrl, e);
        }
    }
}
