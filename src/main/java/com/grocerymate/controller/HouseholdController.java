package com.grocerymate.controller;
import java.time.Duration;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import com.grocerymate.security.AuthenticatedAccount;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.grocerymate.dto.ItemDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/household")
//@CrossOrigin(origins = "*")
public class HouseholdController {

    private final JdbcTemplate jdbcTemplate;

    public HouseholdController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // GET /api/household/items?householdId=1
    @GetMapping("/items")
    public ResponseEntity<?> getItems(
            @RequestParam(required = false) Long householdId) {
        try {
            if (!validId(householdId)) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            return ResponseEntity.ok(jdbcTemplate.queryForList("""
                SELECT item_id, household_id, item_name,
                       quantity_remaining, unit, purchase_date,
                       last_purchase_price, expected_duration_days,
                       estimated_refill_date, reminder_days_before,
                       planning_period, next_stock_check_at,
                       is_active, created_at, updated_at
                FROM household_items
                WHERE household_id = ? AND is_active = 1
                ORDER BY item_id DESC
                """, householdId));

        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not load household items.");
        }
    }

    // GET /api/household/spending/monthly?householdId=1
    @GetMapping("/spending/monthly")
    public ResponseEntity<?> getMonthlySpending(
            @RequestParam(required = false) Long householdId) {
        try {
            if (!validId(householdId)) {
                return ResponseEntity.ok(BigDecimal.ZERO);
            }

            BigDecimal total = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(ph.total_amount), 0)
                FROM purchase_history ph
                JOIN household_items hi ON hi.item_id = ph.item_id
                WHERE hi.household_id = ?
                  AND ph.purchase_date >= DATE_FORMAT(
                      CURDATE(), '%Y-%m-01')
                  AND ph.purchase_date < DATE_FORMAT(
                      DATE_ADD(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
                """, BigDecimal.class, householdId);

            return ResponseEntity.ok(
                    total == null ? BigDecimal.ZERO : total);

        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not calculate monthly spending.");
        }
    }

    // GET /api/household/purchases/upcoming?householdId=1
    @GetMapping("/purchases/upcoming")
    public ResponseEntity<?> getUpcomingPurchases(
            @RequestParam(required = false) Long householdId) {
        try {
            if (!validId(householdId)) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            return ResponseEntity.ok(jdbcTemplate.queryForList("""
                SELECT item_id, item_name, quantity_remaining,
                       unit, estimated_refill_date,
                       reminder_days_before
                FROM household_items
                WHERE household_id = ?
                  AND is_active = 1
                  AND estimated_refill_date IS NOT NULL
                  AND estimated_refill_date >= CURDATE()
                ORDER BY estimated_refill_date ASC
                """, householdId));

        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not load upcoming purchases.");
        }
    }

    // POST /api/household/items
    // Creates inventory and optionally records its initial purchase.

    @PostMapping("/items")
    @Transactional
    public ResponseEntity<?> addItem(
            @RequestBody ItemDto dto,
            Authentication authentication) {

        try {
            // 1. Validate the authenticated account.
            if (authentication == null
                    || !(authentication.getPrincipal()
                    instanceof AuthenticatedAccount account)) {
                return error(HttpStatus.UNAUTHORIZED,
                        "Please log in again.");
            }

            if (!"USER".equals(account.role())
                    || account.householdId() == null) {
                return error(HttpStatus.FORBIDDEN,
                        "You are not authorized to manage household inventory.");
            }

            if (!validId(dto.getHouseholdId())
                    || !account.householdId().equals(dto.getHouseholdId())) {
                return error(HttpStatus.FORBIDDEN,
                        "You cannot modify another household's inventory.");
            }

            // 2. Validate the product details.
            if (dto.getItemName() == null
                    || dto.getItemName().isBlank()) {
                return error(HttpStatus.BAD_REQUEST,
                        "Item name is required.");
            }

            if (dto.getUnit() == null || dto.getUnit().isBlank()) {
                return error(HttpStatus.BAD_REQUEST,
                        "Unit is required.");
            }

            String itemName = dto.getItemName().trim();
            String unit = dto.getUnit().trim();
            Long householdId = account.householdId();

            BigDecimal quantity = dto.getQuantityRemaining();
            BigDecimal price = dto.getLastPurchasePrice();

            if (quantity == null
                    || quantity.compareTo(BigDecimal.ZERO) < 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "Quantity must be zero or greater.");
            }

            if (price != null
                    && price.compareTo(BigDecimal.ZERO) < 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "Purchase price cannot be negative.");
            }

            int duration = dto.getExpectedDurationDays() == null
                    ? 30 : dto.getExpectedDurationDays();

            int reminder = dto.getReminderDaysBefore() == null
                    ? 3 : dto.getReminderDaysBefore();

            if (duration < 1 || reminder < 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "Duration must be at least 1 day and reminder cannot be negative.");
            }

            // 3. Find or create the globally shared catalog product.
            // The unique normalized name prevents duplicate catalog entries.
            KeyHolder catalogKey = new GeneratedKeyHolder();

            String catalogSql = """
            INSERT INTO items_catalog (item_name, default_unit)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE
                catalog_item_id = LAST_INSERT_ID(catalog_item_id)
            """;

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        catalogSql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, itemName);
                ps.setString(2, unit);
                return ps;
            }, catalogKey);

            Number catalogKeyNumber = catalogKey.getKey();

            if (catalogKeyNumber == null) {
                throw new IllegalStateException(
                        "Could not resolve the global catalog product.");
            }

            long catalogItemId = catalogKeyNumber.longValue();

            Map<String, Object> catalog = jdbcTemplate.queryForMap("""
            SELECT catalog_item_id, item_name, default_unit
            FROM items_catalog
            WHERE catalog_item_id = ?
            """, catalogItemId);

            String catalogName = catalog.get("item_name").toString();
            String defaultUnit = catalog.get("default_unit").toString();

            // This version requires a consistent unit for each catalog product.
            if (!defaultUnit.equalsIgnoreCase(unit)) {
                return error(HttpStatus.BAD_REQUEST,
                        "This product uses the unit '" + defaultUnit
                                + "'. Please use the same unit.");
            }

            // 4. Lock any existing inventory record for this household/product.
            List<Map<String, Object>> existingItems =
                    jdbcTemplate.queryForList("""
                    SELECT item_id, quantity_remaining, unit
                    FROM household_items
                    WHERE household_id = ?
                      AND catalog_item_id = ?
                    FOR UPDATE
                    """, householdId, catalogItemId);

            long itemId;
            boolean isNewItem = existingItems.isEmpty();

            if (!isNewItem) {
                // 5. Existing product: add the newly purchased quantity.
                Map<String, Object> existing = existingItems.get(0);

                itemId = ((Number) existing.get("item_id")).longValue();

                String existingUnit = existing.get("unit").toString();

                if (!existingUnit.equalsIgnoreCase(unit)) {
                    return error(HttpStatus.CONFLICT,
                            "The existing inventory uses unit '"
                                    + existingUnit + "'. Resolve the unit mismatch first.");
                }

                BigDecimal currentQuantity = new BigDecimal(
                        existing.get("quantity_remaining").toString());

                BigDecimal updatedQuantity = currentQuantity.add(quantity);

                jdbcTemplate.update("""
                UPDATE household_items
                SET item_name = ?,
                    quantity_remaining = ?,
                    purchase_date = COALESCE(?, purchase_date),
                    last_purchase_price =
                        COALESCE(?, last_purchase_price),
                    expected_duration_days = ?,
                    estimated_refill_date =
                        COALESCE(?, estimated_refill_date),
                    reminder_days_before = ?,
                    next_stock_check_at =
                        COALESCE(?, next_stock_check_at),
                    is_active = 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE item_id = ?
                  AND household_id = ?
                  AND catalog_item_id = ?
                """,
                        catalogName,
                        updatedQuantity,
                        dto.getPurchaseDate() == null
                                ? null
                                : java.sql.Date.valueOf(dto.getPurchaseDate()),
                        price,
                        duration,
                        dto.getEstimatedRefillDate() == null
                                ? null
                                : java.sql.Date.valueOf(
                                dto.getEstimatedRefillDate()),
                        reminder,
                        dto.getNextStockCheckAt() == null
                                ? null
                                : java.sql.Timestamp.valueOf(
                                dto.getNextStockCheckAt()),
                        itemId,
                        householdId,
                        catalogItemId);

            } else {
                // 6. New household/product combination: create inventory.
                String insertSql = """
                INSERT INTO household_items (
                    household_id, catalog_item_id, item_name,
                    quantity_remaining, unit, purchase_date,
                    last_purchase_price, expected_duration_days,
                    estimated_refill_date, reminder_days_before,
                    next_stock_check_at, planning_period, is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        'MONTHLY', 1)
                """;

                KeyHolder itemKey = new GeneratedKeyHolder();

                jdbcTemplate.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                            insertSql, Statement.RETURN_GENERATED_KEYS);

                    ps.setLong(1, householdId);
                    ps.setLong(2, catalogItemId);
                    ps.setString(3, catalogName);
                    ps.setBigDecimal(4, quantity);
                    ps.setString(5, unit);

                    if (dto.getPurchaseDate() == null) {
                        ps.setNull(6, Types.DATE);
                    } else {
                        ps.setDate(6, java.sql.Date.valueOf(
                                dto.getPurchaseDate()));
                    }

                    if (price == null) {
                        ps.setNull(7, Types.DECIMAL);
                    } else {
                        ps.setBigDecimal(7, price);
                    }

                    ps.setInt(8, duration);

                    if (dto.getEstimatedRefillDate() == null) {
                        ps.setNull(9, Types.DATE);
                    } else {
                        ps.setDate(9, java.sql.Date.valueOf(
                                dto.getEstimatedRefillDate()));
                    }

                    ps.setInt(10, reminder);

                    if (dto.getNextStockCheckAt() == null) {
                        ps.setNull(11, Types.TIMESTAMP);
                    } else {
                        ps.setTimestamp(11, java.sql.Timestamp.valueOf(
                                dto.getNextStockCheckAt()));
                    }

                    return ps;
                }, itemKey);

                Number generatedItemKey = itemKey.getKey();

                if (generatedItemKey == null) {
                    throw new IllegalStateException(
                            "Could not generate the inventory item ID.");
                }

                itemId = generatedItemKey.longValue();
            }

            // 7. Record only this purchase, not the total stock.
            if (dto.getPurchaseDate() != null) {
                BigDecimal total = price == null
                        ? BigDecimal.ZERO : price;

                BigDecimal unitPrice = price;

                if (price != null
                        && quantity.compareTo(BigDecimal.ZERO) > 0) {
                    unitPrice = total.divide(
                            quantity, 2, RoundingMode.HALF_UP);
                }

                jdbcTemplate.update("""
                INSERT INTO purchase_history (
                    item_id, quantity_purchased, unit,
                    unit_price, total_amount, purchase_date
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                        itemId,
                        quantity,
                        unit,
                        unitPrice,
                        total,
                        java.sql.Date.valueOf(dto.getPurchaseDate()));
            }

            return ResponseEntity.status(
                    isNewItem ? HttpStatus.CREATED : HttpStatus.OK
            ).body(Map.of(
                    "message", isNewItem
                            ? "Product added to household inventory."
                            : "Existing stock updated successfully.",
                    "itemId", itemId,
                    "catalogItemId", catalogItemId,
                    "householdId", householdId,
                    "itemName", catalogName,
                    "quantityAdded", quantity,
                    "inventoryAction", isNewItem ? "CREATED" : "UPDATED"
            ));

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not save the household inventory item.", e);
        }
    }

    // GET /api/household/items/1/purchases
    // householdId is optional for backward compatibility.
    @GetMapping("/items/{itemId}/purchases")
    public ResponseEntity<?> getPurchaseHistory(
            @PathVariable Long itemId,
            @RequestParam(required = false) Long householdId) {
        try {
            if (!validId(itemId)
                    || (householdId != null && !validId(householdId))) {
                return error(HttpStatus.BAD_REQUEST,
                        "Invalid item or household ID.");
            }

            return ResponseEntity.ok(jdbcTemplate.queryForList("""
                SELECT ph.purchase_id, ph.item_id, hi.item_name,
                       ph.quantity_purchased, ph.unit,
                       ph.unit_price, ph.total_amount,
                       ph.purchase_date, ph.purchased_by,
                       ph.vendor_id, ph.notes, ph.created_at
                FROM purchase_history ph
                JOIN household_items hi ON hi.item_id = ph.item_id
                WHERE hi.item_id = ?
                  AND (? IS NULL OR hi.household_id = ?)
                ORDER BY ph.purchase_date DESC, ph.purchase_id DESC
                """, itemId, householdId, householdId));

        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not load purchase history.");
        }
    }

    @PostMapping("/items/{itemId}/checkins")
    @Transactional
    public ResponseEntity<?> addStockCheckin(
            @PathVariable Long itemId,
            @RequestBody Map<String, Object> body) {
        try {
            if (!validId(itemId)) {
                return error(HttpStatus.BAD_REQUEST,
                        "Invalid item ID.");
            }

            if (body == null || body.get("quantity_remaining") == null
                    || body.get("will_last_until_date") == null) {
                return error(HttpStatus.BAD_REQUEST,
                        "quantity_remaining and will_last_until_date are required.");
            }

            BigDecimal quantity = new BigDecimal(
                    body.get("quantity_remaining").toString());

            if (quantity.compareTo(BigDecimal.ZERO) < 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "Remaining quantity cannot be negative.");
            }

            Object lastsValue = body.get("will_last_until_date");
            boolean willLast;

            if (lastsValue instanceof Boolean) {
                willLast = (Boolean) lastsValue;
            } else if ("1".equals(lastsValue.toString())
                    || "0".equals(lastsValue.toString())) {
                willLast = "1".equals(lastsValue.toString());
            } else {
                return error(HttpStatus.BAD_REQUEST,
                        "will_last_until_date must be true or false.");
            }

            Object householdValue = body.get("household_id");
            Long householdId = householdValue == null ? null
                    : Long.valueOf(householdValue.toString());

            if (householdId != null && !validId(householdId)) {
                return error(HttpStatus.BAD_REQUEST,
                        "Invalid household ID.");
            }

            // Lock and verify the active inventory item.
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT household_id
                FROM household_items
                WHERE item_id = ? AND is_active = 1
                FOR UPDATE
                """, itemId);

            if (rows.isEmpty()) {
                return error(HttpStatus.NOT_FOUND,
                        "Active inventory item not found.");
            }

            long actualHouseholdId = ((Number)
                    rows.get(0).get("household_id")).longValue();

            if (householdId != null
                    && householdId.longValue() != actualHouseholdId) {
                return error(HttpStatus.NOT_FOUND,
                        "Item does not belong to the specified household.");
            }

            Object userValue = body.get("user_id");
            Long userId = userValue == null ? null
                    : Long.valueOf(userValue.toString());

            if (userId != null) {
                Integer userExists = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM users WHERE user_id = ?
                    """, Integer.class, userId);

                if (userExists == null || userExists == 0) {
                    return error(HttpStatus.BAD_REQUEST,
                            "The specified user does not exist.");
                }
            }

            Object extraValue = body.get("expected_extra_days");
            Integer extraDays = extraValue == null ? null
                    : Integer.valueOf(extraValue.toString());

            if (extraDays != null && extraDays < 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "expected_extra_days cannot be negative.");
            }

            Object nextValue = body.get("next_checkin_at");
            java.sql.Timestamp nextCheck = null;

            if (nextValue != null && !nextValue.toString().isBlank()) {
                nextCheck = java.sql.Timestamp.valueOf(
                        LocalDateTime.parse(nextValue.toString()));
            }

            Object notesValue = body.get("notes");
            String notes = notesValue == null
                    ? null : notesValue.toString();

            jdbcTemplate.update("""
                INSERT INTO stock_checkins (
                    item_id, user_id, quantity_remaining,
                    will_last_until_date, expected_extra_days,
                    checked_at, next_checkin_at, notes
                )
                VALUES (?, ?, ?, ?, ?, NOW(), ?, ?)
                """,
                    itemId,
                    userId,
                    quantity,
                    willLast,
                    extraDays,
                    nextCheck,
                    notes);

            jdbcTemplate.update("""
                UPDATE household_items
                SET quantity_remaining = ?,
                    next_stock_check_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE item_id = ?
                """,
                    quantity, nextCheck, itemId);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of(
                            "message", "Stock check recorded successfully.",
                            "itemId", itemId));

        } catch (NumberFormatException
                 | java.time.format.DateTimeParseException e) {
            return error(HttpStatus.BAD_REQUEST,
                    "Invalid numeric value or date/time format.");
        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not record the stock check.", e);
        }
    }

    // GET /api/household/items/1/checkins
    // Optional householdId adds an ownership filter.
    @GetMapping("/items/{itemId}/checkins")
    public ResponseEntity<?> getStockCheckins(
            @PathVariable Long itemId,
            @RequestParam(required = false) Long householdId) {
        try {
            if (!validId(itemId)
                    || (householdId != null && !validId(householdId))) {
                return error(HttpStatus.BAD_REQUEST,
                        "Invalid item or household ID.");
            }

            return ResponseEntity.ok(jdbcTemplate.queryForList("""
                SELECT sc.checkin_id, sc.item_id, sc.user_id,
                       sc.quantity_remaining, sc.will_last_until_date,
                       sc.expected_extra_days, sc.checked_at,
                       sc.next_checkin_at, sc.notes
                FROM stock_checkins sc
                JOIN household_items hi ON hi.item_id = sc.item_id
                WHERE hi.item_id = ?
                  AND (? IS NULL OR hi.household_id = ?)
                ORDER BY sc.checked_at DESC, sc.checkin_id DESC
                """, itemId, householdId, householdId));

        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not load stock-check history.");
        }
    }

    private boolean validId(Long id) {
        return id != null && id > 0;
    }


    @PostMapping("/items/{itemId}/stock")
    @Transactional
    public ResponseEntity<?> updateStock(
            @PathVariable Long itemId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        if (!validId(itemId)) {
            return error(HttpStatus.BAD_REQUEST, "Invalid item ID.");
        }

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
        }

        if (!"USER".equals(account.role())
                || account.householdId() == null) {
            return error(HttpStatus.FORBIDDEN,
                    "You are not authorized to update household stock.");
        }

        if (body == null || body.get("action") == null
                || body.get("quantity") == null) {
            return error(HttpStatus.BAD_REQUEST,
                    "Action and quantity are required.");
        }

        String action = body.get("action").toString()
                .trim().toUpperCase();

        if (!action.equals("CONSUME")
                && !action.equals("REPLENISH")) {
            return error(HttpStatus.BAD_REQUEST,
                    "Action must be CONSUME or REPLENISH.");
        }

        final BigDecimal amount;

        try {
            amount = new BigDecimal(body.get("quantity").toString());
        } catch (NumberFormatException e) {
            return error(HttpStatus.BAD_REQUEST,
                    "Quantity must be a valid number.");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "Quantity must be greater than zero.");
        }

        Long householdId = account.householdId();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT quantity_remaining, unit
            FROM household_items
            WHERE item_id = ?
              AND household_id = ?
              AND is_active = 1
            FOR UPDATE
            """, itemId, householdId);

        if (rows.isEmpty()) {
            return error(HttpStatus.NOT_FOUND,
                    "Item not found in your household.");
        }

        Map<String, Object> item = rows.get(0);
        Object storedQuantity = item.get("quantity_remaining");

        BigDecimal currentQuantity = storedQuantity == null
                ? BigDecimal.ZERO
                : new BigDecimal(storedQuantity.toString());

        BigDecimal newQuantity;

        if (action.equals("CONSUME")) {
            if (amount.compareTo(currentQuantity) > 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "Consumed quantity cannot exceed available stock.");
            }

            newQuantity = currentQuantity.subtract(amount);
        } else {
            newQuantity = currentQuantity.add(amount);
        }

        // 1. Update the current stock.
        jdbcTemplate.update("""
            UPDATE household_items
            SET quantity_remaining = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE item_id = ?
              AND household_id = ?
              AND is_active = 1
            """, newQuantity, itemId, householdId);

        // 2. Record the stock change in the same transaction.
        jdbcTemplate.update("""
            INSERT INTO stock_transactions (
                item_id,
                user_id,
                transaction_type,
                quantity,
                previous_quantity,
                new_quantity
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
                itemId,
                account.id(),
                action,
                amount,
                currentQuantity,
                newQuantity
        );

        return ResponseEntity.ok(Map.of(
                "message", "Stock updated and transaction recorded successfully.",
                "itemId", itemId,
                "action", action,
                "previousQuantity", currentQuantity,
                "quantityChanged", amount,
                "quantityRemaining", newQuantity,
                "unit", item.get("unit") == null
                        ? "" : item.get("unit").toString()
        ));
    }
    private ResponseEntity<Map<String, String>> error(
            HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("message", message));
    }

    @GetMapping("/items/{itemId}/prediction")
    public ResponseEntity<?> getStockPrediction(
            @PathVariable Long itemId,
            Authentication authentication) {

        if (!validId(itemId)) {
            return error(HttpStatus.BAD_REQUEST, "Invalid item ID.");
        }

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Please log in again.");
        }

        if (!"USER".equals(account.role())
                || account.householdId() == null) {
            return error(HttpStatus.FORBIDDEN,
                    "You are not authorized to view this prediction.");
        }

        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
        SELECT item_id, item_name, quantity_remaining, unit
        FROM household_items
        WHERE item_id = ?
          AND household_id = ?
          AND is_active = 1
        """, itemId, account.householdId());

        if (items.isEmpty()) {
            return error(HttpStatus.NOT_FOUND,
                    "Item not found in your household.");
        }

        Map<String, Object> item = items.get(0);

        BigDecimal remaining = new BigDecimal(
                item.get("quantity_remaining").toString());

        // Use consumption records only. Replenishments are not usage.
        List<Map<String, Object>> transactions =
                jdbcTemplate.queryForList("""
        SELECT quantity, created_at
        FROM stock_transactions
        WHERE item_id = ?
          AND transaction_type = 'CONSUME'
          AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
        ORDER BY created_at ASC
        """, itemId);

        Map<String, Object> result = new HashMap<>();
        result.put("itemId", itemId);
        result.put("itemName", item.get("item_name"));
        result.put("quantityRemaining", remaining);
        result.put("unit", item.get("unit"));
        result.put("observationWindowDays", 30);
        result.put("consumptionRecords", transactions.size());

        // Avoid making a prediction from insufficient history.
        if (transactions.size() < 2) {
            result.put("enoughData", false);
            result.put("message",
                    "Collecting more consumption data.");
            result.put("averageDailyConsumption", null);
            result.put("estimatedDaysRemaining", null);
            result.put("predictedRefillDate", null);
            result.put("stockStatus", "INSUFFICIENT_DATA");

            return ResponseEntity.ok(result);
        }

        Timestamp firstUse = (Timestamp)
                transactions.get(0).get("created_at");

        Timestamp lastUse = (Timestamp)
                transactions.get(transactions.size() - 1)
                        .get("created_at");

        long elapsedSeconds = Duration.between(
                firstUse.toInstant(),
                lastUse.toInstant()).getSeconds();

        // Require at least three full days between observations.
        if (elapsedSeconds < Duration.ofDays(3).getSeconds()) {
            result.put("enoughData", false);
            result.put("message",
                    "More days of consumption history are needed.");
            result.put("averageDailyConsumption", null);
            result.put("estimatedDaysRemaining", null);
            result.put("predictedRefillDate", null);
            result.put("stockStatus", "INSUFFICIENT_DATA");

            return ResponseEntity.ok(result);
        }

        BigDecimal totalConsumed = transactions.stream()
                .map(row -> new BigDecimal(
                        row.get("quantity").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal elapsedDays = BigDecimal.valueOf(elapsedSeconds)
                .divide(BigDecimal.valueOf(86400), 6,
                        RoundingMode.HALF_UP);

        BigDecimal dailyUsage = totalConsumed.divide(
                elapsedDays, 4, RoundingMode.HALF_UP);

        result.put("enoughData", true);
        result.put("averageDailyConsumption", dailyUsage);

        if (dailyUsage.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("estimatedDaysRemaining", null);
            result.put("predictedRefillDate", null);
            result.put("stockStatus", "INSUFFICIENT_DATA");
            result.put("message",
                    "A positive consumption rate is required.");
            return ResponseEntity.ok(result);
        }

        long daysRemaining = remaining
                .divide(dailyUsage, 0, RoundingMode.DOWN)
                .longValue();

        LocalDate refillDate = LocalDate.now()
                .plusDays(daysRemaining);

        result.put("estimatedDaysRemaining", daysRemaining);
        result.put("predictedRefillDate", refillDate.toString());

        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            result.put("stockStatus", "OUT_OF_STOCK");
        } else if (daysRemaining <= 3) {
            result.put("stockStatus", "LOW_STOCK");
        } else {
            result.put("stockStatus", "AVAILABLE");
        }

        result.put("message", "Prediction calculated from recorded usage.");

        return ResponseEntity.ok(result);
    }

}



//package com.grocerymate.controller;
//
//import com.grocerymate.dto.ItemDto;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.web.bind.annotation.*;
//
//import java.math.BigDecimal;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/household")
//@CrossOrigin(origins = "*")
//public class    HouseholdController {
//
//    private final JdbcTemplate jdbcTemplate;
//
//    public HouseholdController(JdbcTemplate jdbcTemplate) {
//        this.jdbcTemplate = jdbcTemplate;
//    }
//
//    // GET /api/household/items?householdId=...
//    @GetMapping("/items")
//    public ResponseEntity<?> getItems(@RequestParam(value = "householdId", required = false) Long householdId) {
//        try {
//            if (householdId == null || householdId <= 0) {
//                return ResponseEntity.ok(Collections.emptyList());
//            }
//
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
//                  AND household_id = ?
//                ORDER BY item_id DESC
//                """;
//
//            List<Map<String, Object>> items = jdbcTemplate.queryForList(sql, householdId);
//            return ResponseEntity.ok(items);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load household items.");
//        }
//    }
//
//    // GET /api/household/spending/monthly?householdId=...
//    @GetMapping("/spending/monthly")
//    public ResponseEntity<?> getMonthlySpending(@RequestParam(value = "householdId", required = false) Long householdId) {
//        try {
//            if (householdId == null || householdId <= 0) {
//                return ResponseEntity.ok(BigDecimal.ZERO);
//            }
//
//            BigDecimal total = jdbcTemplate.queryForObject("""
//                SELECT COALESCE(SUM(last_purchase_price), 0)
//                FROM household_items
//                WHERE is_active = 1
//                  AND household_id = ?
//                  AND purchase_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01')
//                  AND purchase_date < DATE_FORMAT(
//                      DATE_ADD(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01'
//                  )
//                """, BigDecimal.class, householdId);
//
//            return ResponseEntity.ok(total == null ? BigDecimal.ZERO : total);
//
//        } catch (Exception e) {
//            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Could not calculate monthly spending.");
//        }
//    }
//
//    // GET /api/household/purchases/upcoming?householdId=...
//    @GetMapping("/purchases/upcoming")
//    public ResponseEntity<?> getUpcomingPurchases(@RequestParam(value = "householdId", required = false) Long householdId) {
//        try {
//            if (householdId == null || householdId <= 0) {
//                return ResponseEntity.ok(Collections.emptyList());
//            }
//
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
//                  AND household_id = ?
//                  AND estimated_refill_date IS NOT NULL
//                  AND estimated_refill_date >= CURDATE()
//                ORDER BY estimated_refill_date ASC
//                """;
//
//            return ResponseEntity.ok(jdbcTemplate.queryForList(sql, householdId));
//
//        } catch (Exception e) {
//            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load upcoming purchases.");
//        }
//    }
//
//    // POST /api/household/items
//    @PostMapping("/items")
//    public ResponseEntity<?> addItem(@RequestBody ItemDto itemDto) {
//        try {
//            if (itemDto.getItemName() == null || itemDto.getItemName().isBlank()) {
//                return errorResponse(HttpStatus.BAD_REQUEST, "Item name is required.");
//            }
//
//            if (itemDto.getQuantityRemaining() == null || itemDto.getQuantityRemaining().compareTo(BigDecimal.ZERO) < 0) {
//                return errorResponse(HttpStatus.BAD_REQUEST, "Remaining quantity must be zero or greater.");
//            }
//
//            if (itemDto.getUnit() == null || itemDto.getUnit().isBlank()) {
//                return errorResponse(HttpStatus.BAD_REQUEST, "Unit is required.");
//            }
//
//            if (itemDto.getLastPurchasePrice() != null && itemDto.getLastPurchasePrice().compareTo(BigDecimal.ZERO) < 0) {
//                return errorResponse(HttpStatus.BAD_REQUEST, "Purchase price cannot be negative.");
//            }
//
//            int duration = itemDto.getExpectedDurationDays() == null ? 30 : itemDto.getExpectedDurationDays();
//            int reminder = itemDto.getReminderDaysBefore() == null ? 3 : itemDto.getReminderDaysBefore();
//
//            if (duration < 1 || reminder < 0) {
//                return errorResponse(HttpStatus.BAD_REQUEST, "Duration must be at least 1 day and reminder cannot be negative.");
//            }
//
//            // Resolve target household
//            Long householdId = itemDto.getHouseholdId();
//            if (householdId == null || householdId <= 0) {
//                List<Long> fallbackIds = jdbcTemplate.queryForList("SELECT household_id FROM households LIMIT 1", Long.class);
//                if (fallbackIds.isEmpty()) {
//                    return errorResponse(HttpStatus.BAD_REQUEST, "No household found in database.");
//                }
//                householdId = fallbackIds.get(0);
//            }
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
//            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Database insert failed: " + e.getMessage());
//        }
//    }
//
//    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String message) {
//        return ResponseEntity.status(status).body(Map.of("message", message));
//    }
//}
//
