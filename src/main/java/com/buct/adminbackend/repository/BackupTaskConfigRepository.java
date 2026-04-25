package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.BackupTaskConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupTaskConfigRepository extends JpaRepository<BackupTaskConfig, Long> {
}

