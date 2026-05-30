package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.RestoreLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestoreLogRepository extends JpaRepository<RestoreLog, Long> {

    List<RestoreLog> findAllByOrderByStartedAtDesc();
}
