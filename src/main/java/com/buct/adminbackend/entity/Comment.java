package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.ReviewStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "museum_id", nullable = false)
    private Integer museumId;

    @Column(name = "object_id", nullable = false, length = 255)
    private String objectId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 20)
    private String source = "web";

    @Column(name = "audit_method", nullable = false)
    private Byte auditMethod = 1;

    @Convert(converter = CommentAuditStatusConverter.class)
    @Column(name = "audit_status", nullable = false)
    private ReviewStatus auditStatus = ReviewStatus.PENDING;

    @Column(name = "auto_audit_status")
    private Byte autoAuditStatus;

    @Column(name = "sensitive_words_hit", length = 255)
    private String sensitiveWordsHit;

    @Column(name = "auditor_id")
    private Long auditorId;

    @Column(nullable = false)
    private Byte status = 1;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "delete_reason", length = 255)
    private String deleteReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @JsonProperty("sourceSystem")
    public String getSourceSystem() {
        return source;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
