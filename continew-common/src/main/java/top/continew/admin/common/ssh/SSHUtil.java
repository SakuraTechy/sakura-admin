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

package top.continew.admin.common.ssh;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * SSH工具类
 *
 * @author hagyao520
 * @since 2025-05-10 16:02:10
 */
@Slf4j
public class SSHUtil {
    //	private static final Logger log = LoggerFactory.getLogger(SSHUtil.class);
    private Vector<String> stdout;
    // 会话session
    Session session;

    // 输入IP、端口、用户名和密码，连接远程服务器
    public SSHUtil(final String host, int port, final String username, final String password) {
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(100000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 测试服务器连接是否成功
     *
     * @param host     服务器主机地址
     * @param port     服务器端口号
     * @param username 用户名
     * @param password 密码
     * @return 连接成功返回 true，否则返回 false
     */
    public static Boolean testConnection(final String host, int port, final String username, final String password) {
        JSch jsch = new JSch();
        Session session = null;

        try {
            // 创建一个新的会话
            session = jsch.getSession(username, host, port);
            session.setPassword(password);

            // 禁用严格主机密钥检查（仅用于测试目的）
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            // 连接到服务器
            session.connect(10000); // 设置超时时间为 10 秒

            log.info("Connected to server at " + host + ":" + port);
            return true;
        } catch (JSchException e) {
            log.error("Failed to connect to server: " + e.getMessage());
            return false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
                log.info("Disconnected from server.");
            }
        }
    }

    public int execute(final String command) {
        int returnCode = 0;
        ChannelShell channel = null;
        PrintWriter printWriter = null;
        BufferedReader input = null;
        stdout = new Vector<String>();
        try {
            channel = (ChannelShell)session.openChannel("shell");
            channel.connect();
            input = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            printWriter = new PrintWriter(channel.getOutputStream());
            printWriter.println(command);
            printWriter.println("exit");
            printWriter.flush();
            //			log.info("The remote command is: ");
            String line;
            while ((line = input.readLine()) != null) {
                stdout.add(line);
                log.info(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            close(printWriter);
            close(input);
            if (channel != null) {
                channel.disconnect();
            }
        }
        return returnCode;
    }

    // 断开连接
    public void close() {
        if (session != null) {
            session.disconnect();
        }
    }

    // 执行命令获取执行结果
    public String executeForResult(String command) {
        execute(command);
        StringBuilder sb = new StringBuilder();
        for (String str : stdout) {
            sb.append(str);
        }
        return sb.toString();
    }

    public static void close(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 运行shell脚本
     * 
     * @param shell 需要运行的shell脚本
     */
    public static void execShell(String shell) {
        try {
            Runtime.getRuntime().exec(shell);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 运行shell脚本 new String[]方式
     * 
     * @param shell 需要运行的shell脚本
     */
    public static void execShellBin(String shell) {
        try {
            Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", shell}, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 运行shell并获得结果，注意：如果sh中含有awk,一定要按new String[]{"/bin/sh","-c",shStr}写,才可以获得流
     * 
     * @param shStr
     *              需要执行的shell
     * @return
     */
    public static List<String> runShell(String shStr) {
        List<String> strList = new ArrayList<String>();
        try {
            Process process = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", shStr}, null, null);
            InputStreamReader ir = new InputStreamReader(process.getInputStream());
            LineNumberReader input = new LineNumberReader(ir);
            String line;
            process.waitFor();
            while ((line = input.readLine()) != null) {
                strList.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return strList;
    }

    public static void main(String[] args) {
        //		SSHUtil sshUtil = new SSHUtil("172.19.5.60", 2233, "root", "@nKk1^2Oe38&8!~!");
        ////		// 执行 ls /opt/命令
        //		String result = sshUtil.executeForResult("df -h");
        //		log.info(result);
        //		sshUtil.close();
        //		runShell("");
        testConnection("172.19.5.60", 2233, "root", "@nKk1^2Oe38&8!~!");
    }
}
