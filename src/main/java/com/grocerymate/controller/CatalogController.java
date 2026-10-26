
package com.grocerymate.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final JdbcTemplate jdbcTemplate;

    public CatalogController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> searchCatalog(
            @RequestParam(defaultValue = "") String q) {

        String term = q == null ? "" : q.trim();

        if (term.length() < 2) {
            return List.of();
        }

        String searchTerm =
                "%" + term.toLowerCase(Locale.ROOT) + "%";

        return jdbcTemplate.queryForList("""
                SELECT DISTINCT
                    c.catalog_item_id AS catalogItemId,
                    c.item_name AS itemName,
                    c.default_unit AS defaultUnit,
                    c.category AS category
                FROM items_catalog c
                LEFT JOIN catalog_item_aliases a
                    ON a.catalog_item_id = c.catalog_item_id
                WHERE  (
                      LOWER(c.item_name) LIKE ?
                      OR a.normalized_alias LIKE ?
                  )
                ORDER BY c.item_name
                LIMIT 10
                """, searchTerm, searchTerm);
    }
}
