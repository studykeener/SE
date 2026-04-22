package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {
}
