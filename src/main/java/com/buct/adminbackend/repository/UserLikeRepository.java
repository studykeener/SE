package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.UserLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserLikeRepository extends JpaRepository<UserLike, Long>, JpaSpecificationExecutor<UserLike> {

    void deleteByUserId(Long userId);
}
