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
    private Integer lowRiskMaxScore = 20;

    @Column(nullable = false)
    private Integer mediumRiskMaxScore = 60;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutoReviewAction lowRiskAction = AutoReviewAction.AUTO_APPROVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutoReviewAction mediumRiskAction = AutoReviewAction.MANUAL_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutoReviewAction highRiskAction = AutoReviewAction.AUTO_REJECT;
}
