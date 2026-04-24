package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "artifacts")
public class Artifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String period;

    @Column(length = 100)
    private String type;

    @Column(length = 100)
    private String material;

    @Column(length = 2000)
    private String description;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 50)
    private String sourceSystem;

    @Column(length = 80)
    private String sourceId;

    @Column(nullable = false, length = 20)
    private String kgSyncStatus = "PENDING";

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
