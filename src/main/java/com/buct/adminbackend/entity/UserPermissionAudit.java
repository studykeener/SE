package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_permission_audit")
public class UserPermissionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    @Column(name = "old_status")
    private Integer oldStatus;

    @Column(name = "new_status")
    private Integer newStatus;

    @Column(name = "old_can_comment")
    private Boolean oldCanComment;

    @Column(name = "new_can_comment")
    private Boolean newCanComment;

    @Column(name = "old_can_upload")
    private Boolean oldCanUpload;

    @Column(name = "new_can_upload")
    private Boolean newCanUpload;

    @Column(length = 256)
    private String reason;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt = LocalDateTime.now();
}
