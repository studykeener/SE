package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "`user`")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "user_source", nullable = false, length = 20)
    @JsonProperty("sourceSystem")
    private String userSource = "web";

    @Column(length = 50)
    @JsonProperty("displayName")
    private String nickname;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    private Byte sex;

    @JsonIgnore
    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "register_time", nullable = false)
    @JsonProperty("createdAt")
    private LocalDateTime registerTime = LocalDateTime.now();

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @Convert(converter = UserStatusConverter.class)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ENABLED;

    @Column(name = "disabled_reason", length = 255)
    private String disabledReason;

    @Column(name = "disabled_by")
    private Long disabledBy;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Column(name = "can_comment", nullable = false)
    @JsonProperty("commentAllowed")
    private Boolean canComment = true;

    @Column(name = "can_upload", nullable = false)
    @JsonProperty("uploadAllowed")
    private Boolean canUpload = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
