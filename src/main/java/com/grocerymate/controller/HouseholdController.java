package com.grocerymate.controller;

import com.grocerymate.dto.ItemDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/household")
@CrossOrigin(origins = "*")
public class HouseholdController {

    private final JdbcTemplate jdbcTemplate;

    public HouseholdController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // GET /api/household/items?householdId=...
    @GetMapping("/items")
    public ResponseEntity<?> getItems(@RequestParam(value = "householdId", required = false) Long householdId) {
        try {
            if (householdId == null || householdId <= 0) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            String sql = """
                SELECT
                    item_id,
                    household_id,
                    item_name,
                    quantity_remaining,
                    unit,
                    purchase_date,
                    last_purchase_price,
                    expected_duration_days,
                    estimated_refill_date,
                    reminder_days_before,
                    planning_period,
                    next_stock_check_at,
                    is_active,
                    created_at,
                    updated_at
                FROM household_items
                WHERE is_active = 1
                  AND household_id = ?
                ORDER BY item_id DESC
                """;

            List<Map<String, Object>> items = jdbcTemplate.queryForList(sql, householdId);
            return ResponseEntity.ok(items);

        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load household items.");
        }
    }

    // GET /api/household/spending/monthly?householdId=...
    @GetMapping("/spending/monthly")
    public ResponseEntity<?> getMonthlySpending(@RequestParam(value = "householdId", required = false) Long householdId) {
        try {
            if (householdId == null || householdId <= 0) {
                return ResponseEntity.ok(BigDecimal.ZERO);
            }

            BigDecimal total = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(last_purchase_price), 0)
                FROM household_items
                WHERE is_active = 1
                  AND household_id = ?
                  AND purchase_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01')
                  AND purchase_date < DATE_FORMAT(
                      DATE_ADD(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01'
                  )
                """, BigDecimal.class, householdId);

            return ResponseEntity.ok(total == null ? BigDecimal.ZERO : total);

        } catch (Exception e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Could not calculate monthly spending.");
        }
    }

    // GET /api/household/purchases/upcoming?householdId=...
    @GetMapping("/purchases/upcoming")
    public ResponseEntity<?> getUpcomingPurchases(@RequestParam(value = "householdId", required = false) Long householdId) {
        try {
            if (householdId == null || householdId <= 0) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            String sql = """
                SELECT
                    item_id,
                    item_name,
                    quantity_remaining,
                    unit,
                    estimated_refill_date,
                    reminder_days_before
                FROM household_items
                WHERE is_active = 1
                  AND household_id = ?
                  AND estimated_refill_date IS NOT NULL
                  AND estimated_refill_date >= CURDATE()
                ORDER BY estimated_refill_date ASC
                """;

            return ResponseEntity.ok(jdbcTemplate.queryForList(sql, householdId));

        } catch (Exception e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load upcoming purchases.");
        }
    }

    // POST /api/household/items
    @PostMapping("/items")
    public ResponseEntity<?> addItem(@RequestBody ItemDto itemDto) {
        try {
            if (itemDto.getItemName() == null || itemDto.getItemName().isBlank()) {
                return errorResponse(HttpStatus.BAD_REQUEST, "Item name is required.");
            }

            if (itemDto.getQuantityRemaining() == null || itemDto.getQuantityRemaining().compareTo(BigDecimal.ZERO) < 0) {
                return errorResponse(HttpStatus.BAD_REQUEST, "Remaining quantity must be zero or greater.");
            }

            if (itemDto.getUnit() == null || itemDto.getUnit().isBlank()) {
                return errorResponse(HttpStatus.BAD_REQUEST, "Unit is required.");
            }

            if (itemDto.getLastPurchasePrice() != null && itemDto.getLastPurchasePrice().compareTo(BigDecimal.ZERO) < 0) {
                return errorResponse(HttpStatus.BAD_REQUEST, "Purchase price cannot be negative.");
            }

            int duration = itemDto.getExpectedDurationDays() == null ? 30 : itemDto.getExpectedDurationDays();
            int reminder = itemDto.getReminderDaysBefore() == null ? 3 : itemDto.getReminderDaysBefore();

            if (duration < 1 || reminder < 0) {
                return errorResponse(HttpStatus.BAD_REQUEST, "Duration must be at least 1 day and reminder cannot be negative.");
            }

            // Resolve target household
            Long householdId = itemDto.getHouseholdId();
            if (householdId == null || householdId <= 0) {
                List<Long> fallbackIds = jdbcTemplate.queryForList("SELECT household_id FROM households LIMIT 1", Long.class);
                if (fallbackIds.isEmpty()) {
                    return errorResponse(HttpStatus.BAD_REQUEST, "No household found in database.");
                }
                householdId = fallbackIds.get(0);
            }

            String sql = """
                INSERT INTO household_items (
                    household_id,
                    item_name,
                    quantity_remaining,
                    unit,
                    purchase_date,
                    last_purchase_price,
                    expected_duration_days,
                    estimated_refill_date,
                    reminder_days_before,
                    next_stock_check_at,
                    planning_period,
                    is_active,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    'MONTHLY', 1, NOW(), NOW()
                )
                """;

            jdbcTemplate.update(
                    sql,
                    householdId,
                    itemDto.getItemName().trim(),
                    itemDto.getQuantityRemaining(),
                    itemDto.getUnit().trim(),
                    itemDto.getPurchaseDate(),
                    itemDto.getLastPurchasePrice(),
                    duration,
                    itemDto.getEstimatedRefillDate(),
                    reminder,
                    itemDto.getNextStockCheckAt()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of("message", "Household item saved successfully.")
            );

        } catch (Exception e) {
            e.printStackTrace();
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Database insert failed: " + e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}

//
//package com.grocerymate.controller;
//
//import com.grocerymate.dto.ItemDto;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.web.bind.annotation.*;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/household")
//@CrossOrigin(origins = "*")
//public class HouseholdController {
//
//    private final JdbcTemplate jdbcTemplate;
//
//    public HouseholdController(JdbcTemplate jdbcTemplate) {
//        this.jdbcTemplate = jdbcTemplate;
//    }
//
//    // GET /api/household/items
//    @GetMapping("/items")
//    public ResponseEntity<?> getItems() {
//        try {
//            String sql = """
//                SELECT
//                    item_id,
//                    household_id,
//                    item_name,
//                    quantity_remaining,
//                    unit,
//                    purchase_date,
//                    last_purchase_price,
//                    expected_duration_days,
//                    estimated_refill_date,
//                    reminder_days_before,
//                    planning_period,
//                    next_stock_check_at,
//                    is_active,
//                    created_at,
//                    updated_at
//                FROM household_items
//                WHERE is_active = 1
//                ORDER BY item_id DESC
//                """;
//
//            List<Map<String, Object>> items =
//                    jdbcTemplate.queryForList(sql);
//
//            return ResponseEntity.ok(items);
//
//        } catch (Exception e) {
//            return errorResponse(
//                    HttpStatus.INTERNAL_SERVER_ERROR,
//                    "Could not load household items."
//            );
//        }
//    }
//
//    // GET /api/household/spending/monthly
//    @GetMapping("/spending/monthly")
//    public ResponseEntity<?> getMonthlySpending() {
//        try {
//            BigDecimal total = jdbcTemplate.queryForObject("""
//                SELECT COALESCE(SUM(last_purchase_price), 0)
//                FROM household_items
//                WHERE is_active = 1
//                  AND purchase_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01')
//                  AND purchase_date < DATE_FORMAT(
//                      DATE_ADD(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01'
//                  )
//                """, BigDecimal.class);
//
//            return ResponseEntity.ok(
//                    total == null ? BigDecimal.ZERO : total
//            );
//
//        } catch (Exception e) {
//            return errorResponse(
//                    HttpStatus.INTERNAL_SERVER_ERROR,
//                    "Could not calculate monthly spending."
//            );
//        }
//    }
//
//    // GET /api/household/purchases/upcoming
//    @GetMapping("/purchases/upcoming")
//    public ResponseEntity<?> getUpcomingPurchases() {
//        try {
//            String sql = """
//                SELECT
//                    item_id,
//                    item_name,
//                    quantity_remaining,
//                    unit,
//                    estimated_refill_date,
//                    reminder_days_before
//                FROM household_items
//                WHERE is_active = 1
//                  AND estimated_refill_date IS NOT NULL
//                  AND estimated_refill_date >= CURDATE()
//                ORDER BY estimated_refill_date ASC
//                """;
//
//            return ResponseEntity.ok(
//                    jdbcTemplate.queryForList(sql)
//            );
//
//        } catch (Exception e) {
//            return errorResponse(
//                    HttpStatus.INTERNAL_SERVER_ERROR,
//                    "Could not load upcoming purchases."
//            );
//        }
//    }
//
//    // POST /api/household/items
//    @PostMapping("/items")
//    public ResponseEntity<?> addItem(
//            @RequestBody ItemDto itemDto) {
//
//        try {
//            // Basic input validation
//            if (itemDto.getItemName() == null
//                    || itemDto.getItemName().isBlank()) {
//                return errorResponse(
//                        HttpStatus.BAD_REQUEST,
//                        "Item name is required."
//                );
//            }
//
//            if (itemDto.getQuantityRemaining() == null
//                    || itemDto.getQuantityRemaining()
//                    .compareTo(BigDecimal.ZERO) < 0) {
//                return errorResponse(
//                        HttpStatus.BAD_REQUEST,
//                        "Remaining quantity must be zero or greater."
//                );
//            }
//
//            if (itemDto.getUnit() == null
//                    || itemDto.getUnit().isBlank()) {
//                return errorResponse(
//                        HttpStatus.BAD_REQUEST,
//                        "Unit is required."
//                );
//            }
//
//            if (itemDto.getLastPurchasePrice() != null
//                    && itemDto.getLastPurchasePrice()
//                    .compareTo(BigDecimal.ZERO) < 0) {
//                return errorResponse(
//                        HttpStatus.BAD_REQUEST,
//                        "Purchase price cannot be negative."
//                );
//            }
//
//            int duration = itemDto.getExpectedDurationDays() == null
//                    ? 30 : itemDto.getExpectedDurationDays();
//
//            int reminder = itemDto.getReminderDaysBefore() == null
//                    ? 3 : itemDto.getReminderDaysBefore();
//
//            if (duration < 1 || reminder < 0) {
//                return errorResponse(
//                        HttpStatus.BAD_REQUEST,
//                        "Duration must be at least 1 day and reminder cannot be negative."
//                );
//            }
//
//            // TEMPORARY DEMO APPROACH:
//            // This chooses the first household in the database.
//            // Replace this with the authenticated user's household ID
//            // before deploying or supporting multiple users.
//            List<Long> householdIds = jdbcTemplate.queryForList(
//                    "SELECT household_id FROM households LIMIT 1",
//                    Long.class
//            );
//
//            if (householdIds.isEmpty()) {
//                return errorResponse(
//                        HttpStatus.BAD_REQUEST,
//                        "No household exists. Create a household first."
//                );
//            }
//
//            Long householdId = householdIds.get(0);
//
//            String sql = """
//                INSERT INTO household_items (
//                    household_id,
//                    item_name,
//                    quantity_remaining,
//                    unit,
//                    purchase_date,
//                    last_purchase_price,
//                    expected_duration_days,
//                    estimated_refill_date,
//                    reminder_days_before,
//                    next_stock_check_at,
//                    planning_period,
//                    is_active,
//                    created_at,
//                    updated_at
//                )
//                VALUES (
//                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
//                    'MONTHLY', 1, NOW(), NOW()
//                )
//                """;
//
//            jdbcTemplate.update(
//                    sql,
//                    householdId,
//                    itemDto.getItemName().trim(),
//                    itemDto.getQuantityRemaining(),
//                    itemDto.getUnit().trim(),
//                    itemDto.getPurchaseDate(),
//                    itemDto.getLastPurchasePrice(),
//                    duration,
//                    itemDto.getEstimatedRefillDate(),
//                    reminder,
//                    itemDto.getNextStockCheckAt()
//            );
//
//            return ResponseEntity.status(HttpStatus.CREATED).body(
//                    Map.of("message", "Household item saved successfully.")
//            );
//
//        } catch (Exception e) {
//            e.printStackTrace();
//
//            return errorResponse(
//                    HttpStatus.INTERNAL_SERVER_ERROR,
//                    "Database insert failed. Check the Spring Boot console."
//            );
//        }
//    }
//
//    private ResponseEntity<Map<String, String>> errorResponse(
//            HttpStatus status, String message) {
//
//        return ResponseEntity.status(status).body(
//                Map.of("message", message)
//        );
//    }
//}
