package com.healthcare.sandbox.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_authorization_codes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthCodeEntity {

    @Id
    private String code;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String patientId;

    private String redirectUri;

    private String scope;

    private String state;

    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private Boolean isConsumed;
}
