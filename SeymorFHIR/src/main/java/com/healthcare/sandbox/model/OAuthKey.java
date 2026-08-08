package com.healthcare.sandbox.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthKey {

    @Id
    @Column(name = "key_id", nullable = false, unique = true)
    private String keyId;

    @Column(name = "private_key_pem", nullable = false, columnDefinition = "TEXT")
    private String privateKeyPem;

    @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
    private String publicKeyPem;

    @Column(name = "algorithm", length = 50)
    private String algorithm;

    @Column(name = "is_active")
    private Boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
