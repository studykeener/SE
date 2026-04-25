package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "unified_users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_unified_source_user", columnNames = {"sourceSystem", "sourceUserId"})
})
public class UnifiedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(length = 128)
    private String displayName;

    @Column(length = 128)
    private String email;

    @Column(length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ENABLED;

    @Column(nullable = false, length = 32)
    private String sourceSystem;

    @Column(nullable = false, length = 64)
    private String sourceUserId;

    @Column(nullable = false)
    private Boolean commentAllowed = true;

    @Column(nullable = false)
    private Boolean uploadAllowed = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

