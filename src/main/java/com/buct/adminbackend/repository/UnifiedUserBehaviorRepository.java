package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.UnifiedUserBehavior;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UnifiedUserBehaviorRepository extends JpaRepository<UnifiedUserBehavior, Long>, JpaSpecificationExecutor<UnifiedUserBehavior> {

    void deleteByUserId(Long userId);
}

