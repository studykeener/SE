package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.UserBehaviorRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserBehaviorRecordRepository extends JpaRepository<UserBehaviorRecord, Long> {
    List<UserBehaviorRecord> findByPlatformUserIdOrderByBehaviorTimeDesc(Long platformUserId);
}
