package com.demo.backend.controller;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rasp/config")
public class ConfigController {

    @GetMapping
    public Map<String, Boolean> getConfig() {
        Map<String, Boolean> config = new HashMap<>();
        // Default to true if not set
        String enabled = System.getProperty("rasp.enabled", "true");
        config.put("enabled", "true".equals(enabled));
        return config;
    }

    @PostMapping("/toggle")
    public Map<String, Boolean> toggleConfig(@RequestBody Map<String, Boolean> payload) {
        Boolean enable = payload.get("enabled");
        if (enable != null) {
            System.setProperty("rasp.enabled", enable.toString());
            System.out.println("[Backend] RASP Switch updated to: " + enable);
        }
        return getConfig();
    }
}
