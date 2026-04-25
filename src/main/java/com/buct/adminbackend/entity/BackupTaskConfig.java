package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "backup_task_config")
public class BackupTaskConfig {
    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private Boolean autoEnabled = true;

    @Column(nullable = false, length = 64)
    private String cronExpression = "0 0 2 * * *";

    @Column(nullable = false)
    private Integer retentionDays = 30;

    private LocalDateTime lastAutoRun;
}

