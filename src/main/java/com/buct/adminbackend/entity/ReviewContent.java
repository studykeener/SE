package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.ContentType;
import com.buct.adminbackend.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "review_contents")
public class ReviewContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Column(name = "source_system", nullable = false, length = 20)
    private String sourceSystem;

    @Column(name = "source_table", length = 50)
    private String sourceTable;

    @Column(name = "source_record_id")
    private Long sourceRecordId;

    @Column(name = "submitter_user_id")
    private Long submitterUserId;

    @Column(length = 100)
    private String submitter;

    @Column(name = "artifact_id", length = 64)
    private String artifactId;

    @Column(name = "content_text", nullable = false, length = 2000)
    private String contentText;

    @Column(name = "content_url", length = 500)
    private String contentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore = 0;

    @Column(name = "submit_time", nullable = false)
    private LocalDateTime submitTime = LocalDateTime.now();

    @Column(name = "review_time")
    private LocalDateTime reviewTime;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(length = 50)
    private String reviewer;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "auto_reviewed", nullable = false)
    private Boolean autoReviewed = false;

    @Column(name = "auto_decision_note", length = 500)
    private String autoDecisionNote;

    @Column(name = "recheck_required", nullable = false)
    private Boolean recheckRequired = false;
}
