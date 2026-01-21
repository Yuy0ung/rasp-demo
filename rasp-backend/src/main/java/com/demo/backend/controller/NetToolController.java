package com.demo.backend.controller;

import org.springframework.web.bind.annotation.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/net")
public class NetToolController {

    @PostMapping("/ping")
    public String ping(@RequestBody PingRequest request) {
        return checkConnectivity(request.getIp());
    }

    // 模拟业务逻辑层调用
    private String checkConnectivity(String ip) {
        // 拼接命令
        String command = buildPingCommand(ip);
        // 执行命令
        return executeCommand(command);
    }

    private String buildPingCommand(String ip) {
        // 简单的字符串拼接，存在命令注入漏洞
        return "ping -c 1 " + ip;
    }

    private String executeCommand(String command) {
        try {
            System.out.println("Executing command: " + command);
            // 使用 Shell 执行以触发命令注入漏洞 (MacOS/Linux 使用 /bin/sh, Windows 使用 cmd.exe)
            String[] shellCommand;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                shellCommand = new String[]{"cmd.exe", "/c", command};
            } else {
                shellCommand = new String[]{"/bin/sh", "-c", command};
            }
            
            Process process = Runtime.getRuntime().exec(shellCommand);
            
            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.lines().collect(Collectors.joining("\n"));
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // 读取错误输出
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String error = errorReader.lines().collect(Collectors.joining("\n"));
                return "Error (Exit Code " + exitCode + "):\n" + error;
            }
            
            return output;
        } catch (Exception e) {
            // 如果是 RASP 阻断，异常信息会包含 "RASP Blocked"
            return "Execution Failed: " + e.getMessage();
        }
    }

    public static class PingRequest {
        private String ip;
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
    }
}
