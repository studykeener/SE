package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.RestoreLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestoreLogRepository extends JpaRepository<RestoreLog, Long> {
}
