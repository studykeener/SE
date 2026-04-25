package com.buct.adminbackend.service;

import com.buct.adminbackend.config.BackupProperties;
import com.buct.adminbackend.dto.CreateBackupRequest;
import com.buct.adminbackend.dto.UpdateBackupTaskConfigRequest;
import com.buct.adminbackend.entity.BackupRecord;
import com.buct.adminbackend.entity.BackupTaskConfig;
import com.buct.adminbackend.repository.BackupRecordRepository;
import com.buct.adminbackend.repository.BackupTaskConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BackupService {
    private static final Set<String> INTERNAL_EXCLUDED_TABLES = Set.of("backup_records", "backup_task_config");

    private final BackupProperties backupProperties;
    private final BackupRecordRepository backupRecordRepository;
    private final BackupTaskConfigRepository backupTaskConfigRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SystemLogService systemLogService;

    public BackupTaskConfig getOrCreateConfig() {
        return backupTaskConfigRepository.findById(1L).orElseGet(() -> {
            BackupTaskConfig c = new BackupTaskConfig();
            c.setId(1L);
            return backupTaskConfigRepository.save(c);
        });
    }

    public BackupTaskConfig updateConfig(UpdateBackupTaskConfigRequest req) {
        BackupTaskConfig c = getOrCreateConfig();
        if (req.autoEnabled() != null) c.setAutoEnabled(req.autoEnabled());
        if (StringUtils.hasText(req.cronExpression())) {
            // validate cron format
            CronExpression.parse(req.cronExpression().trim());
            c.setCronExpression(req.cronExpression().trim());
        }
        if (req.retentionDays() != null) {
            if (req.retentionDays() < 1 || req.retentionDays() > 3650) {
                throw new IllegalArgumentException("retentionDays 需在 1~3650 之间");
            }
            c.setRetentionDays(req.retentionDays());
        }
        return backupTaskConfigRepository.save(c);
    }

    public List<BackupRecord> listRecords() {
        List<BackupRecord> all = backupRecordRepository.findAll();
        all.sort(Comparator.comparing(BackupRecord::getBackupTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return all;
    }

    public List<String> listAvailableTables() {
        return listSchemaTables();
    }

    public BackupRecord createBackup(CreateBackupRequest req, String operator) {
        String type = normalize(req.backupType());
        List<String> tables = resolveTables(type, req.tables());
        if (tables.isEmpty()) throw new IllegalArgumentException("无可备份数据表");

        Path dir = ensureBackupDir();
        String fileName = String.format("backup-%s-%s-%s.bin",
                type.toLowerCase(Locale.ROOT),
                LocalDateTime.now().toString().replace(":", "").replace(".", ""),
                UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        Path file = dir.resolve(fileName);

        BackupRecord rec = new BackupRecord();
        rec.setOperator(StringUtils.hasText(operator) ? operator : "system");
        rec.setBackupType(type);
        rec.setTableScope(String.join(",", tables));
        rec.setFileName(fileName);
        rec.setFilePath(file.toString());
        rec.setFileSize(0L);
        rec.setStatus("FAILED");
        rec.setEncrypted(true);
        rec.setBackupTime(LocalDateTime.now());
        rec = backupRecordRepository.save(rec);

        try {
            Map<String, Object> payload = buildPayload(tables);
            byte[] plain = objectMapper.writeValueAsBytes(payload);
            byte[] encrypted = encrypt(plain);
            Files.write(file, encrypted);

            rec.setFileName(fileName);
            rec.setFilePath(file.toString());
            rec.setFileSize((long) encrypted.length);
            rec.setStatus("SUCCESS");
            rec.setNote("备份完成");
            systemLogService.info("MANUAL_BACKUP", "BackupService",
                    "backupId=" + rec.getId() + ", type=" + type + ", tables=" + tables.size());
            return backupRecordRepository.save(rec);
        } catch (Exception e) {
            rec.setNote("备份失败: " + e.getMessage());
            backupRecordRepository.save(rec);
            systemLogService.error("MANUAL_BACKUP_FAIL", "BackupService", "type=" + type + ", error=" + e.getMessage(), e);
            throw new IllegalStateException("备份失败: " + e.getMessage(), e);
        }
    }

    public BackupRecord getRecord(Long id) {
        return backupRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("备份记录不存在"));
    }

    public byte[] readBackupFileRaw(Long id) {
        BackupRecord r = getRecord(id);
        try {
            return Files.readAllBytes(Paths.get(r.getFilePath()));
        } catch (Exception e) {
            throw new IllegalStateException("读取备份文件失败: " + e.getMessage(), e);
        }
    }

    public void restore(Long id, String confirmText, String operator) {
        if (!"CONFIRM_RESTORE".equals(confirmText)) {
            throw new IllegalArgumentException("恢复确认文本错误，请输入 CONFIRM_RESTORE");
        }
        BackupRecord r = getRecord(id);
        try {
            byte[] raw = Files.readAllBytes(Paths.get(r.getFilePath()));
            byte[] plain = decrypt(raw);
            Map<String, Object> payload = objectMapper.readValue(plain, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, Object>>> data = (Map<String, List<Map<String, Object>>>) payload.get("data");
            if (data == null || data.isEmpty()) throw new IllegalArgumentException("备份内容为空");

            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                // truncate first
                for (String table : data.keySet()) {
                    jdbcTemplate.execute("TRUNCATE TABLE `" + table + "`");
                }
                // re-insert
                for (Map.Entry<String, List<Map<String, Object>>> e : data.entrySet()) {
                    String table = e.getKey();
                    for (Map<String, Object> row : e.getValue()) {
                        insertRow(table, row);
                    }
                }
            } finally {
                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        } catch (Exception e) {
            throw new IllegalStateException("恢复失败: " + e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public void autoBackupSchedulerTick() {
        BackupTaskConfig c = getOrCreateConfig();
        if (!Boolean.TRUE.equals(c.getAutoEnabled())) return;
        CronExpression expr = CronExpression.parse(c.getCronExpression());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = c.getLastAutoRun() == null ? now.minusMinutes(2) : c.getLastAutoRun();
        LocalDateTime next = expr.next(base);
        if (next == null || next.isAfter(now)) return;
        try {
            createBackup(new CreateBackupRequest("FULL", null), "system-auto");
            c.setLastAutoRun(now);
            backupTaskConfigRepository.save(c);
            systemLogService.info("AUTO_BACKUP", "BackupService", "auto backup executed at " + now);
        } catch (Exception e) {
            systemLogService.error("AUTO_BACKUP_FAIL", "BackupService", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 10 3 * * *")
    public void cleanupExpiredBackups() {
        BackupTaskConfig c = getOrCreateConfig();
        int days = c.getRetentionDays() == null ? 30 : c.getRetentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        List<BackupRecord> expired = backupRecordRepository.findByBackupTimeBefore(cutoff);
        for (BackupRecord r : expired) {
            try {
                if (StringUtils.hasText(r.getFilePath())) {
                    Files.deleteIfExists(Paths.get(r.getFilePath()));
                }
            } catch (Exception ignored) {
            }
            backupRecordRepository.deleteById(r.getId());
        }
        if (!expired.isEmpty()) {
            systemLogService.info("BACKUP_CLEANUP", "BackupService", "cleaned expired backups: " + expired.size());
        }
    }

    private Map<String, Object> buildPayload(List<String> tables) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("backupAt", LocalDateTime.now().toString());
        payload.put("schema", currentSchemaName());
        payload.put("tables", tables);
        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
        for (String t : tables) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM `" + t + "`");
            data.put(t, rows);
        }
        payload.put("data", data);
        return payload;
    }

    private List<String> resolveTables(String backupType, List<String> requestTables) {
        List<String> all = listSchemaTables();
        if ("FULL".equals(backupType)) return all;
        if (!"TABLES".equals(backupType)) {
            throw new IllegalArgumentException("backupType 仅支持 FULL 或 TABLES");
        }
        if (requestTables == null || requestTables.isEmpty()) {
            throw new IllegalArgumentException("TABLES 模式下 tables 不能为空");
        }
        Set<String> allow = all.stream().map(String::toLowerCase).collect(Collectors.toSet());
        List<String> result = new ArrayList<>();
        for (String t : requestTables) {
            String n = normalize(t);
            if (!allow.contains(n.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("非法或不存在的数据表: " + n);
            }
            if (!result.contains(n)) result.add(n);
        }
        return result;
    }

    private List<String> listSchemaTables() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name";
        List<String> tables = jdbcTemplate.queryForList(sql, String.class, currentSchemaName());
        return tables.stream().filter(t -> !INTERNAL_EXCLUDED_TABLES.contains(t)).toList();
    }

    private String currentSchemaName() {
        String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!StringUtils.hasText(schema)) throw new IllegalStateException("当前数据库名为空");
        return schema;
    }

    private void insertRow(String table, Map<String, Object> row) {
        if (row == null || row.isEmpty()) return;
        List<String> cols = new ArrayList<>(row.keySet());
        String colSql = cols.stream().map(c -> "`" + c + "`").collect(Collectors.joining(","));
        String valSql = cols.stream().map(c -> "?").collect(Collectors.joining(","));
        String sql = "INSERT INTO `" + table + "` (" + colSql + ") VALUES (" + valSql + ")";
        Object[] params = cols.stream().map(row::get).toArray();
        jdbcTemplate.update(sql, params);
    }

    private Path ensureBackupDir() {
        try {
            Path dir = Paths.get(backupProperties.getDirectory());
            Files.createDirectories(dir);
            return dir;
        } catch (Exception e) {
            throw new IllegalStateException("创建备份目录失败: " + e.getMessage(), e);
        }
    }

    private byte[] encrypt(byte[] plain) {
        try {
            byte[] key = Base64.getDecoder().decode(backupProperties.getAesKeyBase64());
            if (!(key.length == 16 || key.length == 24 || key.length == 32)) {
                throw new IllegalArgumentException("backup.aes-key-base64 解码后长度必须为 16/24/32");
            }
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plain);
            byte[] out = new byte[16 + encrypted.length];
            System.arraycopy(iv, 0, out, 0, 16);
            System.arraycopy(encrypted, 0, out, 16, encrypted.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("备份加密失败: " + e.getMessage(), e);
        }
    }

    private byte[] decrypt(byte[] data) {
        try {
            if (data == null || data.length < 17) throw new IllegalArgumentException("备份文件格式不正确");
            byte[] iv = Arrays.copyOfRange(data, 0, 16);
            byte[] body = Arrays.copyOfRange(data, 16, data.length);
            byte[] key = Base64.getDecoder().decode(backupProperties.getAesKeyBase64());
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(body);
        } catch (Exception e) {
            throw new IllegalStateException("备份解密失败: " + e.getMessage(), e);
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }
}

