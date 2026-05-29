package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.UserBehavior;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserBehaviorRepository extends JpaRepository<UserBehavior, Long>, JpaSpecificationExecutor<UserBehavior> {

    void deleteByUserId(Long userId);
}
