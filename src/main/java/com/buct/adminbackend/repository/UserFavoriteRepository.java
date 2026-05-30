package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long>, JpaSpecificationExecutor<UserFavorite> {

    void deleteByUserId(Long userId);
}
