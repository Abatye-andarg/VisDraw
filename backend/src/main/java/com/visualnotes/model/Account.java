package com.visualnotes.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(name = "uk_accounts_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, nullable = false, updatable = false, columnDefinition = "char(36)")
    private String id;

    @Column(nullable = false, length = 254, columnDefinition = "varchar(254) collate utf8mb4_bin")
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamp(6)")
    private Instant createdAt;

    public Account(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }
}
