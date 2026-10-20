package com.grocerymate.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class DatabaseController {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/database/status")
    public Map<String, Object> status() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        Integer tableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
            Integer.class
        );
        return Map.of("connected", true, "database", database, "tableCount", tableCount);
    }
}
