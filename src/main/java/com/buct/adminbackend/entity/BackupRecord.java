package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "backup_records")
public class BackupRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 128)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "backup_type", nullable = false, length = 20)
    private String backupType;

    @Column(name = "table_scope", length = 500)
    private String tableScope;

    @Column(name = "file_size", nullable = false)
    private Long fileSize = 0L;

    @Column(nullable = false)
    private Boolean encrypted = true;

    @Column(length = 64)
    private String checksum;

    @Column(nullable = false, length = 20)
    private String status = "SUCCESS";

    @Column(length = 1000)
    private String note;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(length = 64)
    private String operator;

    @Column(name = "backup_time", nullable = false)
    private LocalDateTime backupTime = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
