package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.ReviewContent;
import com.buct.adminbackend.enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewContentRepository extends JpaRepository<ReviewContent, Long>, JpaSpecificationExecutor<ReviewContent> {

    long countByReviewStatus(ReviewStatus reviewStatus);

    List<ReviewContent> findByReviewTimeBetweenOrderByReviewTimeDesc(LocalDateTime from, LocalDateTime to);
}
