package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.CreateReviewContentRequest;
import com.buct.adminbackend.dto.ReviewActionRequest;
import com.buct.adminbackend.entity.ReviewContent;
import com.buct.adminbackend.enums.ReviewStatus;
import com.buct.adminbackend.repository.ReviewContentRepository;
import com.buct.adminbackend.service.OperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewContentRepository reviewContentRepository;
    private final OperationLogService operationLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<List<ReviewContent>> list(@RequestParam(required = false) ReviewStatus status) {
        if (status == null) {
            return ApiResponse.ok(reviewContentRepository.findAll());
        }
        return ApiResponse.ok(reviewContentRepository.findByReviewStatusOrderBySubmitTimeDesc(status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<ReviewContent> detail(@PathVariable Long id) {
        ReviewContent content = reviewContentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核内容不存在"));
        return ApiResponse.ok(content);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER','DATA_ADMIN')")
    public ApiResponse<ReviewContent> create(@Valid @RequestBody CreateReviewContentRequest request, Authentication authentication) {
        ReviewContent content = new ReviewContent();
        content.setContentType(request.contentType());
        content.setSourceSystem(request.sourceSystem());
        content.setSubmitter(request.submitter());
        content.setContentText(request.contentText());
        content.setContentUrl(request.contentUrl());
        content.setRiskScore(request.riskScore() == null ? 0 : request.riskScore());
        content.setReviewStatus(ReviewStatus.PENDING);
        ReviewContent saved = reviewContentRepository.save(content);
        operationLogService.log(authentication.getName(), "CREATE_REVIEW_CONTENT", String.valueOf(saved.getId()), "新增待审核内容");
        return ApiResponse.ok("新增成功", saved);
    }

    @PatchMapping("/{id}/action")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<ReviewContent> review(@PathVariable Long id,
                                             @Valid @RequestBody ReviewActionRequest request,
                                             Authentication authentication) {
        if (request.reviewStatus() == ReviewStatus.PENDING) {
            throw new IllegalArgumentException("审核动作不能设置为 PENDING");
        }
        if (request.reviewStatus() == ReviewStatus.REJECTED
                && (request.rejectReason() == null || request.rejectReason().isBlank())) {
            throw new IllegalArgumentException("拒绝时必须填写拒绝原因");
        }
        ReviewContent content = reviewContentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核内容不存在"));
        content.setReviewStatus(request.reviewStatus());
        content.setReviewTime(LocalDateTime.now());
        content.setReviewer(authentication.getName());
        content.setRejectReason(request.reviewStatus() == ReviewStatus.REJECTED ? request.rejectReason() : null);
        ReviewContent saved = reviewContentRepository.save(content);
        operationLogService.log(authentication.getName(), "REVIEW_CONTENT", String.valueOf(saved.getId()),
                "审核结果: " + request.reviewStatus());
        return ApiResponse.ok("审核成功", saved);
    }

    @PatchMapping("/batch/action")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<Void> batchReview(@RequestParam List<Long> ids,
                                         @Valid @RequestBody ReviewActionRequest request,
                                         Authentication authentication) {
        if (request.reviewStatus() == ReviewStatus.PENDING) {
            throw new IllegalArgumentException("审核动作不能设置为 PENDING");
        }
        for (Long id : ids) {
            reviewContentRepository.findById(id).ifPresent(content -> {
                content.setReviewStatus(request.reviewStatus());
                content.setReviewTime(LocalDateTime.now());
                content.setReviewer(authentication.getName());
                content.setRejectReason(request.reviewStatus() == ReviewStatus.REJECTED ? request.rejectReason() : null);
                reviewContentRepository.save(content);
            });
        }
        operationLogService.log(authentication.getName(), "BATCH_REVIEW_CONTENT", ids.toString(), "审核结果: " + request.reviewStatus());
        return ApiResponse.ok("批量审核成功", null);
    }
}
