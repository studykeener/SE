package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_behaviors")
public class UserBehavior {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "behavior_type", nullable = false, length = 32)
    private String behaviorType;

    @Column(name = "behavior_content", length = 2000)
    private String behaviorContent;

    @Column(name = "source_system", nullable = false, length = 32)
    private String sourceSystem;

    @Column(name = "source_record_id", length = 64)
    private String sourceRecordId;

    @Column(name = "behavior_time", nullable = false)
    private LocalDateTime behaviorTime = LocalDateTime.now();
}
