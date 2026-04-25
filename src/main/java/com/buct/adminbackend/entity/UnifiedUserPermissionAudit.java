package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "unified_user_permission_audit")
public class UnifiedUserPermissionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String operator;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus newStatus;

    private Boolean oldCommentAllowed;

    private Boolean newCommentAllowed;

    private Boolean oldUploadAllowed;

    private Boolean newUploadAllowed;

    @Column(length = 256)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime operatedAt = LocalDateTime.now();
}

