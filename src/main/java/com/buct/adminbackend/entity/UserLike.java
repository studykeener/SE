package com.buct.adminbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_like")
public class UserLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "museum_id", nullable = false)
    private Integer museumId;

    @Column(name = "object_id", nullable = false, length = 255)
    private String objectId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
