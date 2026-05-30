package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.AutoReviewAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "review_strategy_config")
public class ReviewStrategyConfig {

    @Id
    private Long id = 1L;

    @Column(name = "low_risk_max_score", nullable = false)
    private Integer lowRiskMaxScore = 20;

    @Column(name = "medium_risk_max_score", nullable = false)
    private Integer mediumRiskMaxScore = 60;

    @Enumerated(EnumType.STRING)
    @Column(name = "low_risk_action", nullable = false, length = 30)
    private AutoReviewAction lowRiskAction = AutoReviewAction.AUTO_APPROVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "medium_risk_action", nullable = false, length = 30)
    private AutoReviewAction mediumRiskAction = AutoReviewAction.MANUAL_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "high_risk_action", nullable = false, length = 30)
    private AutoReviewAction highRiskAction = AutoReviewAction.AUTO_REJECT;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
