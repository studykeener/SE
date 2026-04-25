package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.CreateBackupRequest;
import com.buct.adminbackend.dto.RestoreBackupRequest;
import com.buct.adminbackend.dto.UpdateBackupTaskConfigRequest;
import com.buct.adminbackend.entity.BackupRecord;
import com.buct.adminbackend.entity.BackupTaskConfig;
import com.buct.adminbackend.service.BackupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/backup")
@RequiredArgsConstructor
public class BackupController {
    private final BackupService backupService;

    @GetMapping("/records")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<BackupRecord>> listRecords() {
        return ApiResponse.ok(backupService.listRecords());
    }

    @PostMapping("/manual")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<BackupRecord> manualBackup(@Valid @RequestBody CreateBackupRequest request, Authentication authentication) {
        return ApiResponse.ok("备份成功", backupService.createBackup(request, authentication.getName()));
    }

    @GetMapping("/tables")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<String>> listTables() {
        return ApiResponse.ok(backupService.listAvailableTables());
    }

    @GetMapping("/records/{id}/download")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        BackupRecord r = backupService.getRecord(id);
        byte[] bytes = backupService.readBackupFileRaw(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + r.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @PostMapping("/restore/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> restore(@PathVariable Long id,
                                     @Valid @RequestBody RestoreBackupRequest request,
                                     Authentication authentication) {
        backupService.restore(id, request.confirmText(), authentication.getName());
        return ApiResponse.ok("恢复成功", null);
    }

    @GetMapping("/config")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<BackupTaskConfig> getConfig() {
        return ApiResponse.ok(backupService.getOrCreateConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<BackupTaskConfig> updateConfig(@RequestBody UpdateBackupTaskConfigRequest request) {
        return ApiResponse.ok("更新成功", backupService.updateConfig(request));
    }
}

