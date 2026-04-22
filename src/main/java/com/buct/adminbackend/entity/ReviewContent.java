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
    @Column(nullable = false, length = 20)
    private ContentType contentType;

    @Column(nullable = false, length = 20)
    private String sourceSystem;

    @Column(nullable = false, length = 100)
    private String submitter;

    @Column(nullable = false, length = 2000)
    private String contentText;

    @Column(length = 500)
    private String contentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(nullable = false)
    private Integer riskScore = 0;

    @Column(nullable = false)
    private LocalDateTime submitTime = LocalDateTime.now();

    private LocalDateTime reviewTime;

    @Column(length = 50)
    private String reviewer;

    @Column(length = 500)
    private String rejectReason;
}
