package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_upload_photo")
public class UserUploadPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photo_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "museum_id")
    private Integer museumId;

    @Column(name = "object_id", length = 255)
    private String objectId;

    @Column(name = "photo_url", nullable = false, length = 500)
    private String photoUrl;

    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String location;

    @Column(nullable = false, length = 20)
    private String source = "web";

    @Convert(converter = PhotoAuditStatusConverter.class)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "audit_method", nullable = false)
    private Byte auditMethod = 1;

    @Column(name = "auto_audit_status")
    private Byte autoAuditStatus;

    @Column(name = "auto_audit_score", precision = 5, scale = 2)
    private BigDecimal autoAuditScore;

    @Column(name = "auditor_id")
    private Long auditorId;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
