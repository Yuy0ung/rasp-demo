package com.demo.rasp.protection;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class RaspProtector {

    /**
     * 检查 RCE 命令执行
     * 策略：只允许 ping 命令，其他命令视为恶意
     */
    public static void checkRce(String command) throws Exception {
        System.out.println("[RASP LOG] Checking command: " + command);
        
        boolean isEnabled = "true".equals(System.getProperty("rasp.enabled", "true"));
        boolean isMalicious = false;
        
        // 简单策略：如果不以 ping 开头，或者包含常见的命令连接符，则视为恶意
        // 注意：这里为了演示效果，逻辑比较简单
        String trimmedCmd = command.trim();
        
        // 允许直接执行 ping，或者通过 shell 执行 ping (例如 /bin/sh -c ping ...)
        boolean isPingCommand = trimmedCmd.startsWith("ping") || 
                               ((trimmedCmd.startsWith("/bin/sh") || trimmedCmd.startsWith("sh") || trimmedCmd.startsWith("cmd")) && trimmedCmd.contains("ping"));

        if (!isPingCommand) {
            isMalicious = true;
        } else {
            // 即使是 ping，如果包含分号、管道符等，也可能是命令注入
            // 例如: ping 127.0.0.1; whoami
            if (trimmedCmd.contains(";") || trimmedCmd.contains("|") || trimmedCmd.contains("&") || trimmedCmd.contains("`") || trimmedCmd.contains("$")) {
                isMalicious = true;
            }
        }

        if (isMalicious) {
            if (isEnabled) {
                // 开启拦截模式：阻断并报警
                report("RCE", "Blocked malicious command: " + command, "BLOCKED");
                throw new SecurityException("RASP Blocked: Malicious command detected -> " + command);
            } else {
                // 关闭拦截模式：获取污点传播路径（堆栈追踪）
                String stackTrace = getTaintPath();
                report("RCE", "Detected malicious command (Monitor Mode): " + command + "\nPath: " + stackTrace, "MONITORED");
            }
        } else {
            // 非恶意命令：记录审计日志
            report("AUDIT", "Command execution audit: " + command, "PASSED");
        }
    }

    /**
     * 获取当前堆栈信息 (Taint Path)
     */
    private static String getTaintPath() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        sb.append("Taint Path (Call Stack):\n");
        boolean found = false;
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            // 只关注后端业务代码的调用栈
            if (className.startsWith("com.demo.backend")) {
                sb.append(" -> ")
                  .append(className)
                  .append(".")
                  .append(element.getMethodName())
                  .append("(")
                  .append(element.getFileName())
                  .append(":")
                  .append(element.getLineNumber())
                  .append(")\n");
                found = true;
            }
        }
        if (!found) {
            sb.append(" -> (No application code found in stack trace)");
        }
        return sb.toString();
    }



    /**
     * 上报告警给后端 (异步发送，避免阻塞业务)
     */
    public static void report(String type, String detail, String status) {
        new Thread(() -> {
            try {
                // 假设后端运行在本地 8080
                URL url = new URL("http://localhost:8080/api/rasp/report");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                
                // 处理 JSON 转义
                String safeDetail = detail.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
                
                // 构造 JSON
                String jsonInputString = String.format(
                        "{\"type\": \"%s\", \"detail\": \"%s\", \"time\": \"%s\", \"status\": \"%s\"}",
                        type, safeDetail, time, status
                );

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                // System.out.println("Report sent, response code: " + code);
            } catch (Exception e) {
                // System.err.println("Failed to report to backend: " + e.getMessage());
            }
        }).start();
    }
}
