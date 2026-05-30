package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "login_logs")
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_type", nullable = false, length = 10)
    private String userType = "ADMIN";

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    @Column(name = "source_system", nullable = false, length = 20)
    private String sourceSystem = "admin";

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime = LocalDateTime.now();
}
