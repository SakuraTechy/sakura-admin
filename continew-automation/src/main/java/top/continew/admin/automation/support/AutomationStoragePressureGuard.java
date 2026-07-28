/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.automation.support;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import top.continew.starter.core.exception.BusinessException;

/**
 * UI 自动化执行前的存储水位熔断器。
 *
 * <p>MySQL 与 admin 分容器部署时，必须把 MySQL 数据盘以只读方式挂载到 admin 并加入 monitored-paths，
 * 或配置数据库逻辑容量上限；仅检查 admin 临时目录不能代表 MySQL 数据盘剩余空间。</p>
 */
@Component
@RequiredArgsConstructor
public class AutomationStoragePressureGuard {

    private final JdbcTemplate jdbcTemplate;

    @Value("${automation.storage-pressure.enabled:true}")
    private boolean enabled;

    @Value("${automation.storage-pressure.monitored-paths:}")
    private String monitoredPaths;

    @Value("${automation.storage-pressure.min-free-bytes:5368709120}")
    private long minFreeBytes;

    @Value("${automation.storage-pressure.max-used-percent:90}")
    private int maxUsedPercent;

    @Value("${automation.storage-pressure.max-database-bytes:0}")
    private long maxDatabaseBytes;

    @Value("${automation.storage-pressure.cache-seconds:30}")
    private long cacheSeconds;

    private volatile long lastCheckMillis;
    private volatile String lastFailure;

    public void assertExecutionAllowed() {
        if (!enabled) {
            return;
        }
        refreshIfNeeded();
        if (StringUtils.isNotBlank(lastFailure)) {
            throw new BusinessException("存储空间不足，已暂停新的 UI 自动化执行：" + lastFailure);
        }
    }

    private synchronized void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMillis < Math.max(1, cacheSeconds) * 1000) {
            return;
        }
        lastFailure = inspectStorage();
        lastCheckMillis = now;
    }

    private String inspectStorage() {
        for (Path path : resolvePaths()) {
            try {
                if (!Files.exists(path)) {
                    return "监控路径不存在：" + path;
                }
                FileStore store = Files.getFileStore(path);
                long total = store.getTotalSpace();
                long usable = store.getUsableSpace();
                long usedPercent = total <= 0 ? 0 : Math.round((total - usable) * 100.0 / total);
                if (usable < Math.max(0, minFreeBytes) || usedPercent >= Math.max(1, maxUsedPercent)) {
                    return "路径 " + path + " 可用 " + usable + " 字节，使用率 " + usedPercent + "%";
                }
            } catch (Exception e) {
                return "无法读取监控路径 " + path + "：" + e.getMessage();
            }
        }
        if (maxDatabaseBytes > 0) {
            Long databaseBytes = jdbcTemplate
                .queryForObject("SELECT COALESCE(SUM(data_length + index_length), 0)" + " FROM information_schema.tables WHERE table_schema = DATABASE()", Long.class);
            if (databaseBytes != null && databaseBytes >= maxDatabaseBytes) {
                return "当前数据库逻辑容量 " + databaseBytes + " 字节，已达到上限 " + maxDatabaseBytes + " 字节";
            }
        }
        return null;
    }

    private List<Path> resolvePaths() {
        List<Path> result = new ArrayList<>();
        if (StringUtils.isNotBlank(monitoredPaths)) {
            for (String value : monitoredPaths.split("[,;]")) {
                if (StringUtils.isNotBlank(value)) {
                    result.add(Path.of(value.trim()).toAbsolutePath().normalize());
                }
            }
        }
        if (result.isEmpty()) {
            result.add(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize());
        }
        return result;
    }
}
