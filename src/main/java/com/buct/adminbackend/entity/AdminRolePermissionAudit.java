package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "admin_role_permission_audit")
public class AdminRolePermissionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "before_snapshot", columnDefinition = "JSON")
    private String beforeSnapshot;

    @Column(name = "after_snapshot", columnDefinition = "JSON")
    private String afterSnapshot;

    @Column(length = 256)
    private String reason;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt = LocalDateTime.now();
}
