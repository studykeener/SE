package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_behavior_records")
public class UserBehaviorRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long platformUserId;

    @Column(nullable = false, length = 50)
    private String behaviorType;

    @Column(nullable = false, length = 1000)
    private String behaviorDetail;

    @Column(nullable = false)
    private LocalDateTime behaviorTime = LocalDateTime.now();
}
