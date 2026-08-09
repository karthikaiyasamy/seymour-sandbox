package com.terryfox.hospital.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TerryFoxJwksKeyServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private TerryFoxJwksKeyService jwksKeyService;

    private KeyPair keyPair1;
    private String keyId1 = "seymour-key-1";

    private KeyPair keyPair2;
    private String keyId2 = "seymour-key-rotated-2";

    @BeforeEach
    void setUp() throws Exception {
        jwksKeyService = new TerryFoxJwksKeyService(restTemplate);
        ReflectionTestUtils.setField(jwksKeyService, "jwksUrl", "http://localhost:8090/.well-known/jwks.json");

        // Generate RSA KeyPair 1
        KeyPairGenerator kpg1 = KeyPairGenerator.getInstance("RSA");
        kpg1.initialize(2048);
        keyPair1 = kpg1.generateKeyPair();

        // Generate RSA KeyPair 2 (Rotated Key)
        KeyPairGenerator kpg2 = KeyPairGenerator.getInstance("RSA");
        kpg2.initialize(2048);
        keyPair2 = kpg2.generateKeyPair();
    }

    @Test
    @DisplayName("Should successfully verify RS256 Bearer JWT signed with active JWKS public key")
    void testVerifySignedJwt_Success() throws Exception {
        // Build mock JWKS payload
        RSAKey rsaJwk = new RSAKey.Builder((RSAPublicKey) keyPair1.getPublic())
                .keyID(keyId1)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        JWKSet jwkSet = new JWKSet(rsaJwk);
        String jwksJson = jwkSet.toJSONObject().toString();

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jwksJson);

        // Sign token with keyPair1
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId1).build(),
                new JWTClaimsSet.Builder()
                        .subject("seymour_smart_app")
                        .claim("client_id", "seymour_smart_app")
                        .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                        .build()
        );
        signedJWT.sign(new RSASSASigner((RSAPrivateKey) keyPair1.getPrivate()));
        String token = signedJWT.serialize();

        // Execute verification
        JWTClaimsSet claims = jwksKeyService.verifySignedJwt(token);

        assertNotNull(claims);
        assertEquals("seymour_smart_app", claims.getSubject());
        assertEquals("seymour_smart_app", claims.getClaim("client_id"));
    }

    @Test
    @DisplayName("Should trigger dynamic kid cache eviction and re-fetch JWKS when unknown rotated key ID is presented")
    void testVerifySignedJwt_DynamicKidCacheEviction() throws Exception {
        // Step 1: Initial JWKS response with key 1
        RSAKey rsaJwk1 = new RSAKey.Builder((RSAPublicKey) keyPair1.getPublic())
                .keyID(keyId1)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        JWKSet jwkSet1 = new JWKSet(rsaJwk1);

        // Step 2: Rotated JWKS response containing BOTH key 1 and key 2
        RSAKey rsaJwk2 = new RSAKey.Builder((RSAPublicKey) keyPair2.getPublic())
                .keyID(keyId2)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        JWKSet jwkSetRotated = new JWKSet(java.util.List.of(rsaJwk1, rsaJwk2));

        // Configure mock to return jwkSet1 first, then jwkSetRotated on cache eviction
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(jwkSet1.toJSONObject().toString())
                .thenReturn(jwkSetRotated.toJSONObject().toString());

        // Token 1 signed with Key 1
        SignedJWT token1 = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId1).build(),
                new JWTClaimsSet.Builder().subject("patient_app").expirationTime(new Date(System.currentTimeMillis() + 3600000)).build()
        );
        token1.sign(new RSASSASigner((RSAPrivateKey) keyPair1.getPrivate()));
        JWTClaimsSet claims1 = jwksKeyService.verifySignedJwt(token1.serialize());
        assertNotNull(claims1);

        // Token 2 signed with NEW Rotated Key 2 (triggers kid cache eviction!)
        SignedJWT token2 = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId2).build(),
                new JWTClaimsSet.Builder().subject("patient_app").expirationTime(new Date(System.currentTimeMillis() + 3600000)).build()
        );
        token2.sign(new RSASSASigner((RSAPrivateKey) keyPair2.getPrivate()));

        JWTClaimsSet claims2 = jwksKeyService.verifySignedJwt(token2.serialize());

        assertNotNull(claims2);
        assertEquals("patient_app", claims2.getSubject());
        verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
    }

    @Test
    @DisplayName("Should reject token signed with mismatched private key signature")
    void testVerifySignedJwt_InvalidSignature() throws Exception {
        // JWKS public key is Key 1
        RSAKey rsaJwk1 = new RSAKey.Builder((RSAPublicKey) keyPair1.getPublic())
                .keyID(keyId1)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(new JWKSet(rsaJwk1).toJSONObject().toString());

        // Token signed with Key 2 private key, but claiming keyId1 in header!
        SignedJWT tamperedToken = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId1).build(),
                new JWTClaimsSet.Builder().subject("hacker_app").expirationTime(new Date(System.currentTimeMillis() + 3600000)).build()
        );
        tamperedToken.sign(new RSASSASigner((RSAPrivateKey) keyPair2.getPrivate()));

        JWTClaimsSet claims = jwksKeyService.verifySignedJwt(tamperedToken.serialize());
        assertNull(claims, "Token with tampered/invalid signature MUST be rejected!");
    }

    @Test
    @DisplayName("Should reject expired Bearer JWT tokens")
    void testVerifySignedJwt_ExpiredToken() throws Exception {
        RSAKey rsaJwk1 = new RSAKey.Builder((RSAPublicKey) keyPair1.getPublic())
                .keyID(keyId1)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(new JWKSet(rsaJwk1).toJSONObject().toString());

        // Expired token (1 hour in the past)
        SignedJWT expiredToken = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId1).build(),
                new JWTClaimsSet.Builder().subject("patient_app").expirationTime(new Date(System.currentTimeMillis() - 3600000)).build()
        );
        expiredToken.sign(new RSASSASigner((RSAPrivateKey) keyPair1.getPrivate()));

        JWTClaimsSet claims = jwksKeyService.verifySignedJwt(expiredToken.serialize());
        assertNull(claims, "Expired Bearer token MUST be rejected!");
    }
}
