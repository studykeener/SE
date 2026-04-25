package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.UnifiedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UnifiedUserRepository extends JpaRepository<UnifiedUser, Long>, JpaSpecificationExecutor<UnifiedUser> {

    boolean existsBySourceSystemAndSourceUserId(String sourceSystem, String sourceUserId);

    boolean existsBySourceSystemAndSourceUserIdAndIdNot(String sourceSystem, String sourceUserId, Long id);
}

