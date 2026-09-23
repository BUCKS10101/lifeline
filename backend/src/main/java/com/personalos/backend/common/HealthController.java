package com.personalos.backend.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "personal-os-backend",
                "database", databaseStatus(),
                "timestamp", Instant.now().toString()
        );
    }

    private String databaseStatus() {
        try {
            jdbc.queryForObject("select 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
