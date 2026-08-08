package com.healthcare.sandbox.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class JwtKeyService {

    private RSAKey rsaKey;
    private JWSSigner signer;

    @PostConstruct
    public void init() {
        try {
            log.info("Generating RSA 2048-bit Key Pair for SMART-on-FHIR RS256 JWT Signing");
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            String keyId = "seymour-smart-key-1";
            rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .build();

            signer = new RSASSASigner(rsaKey);
            log.info("RSA 2048-bit Key Pair successfully generated. Key ID: {}", keyId);
        } catch (Exception e) {
            log.error("Failed to initialize RSA keypair for JWT signing", e);
            throw new RuntimeException("JwtKeyService initialization failure", e);
        }
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
                    .issuer("http://localhost:8090")
                    .subject(clientId)
                    .audience("http://localhost:8090/api/fhir")
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
}
