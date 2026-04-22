package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.ReviewContent;
import com.buct.adminbackend.enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewContentRepository extends JpaRepository<ReviewContent, Long> {
    List<ReviewContent> findByReviewStatusOrderBySubmitTimeDesc(ReviewStatus reviewStatus);
}
