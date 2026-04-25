package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.OperationLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    List<OperationLog> findByOperationTypeIn(Iterable<String> types, Sort sort);
}
