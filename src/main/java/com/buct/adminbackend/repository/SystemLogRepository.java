package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
}

