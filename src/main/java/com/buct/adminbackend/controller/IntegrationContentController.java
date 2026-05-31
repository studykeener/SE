package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.ReportPlatformLoginRequest;
import com.buct.adminbackend.dto.SubmitCommentRequest;
import com.buct.adminbackend.dto.SubmitContentResponse;
import com.buct.adminbackend.dto.SubmitPhotoRequest;
import com.buct.adminbackend.enums.ReviewStatus;
import com.buct.adminbackend.service.AuditLogService;
import com.buct.adminbackend.service.ReviewQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 供队友业务系统调用的内容提交接口（评论 / 上传照片 / 登录上报）。
 * <p>
 * 功能：
 * 1. POST /api/integration/comments — 提交评论，触发敏感词检测 + 自动审核策略
 * 2. POST /api/integration/photos — 提交照片，触发NSFW模型审核 + 进入人工队列
 * 3. POST /api/integration/logins — 登录上报，供看板统计
 * <p>
 * 注意：此接口无需登录验证，由队友子系统直接调用。
 * 走与后台管理相同的敏感词与自动审核策略，写入共用表 comment / user_upload_photo。
 */
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationContentController {

    private final ReviewQueueService reviewQueueService;
    private final AuditLogService auditLogService;

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", "up");
        data.put("service", "admin-backend-content-review");
        return ApiResponse.ok(data);
    }

    /** 提交评论：触发敏感词检测和自动审核策略，返回审核结果 */
    @PostMapping("/comments")
    public ApiResponse<SubmitContentResponse> submitComment(@Valid @RequestBody SubmitCommentRequest request) {
        try {
            var item = reviewQueueService.submitComment(
                    request.userId(),
                    request.museumId(),
                    request.objectId(),
                    request.content(),
                    request.source());
            return ApiResponse.ok(toResponse(item));
        } catch (IllegalArgumentException ex) {
            // 高风险评论被自动拒绝时，返回统一格式的拒绝响应
            if (ex.getMessage() != null && ex.getMessage().contains("内容违规")) {
                SubmitContentResponse rejected = new SubmitContentResponse(
                        null, "comment", ReviewStatus.REJECTED, 100, null,
                        false, false, ex.getMessage());
                return ApiResponse.ok(rejected);
            }
            throw ex;
        }
    }

    /** 提交照片：触发NSFW模型审核，全部进入人工审核队列 */
    @PostMapping("/photos")
    public ApiResponse<SubmitContentResponse> submitPhoto(@Valid @RequestBody SubmitPhotoRequest request) {
        var item = reviewQueueService.submitPhoto(
                request.userId(),
                request.photoUrl(),
                request.description(),
                request.museumId(),
                request.objectId(),
                request.source());
        return ApiResponse.ok(toResponse(item));
    }

    /** Web/App 用户登录成功后上报，用于看板「日登录人数」统计 */
    @PostMapping("/logins")
    public ApiResponse<Map<String, Object>> reportLogin(@Valid @RequestBody ReportPlatformLoginRequest request) {
        auditLogService.reportPlatformLogin(request.userId(), request.source(), request.ipAddress());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", request.userId());
        data.put("source", AuditLogService.normalizePlatformSource(request.source()));
        return ApiResponse.ok("登录已记录", data);
    }

    private static SubmitContentResponse toResponse(com.buct.adminbackend.dto.ReviewQueueItemResponse item) {
        ReviewStatus status = item.reviewStatus();
        String message;
        if (status == ReviewStatus.APPROVED) {
            message = "审核通过，可以展示";
        } else if (status == ReviewStatus.PENDING || status == ReviewStatus.RECHECK) {
            message = "已提交，等待人工审核";
        } else {
            message = "已拒绝";
        }
        return new SubmitContentResponse(
                item.id(),
                item.sourceTable(),
                status,
                item.riskScore(),
                null,
                status == ReviewStatus.APPROVED,
                status == ReviewStatus.PENDING || status == ReviewStatus.RECHECK,
                message
        );
    }
}
