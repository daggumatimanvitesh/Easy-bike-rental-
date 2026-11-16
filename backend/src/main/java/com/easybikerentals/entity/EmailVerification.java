package com.easybikerentals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Stores only a password hash and a hash of the one-time verification token. */
@Entity
@Table(name = "email_verifications", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class EmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 180, unique = true)
    public String email;

    @Column(length = 120)
    public String name;

    @Column(length = 255)
    public String passwordHash;

    @Column(length = 40)
    public String phone;

    @Column(length = 500)
    public String address;

    @Column(length = 64)
    public String tokenHash;

    public Instant expiresAt;
    public Instant lastSentAt;
    public Instant sendWindowStartedAt;
    @Column(nullable = false)
    public int sendsInWindow;
    public Instant verifiedAt;

    public EmailVerification() {}
}
