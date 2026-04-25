package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.AutoReviewAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "review_strategy_config")
public class ReviewStrategyConfig {

    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private Integer lowRiskThreshold = 30;

    @Column(nullable = false)
    private Integer highRiskThreshold = 80;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutoReviewAction lowRiskAction = AutoReviewAction.AUTO_APPROVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutoReviewAction highRiskAction = AutoReviewAction.MANUAL_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutoReviewAction imageViolationAction = AutoReviewAction.AUTO_REJECT;
}
