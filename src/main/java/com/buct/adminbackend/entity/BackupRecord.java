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

    @Column(nullable = false, length = 128)
    private String fileName;

    @Column(nullable = false, length = 512)
    private String filePath;

    @Column(nullable = false, length = 20)
    private String backupType; // FULL / TABLES

    @Column(length = 500)
    private String tableScope;

    @Column(nullable = false)
    private Long fileSize = 0L;

    @Column(nullable = false)
    private Boolean encrypted = true;

    @Column(nullable = false, length = 20)
    private String status = "SUCCESS"; // SUCCESS / FAILED

    @Column(length = 1000)
    private String note;

    @Column(nullable = false, length = 64)
    private String operator;

    @Column(nullable = false)
    private LocalDateTime backupTime = LocalDateTime.now();
}

