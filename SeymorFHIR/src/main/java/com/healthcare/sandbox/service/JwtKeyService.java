package com.healthcare.sandbox.service;

import com.healthcare.sandbox.model.OAuthKey;
import com.healthcare.sandbox.repository.OAuthKeyRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtKeyService {

    private final OAuthKeyRepository oauthKeyRepository;

    @Value("${smart.jwt.key-id:seymour-smart-key-1}")
    private String configuredKeyId;

    @Value("${smart.jwt.issuer:http://localhost:8090}")
    private String issuer;

    @Value("${smart.jwt.audience:http://localhost:8090/api/fhir}")
    private String audience;

    private RSAKey rsaKey;
    private JWSSigner signer;

    /**
     * Initializes the RSA 2048-bit KeyPair for RS256 JWT signing.
     * Checks PostgreSQL database for active key; if not found, generates and persists a new KeyPair.
     * 
     * PRODUCTION ARCHITECTURE NOTE:
     * In enterprise production environments (such as PHSA or Fraser Health), private RSA keys are 
     * NOT generated dynamically in RAM on JVM startup. Instead, load keys using one of the following patterns:
     * 
     * 1. Cloud Key Vault: Fetch RSA Private/Public PEM keys from AWS Secrets Manager, 
     *    Azure Key Vault, or HashiCorp Vault during bean initialization.
     * 2. Database Key Store: Load active and rotated keys from a persistent `oauth_keys` DB table 
     *    to enable zero-downtime key rotation across multiple load-balanced microservice nodes.
     */
    @PostConstruct
    public void init() {
        try {
            log.info("Initializing SMART-on-FHIR RS256 JWT Key Pair (Configured Default Key ID: {})", configuredKeyId);
            
            KeyPair keyPair = loadOrGenerateKeyPair();

            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(configuredKeyId)
                    .build();

            signer = new RSASSASigner(rsaKey);
            log.info("RSA 2048-bit Key Pair successfully initialized in memory. Key ID: {}", configuredKeyId);
        } catch (Exception e) {
            log.error("Failed to initialize RSA keypair for JWT signing", e);
            throw new RuntimeException("JwtKeyService initialization failure", e);
        }
    }

    /**
     * Loads an active RSA KeyPair from PostgreSQL DB, or generates and persists a new one if missing.
     * 
     * @return KeyPair object containing Public and Private RSA keys
     */
    public KeyPair loadOrGenerateKeyPair() throws Exception {
        Optional<OAuthKey> existingKeyOpt = oauthKeyRepository.findFirstByActiveTrueOrderByCreatedAtDesc();

        if (existingKeyOpt.isPresent()) {
            OAuthKey oauthKey = existingKeyOpt.get();
            configuredKeyId = oauthKey.getKeyId();
            log.info("[KEY_LOADED_DB] Loaded active persistent RSA 2048-bit Key Pair from PostgreSQL (Key ID: {})", configuredKeyId);
            
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(parsePem(oauthKey.getPrivateKeyPem()));
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(privSpec);

            X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(parsePem(oauthKey.getPublicKeyPem()));
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(pubSpec);

            return new KeyPair(publicKey, privateKey);
        }

        // Generate new RSA 2048-bit KeyPair
        log.info("[KEY_GENERATE_NEW] No active RSA key found in database. Generating fresh 2048-bit Key Pair...");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Convert to PEM strings
        String privatePem = convertToPem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String publicPem = convertToPem("PUBLIC KEY", keyPair.getPublic().getEncoded());

        // Persist to PostgreSQL database
        OAuthKey newKey = OAuthKey.builder()
                .keyId(configuredKeyId)
                .privateKeyPem(privatePem)
                .publicKeyPem(publicPem)
                .algorithm("RS256")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        oauthKeyRepository.save(newKey);
        log.info("[KEY_PERSISTED_DB] Persistent RSA 2048-bit Key Pair saved to PostgreSQL (Key ID: {})", configuredKeyId);

        return keyPair;
    }

    public Map<String, Object> getJwks() {
        JWKSet jwkSet = new JWKSet(rsaKey.toPublicJWK());
        return jwkSet.toJSONObject();
    }

    public String generateSignedSmartJwt(String clientId, String patientId, String scope, long validitySeconds) {
        try {
            Date now = new Date();
            Date expiration = new Date(now.getTime() + (validitySeconds * 1000));

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(clientId)
                    .audience(audience)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(now)
                    .expirationTime(expiration)
                    .claim("client_id", clientId)
                    .claim("patient", patientId)
                    .claim("scope", scope)
                    .claim("token_use", "access_token")
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey.getKeyID())
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("Failed to generate signed SMART RS256 JWT", e);
            throw new RuntimeException("JWT signing failure", e);
        }
    }

    public RSAKey getPublicRsaKey() {
        return rsaKey.toPublicJWK();
    }

    private String convertToPem(String type, byte[] keyBytes) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyBytes);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }

    private byte[] parsePem(String pem) {
        String cleaned = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(cleaned);
    }
}
