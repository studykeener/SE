package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "restore_logs")
public class RestoreLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "backup_record_id", nullable = false)
    private Long backupRecordId;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "confirm_text", nullable = false, length = 32)
    private String confirmText;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @Column(name = "restore_scope", nullable = false, length = 20)
    private String restoreScope;

    @Column(name = "table_scope", length = 500)
    private String tableScope;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
