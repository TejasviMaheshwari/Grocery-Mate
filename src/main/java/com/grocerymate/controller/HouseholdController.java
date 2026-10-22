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
            @RequestParam(required = false) Long householdId,
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Please log in again.");
        }

        if (!"USER".equals(account.role())
                || account.householdId() == null) {
            return error(HttpStatus.FORBIDDEN,
                    "You are not authorized to view household inventory.");
        }

        Long authenticatedHouseholdId = account.householdId();

        if (householdId != null
                && !authenticatedHouseholdId.equals(householdId)) {
            return error(HttpStatus.FORBIDDEN,
                    "You cannot view another household's inventory.");
        }

        try {
            return ResponseEntity.ok(jdbcTemplate.queryForList("""
            SELECT
                hi.item_id,
                hi.household_id,
                hi.catalog_item_id,
                hi.item_name,
                hi.quantity_remaining,
                hi.low_stock_threshold,
                hi.unit,
                hi.purchase_date,
                hi.last_purchase_price,
                hi.expected_duration_days,
                hi.estimated_refill_date,
                hi.reminder_days_before,
                hi.planning_period,
                hi.next_stock_check_at,
                hi.is_active,

                lp.purchase_date AS last_purchase_date,
                lp.quantity_purchased AS last_quantity_purchased,
                lp.unit_price AS last_unit_price,
                lp.total_amount AS last_purchase_total,
                lp.purchase_id AS last_purchase_id,

                hi.created_at,
                hi.updated_at

            FROM household_items hi

            LEFT JOIN purchase_history lp
                ON lp.purchase_id = (
                    SELECT ph.purchase_id
                    FROM purchase_history ph
                    WHERE ph.item_id = hi.item_id
                      AND (
                          ph.notes IS NULL
                          OR ph.notes NOT LIKE 'TEST DATA%'
                      )
                    ORDER BY ph.purchase_date DESC,
                             ph.purchase_id DESC
                    LIMIT 1
                )

            WHERE hi.household_id = ?
              AND hi.is_active = 1

            ORDER BY hi.item_id DESC
            """, authenticatedHouseholdId));

        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not load household items.");
        }
    }


    // GET /api/household/spending/monthly?householdId=1
    @GetMapping("/spending/monthly")
    public ResponseEntity<?> getMonthlySpending(
            @RequestParam(required = false) Long householdId,
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
        }

        if (!"USER".equalsIgnoreCase(account.role())
                || account.householdId() == null) {
            return error(HttpStatus.FORBIDDEN,
                    "You are not authorized to view household spending.");
        }

        Long authenticatedHouseholdId = account.householdId();

        if (householdId != null
                && !authenticatedHouseholdId.equals(householdId)) {
            return error(HttpStatus.FORBIDDEN,
                    "You cannot view another household's spending.");
        }

        try {
            BigDecimal total = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(ph.total_amount), 0)
            FROM purchase_history ph
            JOIN household_items hi ON hi.item_id = ph.item_id
            WHERE hi.household_id = ?
              AND (
                  ph.notes IS NULL
                  OR ph.notes NOT LIKE 'TEST DATA%'
              )
              AND ph.purchase_date >= DATE_FORMAT(
                  CURDATE(), '%Y-%m-01')
              AND ph.purchase_date < DATE_FORMAT(
                  DATE_ADD(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
            """, BigDecimal.class, authenticatedHouseholdId);

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
            @RequestParam(required = false) Long householdId,
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
        }

        if (!"USER".equalsIgnoreCase(account.role())
                || account.householdId() == null) {
            return error(HttpStatus.FORBIDDEN,
                    "You are not authorized to view upcoming purchases.");
        }

        Long authenticatedHouseholdId = account.householdId();

        if (householdId != null
                && !authenticatedHouseholdId.equals(householdId)) {
            return error(HttpStatus.FORBIDDEN,
                    "You cannot view another household's upcoming purchases.");
        }

        try {
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
            """, authenticatedHouseholdId));

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

            if (dto == null
                    || !validId(dto.getHouseholdId())
                    || !account.householdId().equals(dto.getHouseholdId())) {
                return error(HttpStatus.FORBIDDEN,
                        "You cannot modify another household's inventory.");
            }

            // 2. Validate purchase details before writing to the database.
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
            long userId = account.id();

            BigDecimal quantity = dto.getQuantityPurchased();
            BigDecimal totalAmount = dto.getTotalPurchaseCost();

            if (quantity == null
                    || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "Purchased quantity must be greater than zero.");
            }

            if (totalAmount == null
                    || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "Total purchase cost must be zero or greater.");
            }

            BigDecimal lowStockThreshold = dto.getLowStockThreshold();

            if (lowStockThreshold != null
                    && lowStockThreshold.compareTo(BigDecimal.ZERO) < 0) {
                return error(
                        HttpStatus.BAD_REQUEST,
                        "Low-stock threshold cannot be negative."
                );
            }
            if (dto.getPurchaseDate() == null) {
                return error(HttpStatus.BAD_REQUEST,
                        "Purchase date is required.");
            }



            int duration = dto.getExpectedDurationDays() == null
                    ? 30 : dto.getExpectedDurationDays();

            int reminder = dto.getReminderDaysBefore() == null
                    ? 3 : dto.getReminderDaysBefore();

            if (duration < 1 || reminder < 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "Duration must be at least 1 day and reminder cannot be negative.");
            }

            // Calculate the per-unit price in the backend.
            // Store it to 2 decimal places for the unit-price fields.
            BigDecimal unitPrice = totalAmount.divide(quantity, 2, RoundingMode.HALF_UP);

            // Keep the original total cost for purchase history.
            // Do not recalculate totalAmount by multiplying the rounded unit price.
            totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
            // Price is per unit; calculate the full cost of this purchase.
//            BigDecimal totalAmount = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

            java.sql.Date purchaseDate =
                    java.sql.Date.valueOf(dto.getPurchaseDate());

            java.sql.Date refillDate =
                    dto.getEstimatedRefillDate() == null
                            ? null
                            : java.sql.Date.valueOf(
                            dto.getEstimatedRefillDate());

            java.sql.Timestamp nextCheck =
                    dto.getNextStockCheckAt() == null
                            ? null
                            : java.sql.Timestamp.valueOf(
                            dto.getNextStockCheckAt());

            // 3. Find or create the shared catalog product.
            // The database's unique normalized name prevents duplicates.
            jdbcTemplate.update("""
            INSERT INTO items_catalog (item_name, default_unit)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE
                catalog_item_id = LAST_INSERT_ID(catalog_item_id)
            """, itemName, unit);

            Map<String, Object> catalog = jdbcTemplate.queryForMap("""
            SELECT catalog_item_id, item_name, default_unit
            FROM items_catalog
            WHERE normalized_item_name = LOWER(TRIM(?))
            """, itemName);

            long catalogItemId = ((Number)
                    catalog.get("catalog_item_id")).longValue();

            String catalogName = catalog.get("item_name").toString();
            String defaultUnit = catalog.get("default_unit").toString();

            if (!defaultUnit.equalsIgnoreCase(unit)) {
                return error(HttpStatus.BAD_REQUEST,
                        "This product uses the unit '" + defaultUnit
                                + "'. Please use the same unit.");
            }

            // 4. Lock existing household inventory, if any.
            List<Map<String, Object>> existingItems =
                    jdbcTemplate.queryForList("""
                    SELECT item_id, quantity_remaining, unit
                    FROM household_items
                    WHERE household_id = ?
                      AND catalog_item_id = ?
                    FOR UPDATE
                    """, householdId, catalogItemId);

            boolean isNewItem = existingItems.isEmpty();
            long itemId;
            BigDecimal previousQuantity;

            if (!isNewItem) {
                Map<String, Object> existing = existingItems.get(0);

                itemId = ((Number)
                        existing.get("item_id")).longValue();

                String existingUnit = existing.get("unit").toString();

                if (!existingUnit.equalsIgnoreCase(unit)) {
                    return error(HttpStatus.CONFLICT,
                            "Existing inventory uses unit '"
                                    + existingUnit
                                    + "'. Resolve the unit mismatch first.");
                }

                previousQuantity = new BigDecimal(
                        existing.get("quantity_remaining").toString());

                BigDecimal updatedQuantity =
                        previousQuantity.add(quantity);

                // Add the purchase to stock, never replace existing stock.
                jdbcTemplate.update("""
                UPDATE household_items
                SET item_name = ?,
                    quantity_remaining = ?,
//                    low_stock_threshold = ?,
                    low_stock_threshold = COALESCE(?, low_stock_threshold),
                    unit = ?,
                    purchase_date = ?,
                    last_purchase_price = ?,
                    expected_duration_days = ?,
                    estimated_refill_date = COALESCE(
                        ?, estimated_refill_date),
                    reminder_days_before = ?,
                    next_stock_check_at = COALESCE(
                        ?, next_stock_check_at),
                    is_active = 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE item_id = ?
                  AND household_id = ?
                  AND catalog_item_id = ?
                """,
                        catalogName,
                        updatedQuantity,
                        lowStockThreshold,
                        unit,
                        purchaseDate,
                        unitPrice,
                        duration,
                        refillDate,
                        reminder,
                        nextCheck,
                        itemId,
                        householdId,
                        catalogItemId);

            } else {
                // 5. Create inventory for a product not yet in this household.
                KeyHolder itemKey = new GeneratedKeyHolder();

                String insertItemSql = """
                INSERT INTO household_items (
                    household_id,
                    catalog_item_id,
                    item_name,
                    quantity_remaining,
                    low_stock_threshold,
                    unit,
                    purchase_date,
                    last_purchase_price,
                    expected_duration_days,
                    estimated_refill_date,
                    reminder_days_before,
                    next_stock_check_at,
                    planning_period,
                    is_active
                )
                VALUES (?, ?, ?, ?, ?, ? , ?, ?, ?, ?, ?, ?,
                        'MONTHLY', 1)
                """;

                jdbcTemplate.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                            insertItemSql, Statement.RETURN_GENERATED_KEYS);

                    ps.setLong(1, householdId);
                    ps.setLong(2, catalogItemId);
                    ps.setString(3, catalogName);
                    ps.setBigDecimal(4, quantity);
                    if (lowStockThreshold == null) {
                        ps.setNull(5, Types.DECIMAL);
                    } else {
                        ps.setBigDecimal(5, lowStockThreshold);
                    }
                    ps.setString(6, unit);
                    ps.setDate(7, purchaseDate);
                    ps.setBigDecimal(8, unitPrice);
                    ps.setInt(9, duration);

                    if (refillDate == null) {
                        ps.setNull(10, Types.DATE);
                    } else {
                        ps.setDate(10, refillDate);
                    }

                    ps.setInt(11, reminder);

                    if (nextCheck == null) {
                        ps.setNull(12, Types.TIMESTAMP);
                    } else {
                        ps.setTimestamp(12, nextCheck);
                    }

                    return ps;
                }, itemKey);

                Number generatedId = itemKey.getKey();

                if (generatedId == null) {
                    throw new IllegalStateException(
                            "Could not generate the inventory item ID.");
                }

                itemId = generatedId.longValue();
                previousQuantity = BigDecimal.ZERO;
            }

            BigDecimal updatedQuantity = previousQuantity.add(quantity);

            // 6. Record this purchase separately from the inventory total.
            jdbcTemplate.update("""
            INSERT INTO purchase_history (
                item_id,
                purchased_by,
                quantity_purchased,
                unit,
                unit_price,
                total_amount,
                purchase_date
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
                    itemId,
                    userId,
                    quantity,
                    unit,
                    unitPrice,
                    totalAmount,
                    purchaseDate);

            // 7. Record the stock replenishment for the inventory timeline.
            jdbcTemplate.update("""
            INSERT INTO stock_transactions (
                item_id,
                user_id,
                transaction_type,
                quantity,
                previous_quantity,
                new_quantity
            )
            VALUES (?, ?, 'REPLENISH', ?, ?, ?)
            """,
                    itemId,
                    userId,
                    quantity,
                    previousQuantity,
                    updatedQuantity);

            createLowStockNotificationIfNeeded(
                    userId,
                    householdId,
                    itemId,
                    catalogName,
                    updatedQuantity
            );

            // 8. Return the purchase and inventory summary.
            Map<String, Object> response = new HashMap<>();

            response.put("message", isNewItem
                    ? "Product and purchase recorded successfully."
                    : "Stock increased and purchase recorded successfully.");
            response.put("itemId", itemId);
            response.put("catalogItemId", catalogItemId);
            response.put("householdId", householdId);
            response.put("itemName", catalogName);
            response.put("quantityPurchased", quantity);
            response.put("previousQuantity", previousQuantity);
            response.put("quantityRemaining", updatedQuantity);
            response.put("unit", unit);
            response.put("unitPrice", unitPrice);
            response.put("totalAmount", totalAmount);
            response.put("purchaseDate", dto.getPurchaseDate());
            response.put("purchasedBy", userId);
            response.put("inventoryAction",
                    isNewItem ? "CREATED" : "UPDATED");

            return ResponseEntity.status(
                    isNewItem ? HttpStatus.CREATED : HttpStatus.OK
            ).body(response);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not save the household purchase and inventory.", e);
        }
    }


    // GET /api/household/items/1/purchases
    // householdId is optional for backward compatibility.
    @GetMapping("/items/{itemId}/purchases")
    public ResponseEntity<?> getPurchaseHistory(
            @PathVariable Long itemId,
            @RequestParam(required = false) Long householdId,
            Authentication authentication) {

        if (!validId(itemId)
                || (householdId != null && !validId(householdId))) {
            return error(HttpStatus.BAD_REQUEST,
                    "Invalid item or household ID.");
        }

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
        }

        if (!"USER".equalsIgnoreCase(account.role())
                || account.householdId() == null) {
            return error(HttpStatus.FORBIDDEN,
                    "You are not authorized to view purchase history.");
        }

        Long authenticatedHouseholdId = account.householdId();

        if (householdId != null
                && !authenticatedHouseholdId.equals(householdId)) {
            return error(HttpStatus.FORBIDDEN,
                    "You cannot view another household's purchase history.");
        }

        try {
            // The authenticated household is always used for ownership.
            List<Map<String, Object>> purchases = jdbcTemplate.queryForList("""
            SELECT ph.purchase_id, ph.item_id, hi.item_name,
                   ph.quantity_purchased, ph.unit,
                   ph.unit_price, ph.total_amount,
                   ph.purchase_date, ph.purchased_by,
                   ph.vendor_id, ph.notes, ph.created_at
            FROM purchase_history ph
            JOIN household_items hi ON hi.item_id = ph.item_id
            WHERE hi.item_id = ?
              AND hi.household_id = ?
              AND (
                  ph.notes IS NULL
                  OR ph.notes NOT LIKE 'TEST DATA%'
              )
            ORDER BY ph.purchase_date DESC, ph.purchase_id DESC
            """, itemId, authenticatedHouseholdId);

            return ResponseEntity.ok(purchases);

        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not load purchase history.");
        }
    }

    @PostMapping("/items/{itemId}/checkins")
    @Transactional
    public ResponseEntity<?> addStockCheckin(
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

        if (!"USER".equalsIgnoreCase(account.role())
                || account.householdId() == null) {
            return error(HttpStatus.FORBIDDEN,
                    "You are not authorized to record a stock check.");
        }

        if (body == null
                || body.get("quantity_remaining") == null
                || body.get("will_last_until_date") == null) {
            return error(HttpStatus.BAD_REQUEST,
                    "quantity_remaining and will_last_until_date are required.");
        }

        try {
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

            Long authenticatedHouseholdId = account.householdId();

            // Lock only an active item belonging to the logged-in household.

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT item_id, item_name, quantity_remaining
                FROM household_items
                WHERE item_id = ?
                  AND household_id = ?
                  AND is_active = 1
                FOR UPDATE
                """, itemId, authenticatedHouseholdId);


            if (rows.isEmpty()) {
                // Avoid revealing whether another household owns this item.
                return error(HttpStatus.NOT_FOUND,
                        "Active inventory item not found in your household.");
            }

            Map<String, Object> lockedItem = rows.get(0);

            BigDecimal previousQuantity = new BigDecimal(
                    lockedItem.get("quantity_remaining").toString());

            String itemName = lockedItem.get("item_name").toString();

            // Never accept user identity from the request body.
            long userId = account.id();

            Object extraValue = body.get("expected_extra_days");
            Integer extraDays = extraValue == null
                    ? null : Integer.valueOf(extraValue.toString());

            if (extraDays != null && extraDays < 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "expected_extra_days cannot be negative.");
            }

            Object nextValue = body.get("next_checkin_at");
            Timestamp nextCheck = null;

            if (nextValue != null && !nextValue.toString().isBlank()) {
                nextCheck = Timestamp.valueOf(
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

            // Keep the household condition on the update as defense in depth.
            jdbcTemplate.update("""
            UPDATE household_items
            SET quantity_remaining = ?,
                next_stock_check_at = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE item_id = ?
              AND household_id = ?
              AND is_active = 1
            """,
                    quantity,
                    nextCheck,
                    itemId,
                    authenticatedHouseholdId);


            createStockOutNotificationIfNeeded(
                    userId,
                    authenticatedHouseholdId,
                    itemId,
                    itemName,
                    previousQuantity,
                    quantity
            );

            createLowStockNotificationIfNeeded(
                    userId,
                    authenticatedHouseholdId,
                    itemId,
                    itemName,
                    quantity
            );
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
            @RequestParam(required = false) Long householdId,
            Authentication authentication) {

        if (!validId(itemId)
                || (householdId != null && !validId(householdId))) {
            return error(HttpStatus.BAD_REQUEST,
                    "Invalid item or household ID.");
        }

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
        }

        if (!"USER".equalsIgnoreCase(account.role())
                || account.householdId() == null) {
            return error(HttpStatus.FORBIDDEN,
                    "You are not authorized to view stock-check history.");
        }

        Long ownHouseholdId = account.householdId();

        if (householdId != null && !ownHouseholdId.equals(householdId)) {
            return error(HttpStatus.FORBIDDEN,
                    "You cannot view another household's stock-check history.");
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT sc.checkin_id, sc.item_id, sc.user_id,
                   sc.quantity_remaining, sc.will_last_until_date,
                   sc.expected_extra_days, sc.checked_at,
                   sc.next_checkin_at, sc.notes
            FROM stock_checkins sc
            JOIN household_items hi ON hi.item_id = sc.item_id
            WHERE hi.item_id = ?
              AND hi.household_id = ?
              AND hi.is_active = 1
            ORDER BY sc.checked_at DESC, sc.checkin_id DESC
            """, itemId, ownHouseholdId);

            // An empty result can mean no history, or an item not owned
            // by this household. Verify ownership to distinguish the cases.
            if (rows.isEmpty()) {
                Integer itemCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM household_items
                WHERE item_id = ?
                  AND household_id = ?
                  AND is_active = 1
                """, Integer.class, itemId, ownHouseholdId);

                if (itemCount == null || itemCount == 0) {
                    return error(HttpStatus.NOT_FOUND,
                            "Active inventory item not found in your household.");
                }
            }

            return ResponseEntity.ok(rows);

        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not load stock-check history.");
        }
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
            SELECT item_name, quantity_remaining, unit  
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

        createLowStockNotificationIfNeeded(
                account.id(),
                householdId,
                itemId,
                item.get("item_name").toString(),
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

    private void createStockOutNotificationIfNeeded(
            long userId,
            long householdId,
            long itemId,
            String itemName,
            BigDecimal previousQuantity,
            BigDecimal newQuantity) {

        boolean wasInStock =
                previousQuantity.compareTo(BigDecimal.ZERO) > 0;

        boolean isNowOutOfStock =
                newQuantity.compareTo(BigDecimal.ZERO) == 0;

        if (wasInStock && isNowOutOfStock) {
            String message = itemName + " is out of stock. Please replenish it.";

            jdbcTemplate.update("""
            INSERT INTO notifications (
                user_id,
                household_id,
                message,
                notification_type,
                is_read
            )
            VALUES (?, ?, ?, 'OUT_OF_STOCK', 0)
            """,
                    userId,
                    householdId,
                    message);
        }
    }

    private void createLowStockNotificationIfNeeded(
            long userId,
            long householdId,
            long itemId,
            String itemName,
            BigDecimal newQuantity) {

        // Zero stock has priority over low stock.
        if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            jdbcTemplate.update("""
            UPDATE household_items
            SET low_stock_notified = 0
            WHERE item_id = ?
              AND household_id = ?
            """, itemId, householdId);
            return;
        }

        Map<String, Object> item = jdbcTemplate.queryForMap("""
        SELECT low_stock_threshold, low_stock_notified
        FROM household_items
        WHERE item_id = ?
          AND household_id = ?
          AND is_active = 1
        FOR UPDATE
        """, itemId, householdId);

        Object thresholdValue = item.get("low_stock_threshold");

        Object notifiedValue = item.get("low_stock_notified");

        boolean alreadyNotified =
                notifiedValue instanceof Boolean
                        ? (Boolean) notifiedValue
                        : notifiedValue instanceof Number
                        && ((Number) notifiedValue).intValue() == 1;

        boolean belowQuantityThreshold = false;

        if (thresholdValue != null) {
            BigDecimal threshold =
                    new BigDecimal(thresholdValue.toString());

            belowQuantityThreshold =
                    newQuantity.compareTo(threshold) <= 0;
        }

        // The same prediction criteria as the dashboard endpoint:
        // consumption on at least 3 distinct dates spanning 3 days,
        // at least 4 observed calendar days, and positive daily usage.
        boolean predictedLowStock =
                isPredictedToRunOutWithinThreeDays(itemId, newQuantity);

        boolean isLowStock =
                belowQuantityThreshold || predictedLowStock;

        if (!isLowStock) {
            // Recovery: allow another notification if the item becomes
            // low again in a future stock change.
            jdbcTemplate.update("""
            UPDATE household_items
            SET low_stock_notified = 0
            WHERE item_id = ?
              AND household_id = ?
            """, itemId, householdId);
            return;
        }

        if (alreadyNotified) {
            return;
        }

        String message = itemName
                + " is running low. Please plan to replenish it.";

        jdbcTemplate.update("""
        INSERT INTO notifications (
            user_id,
            household_id,
            message,
            notification_type,
            is_read
        )
        VALUES (?, ?, ?, 'LOW_STOCK', 0)
        """, userId, householdId, message);

        jdbcTemplate.update("""
        UPDATE household_items
        SET low_stock_notified = 1
        WHERE item_id = ?
          AND household_id = ?
        """, itemId, householdId);
    }

    private boolean isPredictedToRunOutWithinThreeDays(
            long itemId,
            BigDecimal remaining) {

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        List<Map<String, Object>> transactions =
                jdbcTemplate.queryForList("""
                SELECT quantity, created_at
                FROM stock_transactions
                WHERE item_id = ?
                  AND transaction_type = 'CONSUME'
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY)
                ORDER BY created_at ASC
                """, itemId);

        List<LocalDate> consumptionDates = transactions.stream()
                .map(row -> {
                    Object value = row.get("created_at");

                    if (value instanceof Timestamp timestamp) {
                        return timestamp.toLocalDateTime().toLocalDate();
                    }

                    if (value instanceof LocalDateTime dateTime) {
                        return dateTime.toLocalDate();
                    }

                    if (value instanceof java.sql.Date date) {
                        return date.toLocalDate();
                    }

                    throw new IllegalStateException(
                            "Unexpected consumption timestamp type.");
                })
                .distinct()
                .sorted()
                .toList();

        if (consumptionDates.size() < 3) {
            return false;
        }

        LocalDate firstDate = consumptionDates.get(0);
        LocalDate lastDate =
                consumptionDates.get(consumptionDates.size() - 1);
        LocalDate today = LocalDate.now();

        long spanDays =
                java.time.temporal.ChronoUnit.DAYS.between(
                        firstDate, lastDate);

        if (spanDays < 3) {
            return false;
        }

        long observedDays =
                java.time.temporal.ChronoUnit.DAYS.between(
                        firstDate, today) + 1;

        if (observedDays < 4) {
            return false;
        }

        BigDecimal totalConsumed = transactions.stream()
                .map(row ->
                        new BigDecimal(row.get("quantity").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalConsumed.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal dailyUsage = totalConsumed.divide(
                BigDecimal.valueOf(observedDays),
                4,
                RoundingMode.HALF_UP);

        if (dailyUsage.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        long daysRemaining = remaining.divide(
                dailyUsage, 0, RoundingMode.DOWN).longValue();

        return daysRemaining <= 3;
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
            return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
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

        Map<String, Object> result = new HashMap<>();
        result.put("itemId", itemId);
        result.put("itemName", item.get("item_name"));
        result.put("quantityRemaining", remaining);
        result.put("unit", item.get("unit"));
        result.put("observationWindowDays", 14);

        // No prediction is needed when the item is already out of stock.
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("enoughData", true);
            result.put("consumptionRecords", 0);
            result.put("averageDailyConsumption", null);
            result.put("estimatedDaysRemaining", 0);
            result.put("predictedRefillDate", LocalDate.now().toString());
            result.put("stockStatus", "OUT_OF_STOCK");
            result.put("message", "This item is out of stock.");

            return ResponseEntity.ok(result);
        }

        // Only actual consumption counts as usage; replenishments are excluded.
        List<Map<String, Object>> transactions = jdbcTemplate.queryForList("""
        SELECT quantity, created_at
        FROM stock_transactions
        WHERE item_id = ?
          AND transaction_type = 'CONSUME'
          AND created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY)
        ORDER BY created_at ASC
        """, itemId);

        result.put("consumptionRecords", transactions.size());

        // Collect the calendar dates on which consumption was recorded.
        List<LocalDate> consumptionDates = transactions.stream()
                .map(row -> {
                    Object value = row.get("created_at");

                    if (value instanceof Timestamp timestamp) {
                        return timestamp.toLocalDateTime().toLocalDate();
                    }

                    if (value instanceof LocalDateTime dateTime) {
                        return dateTime.toLocalDate();
                    }

                    throw new IllegalStateException(
                            "Unexpected created_at value type.");
                })
                .distinct()
                .sorted()
                .toList();

        LocalDate today = LocalDate.now();

        boolean enoughData = false;
        long observedDays = 0;

        if (consumptionDates.size() >= 3) {
            LocalDate firstDate = consumptionDates.get(0);
            LocalDate lastDate = consumptionDates.get(
                    consumptionDates.size() - 1);

            long spanDays = java.time.temporal.ChronoUnit.DAYS.between(
                    firstDate, lastDate);

            // Require at least three distinct dates spanning three days.
            if (spanDays >= 3) {
                observedDays = java.time.temporal.ChronoUnit.DAYS.between(
                        firstDate, today) + 1;

                enoughData = observedDays >= 4;
            }
        }

        result.put("enoughData", enoughData);

        if (!enoughData) {
            result.put("averageDailyConsumption", null);
            result.put("estimatedDaysRemaining", null);
            result.put("predictedRefillDate", null);
            result.put("stockStatus", "INSUFFICIENT_DATA");
            result.put("message",
                    "Prediction pending: consumption must be recorded "
                            + "on at least three different dates spanning "
                            + "three or more days.");

            return ResponseEntity.ok(result);
        }

        BigDecimal totalConsumed = transactions.stream()
                .map(row -> new BigDecimal(row.get("quantity").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalConsumed.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("enoughData", false);
            result.put("averageDailyConsumption", null);
            result.put("estimatedDaysRemaining", null);
            result.put("predictedRefillDate", null);
            result.put("stockStatus", "INSUFFICIENT_DATA");
            result.put("message", "A positive consumption rate is required.");

            return ResponseEntity.ok(result);
        }

        // Average consumption per calendar day from the first recorded
        // consumption date through today, inclusive.
        BigDecimal dailyUsage = totalConsumed.divide(
                BigDecimal.valueOf(observedDays),
                4,
                RoundingMode.HALF_UP);

        if (dailyUsage.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("enoughData", false);
            result.put("averageDailyConsumption", null);
            result.put("estimatedDaysRemaining", null);
            result.put("predictedRefillDate", null);
            result.put("stockStatus", "INSUFFICIENT_DATA");
            result.put("message", "A positive consumption rate is required.");

            return ResponseEntity.ok(result);
        }

        long daysRemaining = remaining.divide(
                dailyUsage, 0, RoundingMode.DOWN).longValue();

        LocalDate refillDate = today.plusDays(daysRemaining);

        result.put("enoughData", true);
        result.put("averageDailyConsumption", dailyUsage);
        result.put("estimatedDaysRemaining", daysRemaining);
        result.put("predictedRefillDate", refillDate.toString());

        if (daysRemaining <= 3) {
            result.put("stockStatus", "LOW_STOCK");
        } else {
            result.put("stockStatus", "AVAILABLE");
        }

        result.put("message",
                "Prediction calculated from recorded consumption.");

        return ResponseEntity.ok(result);
    }
    private boolean validId(Long id) {
        return id != null && id > 0;
    }
}
