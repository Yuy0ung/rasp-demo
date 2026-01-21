package com.demo.backend.controller;

import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rasp")
public class AlertController {

    // 内存中存储告警，实际应存数据库
    private final List<Map<String, String>> alerts = Collections.synchronizedList(new ArrayList<>());

    /**
     * 接收 Agent 上报的告警
     */
    @PostMapping("/report")
    public String report(@RequestBody Map<String, String> alert) {
        System.out.println("[Backend] Received Alert: " + alert);
        // 插入到头部，最新的在前面
        alerts.add(0, alert);
        // 保持只存最近 50 条
        if (alerts.size() > 50) {
            alerts.remove(alerts.size() - 1);
        }
        return "ok";
    }

    /**
     * 前端轮询获取告警列表
     */
    @GetMapping("/alerts")
    public List<Map<String, String>> getAlerts() {
        return alerts;
    }
}
