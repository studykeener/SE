package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "data_change_logs")
public class DataChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String operator;

    @Column(nullable = false, length = 50)
    private String changeType;

    @Column(nullable = false, length = 100)
    private String targetType;

    @Column(nullable = false, length = 100)
    private String targetId;

    @Column(length = 1000)
    private String detail;

    @Column(nullable = false)
    private LocalDateTime changeTime = LocalDateTime.now();
}
