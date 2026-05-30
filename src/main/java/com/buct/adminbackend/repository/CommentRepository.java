package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.Comment;
import com.buct.adminbackend.enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long>, JpaSpecificationExecutor<Comment> {

    long countByAuditStatus(ReviewStatus auditStatus);

    void deleteByUserId(Long userId);

    List<Comment> findByUpdatedAtBetweenAndAuditStatusInOrderByUpdatedAtDesc(
            LocalDateTime from, LocalDateTime to, List<ReviewStatus> statuses);
}
