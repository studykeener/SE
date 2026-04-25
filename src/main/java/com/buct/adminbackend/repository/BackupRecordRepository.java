package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.BackupRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {
    List<BackupRecord> findByBackupTimeBefore(LocalDateTime cutoff);
}

