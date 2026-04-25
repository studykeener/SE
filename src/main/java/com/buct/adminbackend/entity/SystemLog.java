package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "system_logs")
public class SystemLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String level; // INFO/WARN/ERROR

    @Column(nullable = false, length = 64)
    private String eventType; // EXCEPTION/AUTO_BACKUP/...

    @Column(nullable = false, length = 128)
    private String source;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(length = 4000)
    private String stackTrace;

    @Column(nullable = false)
    private LocalDateTime logTime = LocalDateTime.now();
}

