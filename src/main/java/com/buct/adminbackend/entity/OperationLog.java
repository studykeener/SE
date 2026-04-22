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

    @Column(nullable = false, length = 50)
    private String operator;

    @Column(nullable = false, length = 50)
    private String operationType;

    @Column(nullable = false, length = 100)
    private String operationTarget;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private LocalDateTime operationTime = LocalDateTime.now();
}
