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

    @Column(name = "auto_enabled", nullable = false)
    private Boolean autoEnabled = true;

    @Column(name = "cron_expression", nullable = false, length = 64)
    private String cronExpression = "0 0 2 * * *";

    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays = 30;

    @Column(name = "last_auto_run")
    private LocalDateTime lastAutoRun;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
