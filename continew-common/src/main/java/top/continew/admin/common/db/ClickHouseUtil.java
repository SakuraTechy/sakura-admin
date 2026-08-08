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

package top.continew.admin.common.db;

import com.google.gson.Gson;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;

/**
 * ClickHouse 工具类 - 使用 HTTP 协议（端口 8123）
 * 这是最可靠的方案，因为 ClickHouse 默认启用 HTTP 接口
 */
@Slf4j
public class ClickHouseUtil {

    private static Session sshSession;
    private static int localPort;

    /**
     * 通过 SSH 隧道连接 ClickHouse（HTTP 协议）并执行查询
     */
    public static List<Map<String, Object>> executeQueryViaSSH(String sshHost,
                                                               int sshPort,
                                                               String sshUser,
                                                               String sshPassword,
                                                               String clickhouseHost,
                                                               int clickhousePort,
                                                               String database,
                                                               String user,
                                                               String password,
                                                               String sql) {

        Connection con = null;
        Statement sm = null;
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            // 1. 建立 SSH 隧道
            JSch jsch = new JSch();
            sshSession = jsch.getSession(sshUser, sshHost, sshPort);
            sshSession.setPassword(sshPassword);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("Compression", "yes");
            config.put("ServerAliveInterval", "60");
            config.put("ServerAliveCountMax", "10");
            sshSession.setConfig(config);

            log.info("正在建立 SSH 连接到 {}:{}...", sshHost, sshPort);
            sshSession.connect(30000);

            // 端口转发：本地随机端口 -> 远程 ClickHouse HTTP 端口
            localPort = sshSession.setPortForwardingL(0, clickhouseHost, clickhousePort);
            log.info("SSH 隧道建立成功，本地端口: {} -> {}:{}", localPort, clickhouseHost, clickhousePort);

            // 等待隧道完全建立
            Thread.sleep(5000);
            log.info("SSH 隧道已稳定，准备连接 ClickHouse");

            // 2. 连接 ClickHouse（使用旧版驱动 + HTTP 协议）
            String jdbcUrl = String.format("jdbc:clickhouse://localhost:%d/%s", localPort, database);

            log.info("正在连接 ClickHouse: {}", jdbcUrl);

            // 使用旧版驱动
            Class.forName("ru.yandex.clickhouse.ClickHouseDriver");

            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", password);
            props.setProperty("socket_timeout", "60000");
            props.setProperty("connection_timeout", "30000");

            con = DriverManager.getConnection(jdbcUrl, props);
            sm = con.createStatement();
            sm.setQueryTimeout(60);

            log.info("ClickHouse 连接成功，执行查询: {}", sql);

            // 3. 执行查询
            ResultSet rs = sm.executeQuery(sql);
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                results.add(row);
            }

            log.info("查询完成，返回 {} 条记录", results.size());

        } catch (Exception e) {
            log.error("执行失败", e);
            throw new RuntimeException("ClickHouse 查询失败: " + e.getMessage(), e);
        } finally {
            // 4. 清理资源
            try {
                if (sm != null)
                    sm.close();
                if (con != null)
                    con.close();
            } catch (Exception e) {
                log.error("关闭数据库连接失败", e);
            }
        }

        return results;
    }

    /**
     * 关闭 SSH 隧道
     */
    public static void closeSSHTunnel() {
        try {
            if (sshSession != null && sshSession.isConnected()) {
                if (localPort > 0) {
                    sshSession.delPortForwardingL(localPort);
                }
                sshSession.disconnect();
                log.info("SSH 隧道已关闭");
            }
        } catch (Exception e) {
            log.error("关闭 SSH 隧道失败", e);
        }
    }

    /**
     * 测试方法 - 使用 HTTP 端口 8123
     */
    public static void main(String[] args) {
        String sql = "SELECT * FROM bs_audit.audit_record LIMIT 10";

        try {
            List<Map<String, Object>> results = executeQueryViaSSH("172.19.5.45",          // SSH 主机
                22,                     // SSH 端口
                "root",                 // SSH 用户名
                "${SSH_PASSWORD}",      // SSH 密码
                "172.19.5.45",          // ClickHouse 主机
                8123,                   // ClickHouse HTTP 端口（改为 8123）
                "default",              // 数据库名
                "root",                 // ClickHouse 用户
                "${CLICKHOUSE_PASSWORD}", // ClickHouse 密码
                sql                     // SQL 查询
            );

            // 输出结果
            Gson gson = new Gson();
            System.out.println("\n========== 查询结果 ==========");
            for (Map<String, Object> row : results) {
                System.out.println(gson.toJson(row));
            }
            System.out.println("\n总共 " + results.size() + " 条记录");
            System.out.println("==============================\n");

        } catch (Exception e) {
            System.err.println("查询失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeSSHTunnel();
        }
    }
}
