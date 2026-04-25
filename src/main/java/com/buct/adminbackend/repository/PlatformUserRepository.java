package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long>, JpaSpecificationExecutor<PlatformUser> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);
}
