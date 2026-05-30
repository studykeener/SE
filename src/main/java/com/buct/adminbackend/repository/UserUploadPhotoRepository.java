package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.UserUploadPhoto;
import com.buct.adminbackend.enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface UserUploadPhotoRepository extends JpaRepository<UserUploadPhoto, Long>, JpaSpecificationExecutor<UserUploadPhoto> {

    long countByStatus(ReviewStatus status);

    void deleteByUserId(Long userId);

    List<UserUploadPhoto> findByUpdatedAtBetweenAndStatusInOrderByUpdatedAtDesc(
            LocalDateTime from, LocalDateTime to, List<ReviewStatus> statuses);
}
