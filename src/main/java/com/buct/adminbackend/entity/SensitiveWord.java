package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.SensitiveWordLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "sensitive_words")
public class SensitiveWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String word;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SensitiveWordLevel level = SensitiveWordLevel.LIGHT;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
