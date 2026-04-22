package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.UserSource;
import com.buct.adminbackend.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "platform_users")
public class PlatformUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ENABLED;

    @Column(nullable = false)
    private Boolean commentAllowed = true;

    @Column(nullable = false)
    private Boolean uploadAllowed = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
