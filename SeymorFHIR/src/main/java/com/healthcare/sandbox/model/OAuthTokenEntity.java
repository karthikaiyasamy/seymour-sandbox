package com.healthcare.sandbox.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_access_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthTokenEntity {

    @Id
    private String token;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String patientId;

    @Column(nullable = false)
    private String scope;

    private String tokenType;

    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
