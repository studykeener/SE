package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "operation_logs")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 50)
    private String operator;

    @Column(length = 32)
    private String module;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(name = "operation_target", nullable = false, length = 100)
    private String operationTarget;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "before_data", columnDefinition = "JSON")
    private String beforeData;

    @Column(name = "after_data", columnDefinition = "JSON")
    private String afterData;

    @Column(length = 1000)
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "operation_time", nullable = false)
    private LocalDateTime operationTime = LocalDateTime.now();
}
