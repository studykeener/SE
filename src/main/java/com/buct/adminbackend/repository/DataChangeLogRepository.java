package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.DataChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataChangeLogRepository extends JpaRepository<DataChangeLog, Long> {
}
