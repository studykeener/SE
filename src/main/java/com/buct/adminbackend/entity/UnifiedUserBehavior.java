package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "unified_user_behaviors")
public class UnifiedUserBehavior {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String behaviorType;

    @Column(length = 2000)
    private String behaviorContent;

    @Column(nullable = false, length = 32)
    private String sourceSystem;

    @Column(length = 64)
    private String sourceRecordId;

    @Column(nullable = false)
    private LocalDateTime behaviorTime = LocalDateTime.now();
}

