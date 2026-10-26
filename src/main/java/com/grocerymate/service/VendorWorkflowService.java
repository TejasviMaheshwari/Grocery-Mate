package com.grocerymate.service;
import java.time.LocalDate;
import java.util.Map;
import com.grocerymate.entity.PurchaseRequest;
import com.grocerymate.entity.VendorOrders;
import com.grocerymate.entity.VendorProduct;
import com.grocerymate.repository.PurchaseRequestRepository;
import com.grocerymate.repository.VendorOrdersRepository;
import com.grocerymate.repository.VendorProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VendorWorkflowService {

    private final PurchaseRequestRepository requestRepository;
    private final VendorOrdersRepository orderRepository;
    private final VendorProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    public VendorWorkflowService(
            PurchaseRequestRepository requestRepository,
            VendorOrdersRepository orderRepository,
            VendorProductRepository productRepository,
            JdbcTemplate jdbcTemplate) {
        this.requestRepository = requestRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // --------------------------------------------------
    // 1. Get open requests assigned to this vendor
    // --------------------------------------------------
//    @Transactional(readOnly = true)
//    public List<PurchaseRequest> getOpenRequests(Long vendorId) {
//        return requestRepository
//                .findByAssignedVendorIdAndStatusOrderByRequestedAtDesc(
//                        vendorId, "OPEN");
//    }

    @Transactional(readOnly = true)
    public List<PurchaseRequest> getOpenRequests(Long vendorId) {
        System.out.println("DEBUG getOpenRequests vendorId = " + vendorId);

        List<PurchaseRequest> requests =
                requestRepository
                        .findByAssignedVendorIdAndStatusOrderByRequestedAtDesc(
                                vendorId, "OPEN");

        System.out.println("DEBUG open requests found = " + requests.size());

        for (PurchaseRequest request : requests) {
            System.out.println(
                    "DEBUG requestId=" + request.getRequestId()
                            + ", assignedVendorId=" + request.getAssignedVendorId()
                            + ", status=" + request.getStatus());
        }

        return requests;
    }


    // --------------------------------------------------
    // 2. Get all orders belonging to this vendor
    // --------------------------------------------------
    @Transactional(readOnly = true)
    public List<VendorOrders> getVendorOrders(Long vendorId) {
        return orderRepository
                .findByVendorIdOrderByOrderedAtDesc(vendorId);
    }

    // --------------------------------------------------
    // 3. Accept a request and create its vendor order
    // --------------------------------------------------
    @Transactional
    public VendorOrders acceptRequest(
            Long requestId,
            Long vendorId,
            Long productId) {

        if (requestId == null || vendorId == null || productId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request ID, vendor ID and product ID are required.");
        }

        // Verify that this request belongs to this vendor.
        PurchaseRequest request = requestRepository
                .findByRequestIdAndAssignedVendorId(requestId, vendorId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase request not found for this vendor."));

        if (!"OPEN".equalsIgnoreCase(request.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This purchase request is no longer open.");
        }

        // Verify product ownership.
        VendorProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vendor product not found."));

        if (!vendorId.equals(product.getVendorId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only accept requests using your own products.");
        }

        if (request.getItemId() != null) {
            Long requestedCatalogId = jdbcTemplate.query(
                    """
                    SELECT catalog_item_id
                    FROM household_items
                    WHERE item_id = ?
                    """,
                    rs -> rs.next() ? rs.getLong("catalog_item_id") : null,
                    request.getItemId()
            );

            if (requestedCatalogId == null
                    || !requestedCatalogId.equals(product.getCatalogItemId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The selected product does not match the requested item."
                );
            }
        }

        // Verify that the product can be ordered.
        if (!Boolean.TRUE.equals(product.getAvailable())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This product is currently unavailable.");
        }

        BigDecimal quantity = request.getRequestedQuantity();
        BigDecimal unitPrice = product.getPrice();

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested quantity must be greater than zero.");
        }

        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected product has an invalid price.");
        }

        if (product.getStockQuantity() == null
                || quantity.compareTo(
                BigDecimal.valueOf(product.getStockQuantity())) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient product stock for this request.");
        }

        String requestedUnit = request.getUnit();
        String productUnit = product.getUnit();

        if (requestedUnit == null || productUnit == null
                || !requestedUnit.trim().equalsIgnoreCase(
                productUnit.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The requested unit does not match the product unit.");
        }

        // Atomically claim the request. Only one concurrent acceptance
        // can change OPEN to ACCEPTED.
        int claimed = requestRepository.claimOpenRequest(
                requestId,
                vendorId,
                LocalDateTime.now());

        if (claimed != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This request has already been accepted or changed.");
        }

        int stockUpdated = jdbcTemplate.update("""
            UPDATE vendor_products
            SET stock_quantity = stock_quantity - ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE product_id = ?
              AND vendor_id = ?
              AND is_available = 1
              AND stock_quantity >= ?
              AND LOWER(TRIM(unit)) = LOWER(TRIM(?))
            """,
            request.getRequestedQuantity(),
            productId,
            vendorId,
            request.getRequestedQuantity(),
            request.getUnit()
        );

        if (stockUpdated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient stock or product is no longer available."
            );
        }
        // Create the order in the same transaction.
        VendorOrders order = new VendorOrders();

        order.setVendorId(vendorId);
        order.setHouseholdId(request.getHouseholdId());
        order.setPurchaseRequestId(request.getRequestId());
        order.setItemId(request.getItemId());

        order.setVendorProductId(product.getProductId());
        order.setQuantity(quantity);
        order.setUnit(productUnit);
        order.setUnitPrice(unitPrice);

        order.setTotalAmount(
                unitPrice.multiply(quantity)
                        .setScale(2, RoundingMode.HALF_UP));


        order.setOrderStatus("PLACED");
        order.setOrderedAt(LocalDateTime.now());
        order.setNotes("Created from purchase request #" + requestId);

        VendorOrders savedOrder = orderRepository.save(order);

        recordOrderStatus(
                savedOrder.getVendorOrderId(),
                "PLACED",
                "Order placed and sent to the vendor.",
                savedOrder.getOrderedAt()
        );

        return savedOrder;

    }

    // --------------------------------------------------
    // 4. Update an order's status
    // --------------------------------------------------
    @Transactional
    public VendorOrders updateOrderStatus(
            Long orderId,
            Long vendorId,
            String newStatus) {

        if (newStatus == null || newStatus.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order status is required.");
        }

        String status = newStatus.trim().toUpperCase();

        if (!List.of("PLACED", "PROCESSING", "SHIPPED", "DELIVERED",
                "CANCELLED").contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid order status.");
        }

        VendorOrders order = orderRepository
                .findByVendorOrderIdAndVendorIdForUpdate(orderId, vendorId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found for this vendor."));

        String currentStatus = order.getOrderStatus();

        if ("DELIVERED".equalsIgnoreCase(currentStatus)
                || "CANCELLED".equalsIgnoreCase(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A delivered or cancelled order cannot be changed.");
        }

        if ("CANCELLED".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order cancellation requires a separate stock and request "
                            + "reconciliation workflow.");
        }


        if ("DELIVERED".equals(status)) {
            if (!"SHIPPED".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Only shipped orders can be marked as delivered.");
            }

            if (order.getVendorProductId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The order is missing its vendor product reference.");
            }

            VendorProduct product = productRepository
                    .findById(order.getVendorProductId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "The vendor product for this order no longer exists."));

            PurchaseRequest request = null;

            if (order.getPurchaseRequestId() != null) {
                request = requestRepository
                        .findById(order.getPurchaseRequestId())
                        .orElse(null);
            }

            recordDeliveredPurchase(order, product, request);

            order.setDeliveredAt(LocalDateTime.now());
        }



        order.setOrderStatus(status);

        VendorOrders savedOrder = orderRepository.save(order);

        if (!status.equalsIgnoreCase(currentStatus)) {
            String description = switch (status) {
                case "PLACED" ->
                        "Your order has been placed.";
                case "PROCESSING" ->
                        "The vendor is preparing your order.";
                case "SHIPPED" ->
                        "Your order is on its way.";
                case "DELIVERED" ->
                        "Your order has been delivered.";
                case "CANCELLED" ->
                        "Your order was cancelled.";
                default ->
                        "Your order status has been updated.";
            };

            recordOrderStatus(
                    savedOrder.getVendorOrderId(),
                    status,
                    description,
                    LocalDateTime.now()
            );
        }


        // Keep the related request's status in sync on delivery.
        if ("DELIVERED".equals(status)
                && order.getPurchaseRequestId() != null) {

            PurchaseRequest request = requestRepository
                    .findById(order.getPurchaseRequestId())
                    .orElse(null);

            if (request != null
                    && "ACCEPTED".equalsIgnoreCase(request.getStatus())) {
                request.setStatus("COMPLETED");
                request.setCompletedAt(LocalDateTime.now());
                requestRepository.save(request);
            }
        }

        return savedOrder;
    }


    private void recordDeliveredPurchase(
            VendorOrders order,
            VendorProduct product,
            PurchaseRequest request) {

        if (order.getQuantity() == null
                || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid delivered quantity.");
        }

        if (product.getCatalogItemId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The vendor product has no catalog item.");
        }

        BigDecimal quantity = order.getQuantity();
        BigDecimal unitPrice = order.getUnitPrice();

        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid order unit price.");
        }

        BigDecimal total = order.getTotalAmount() != null
                ? order.getTotalAmount()
                : unitPrice.multiply(quantity)
                .setScale(2, RoundingMode.HALF_UP);

        Long householdId = order.getHouseholdId();
        Long catalogItemId = product.getCatalogItemId();
        String unit = order.getUnit();

        if (householdId == null || unit == null || unit.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Household or order unit is missing.");
        }

        // Find the existing inventory item for this household and catalog item.
        // The household/catalog combination is unique in your database.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        SELECT item_id, quantity_remaining, unit
        FROM household_items
        WHERE household_id = ?
          AND catalog_item_id = ?
        FOR UPDATE
        """,
                householdId,
                catalogItemId);

        long itemId;
        BigDecimal previousQuantity;
        BigDecimal newQuantity;

        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);

            itemId = ((Number) row.get("item_id")).longValue();

            String existingUnit = (String) row.get("unit");

            // Validate units BEFORE changing inventory.
            if (existingUnit == null
                    || !existingUnit.trim().equalsIgnoreCase(unit.trim())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The household item exists with a different unit. "
                                + "Reconcile the units before recording delivery.");
            }

            previousQuantity = (BigDecimal) row.get("quantity_remaining");

            if (previousQuantity == null) {
                previousQuantity = BigDecimal.ZERO;
            }

            newQuantity = previousQuantity.add(quantity);

            jdbcTemplate.update("""
            UPDATE household_items
            SET quantity_remaining = ?,
                purchase_date = ?,
                last_purchase_price = ?,
                is_active = 1,
                low_stock_notified = 0
            WHERE item_id = ?
              AND household_id = ?
            """,
                    newQuantity,
                    LocalDate.now(),
                    unitPrice,
                    itemId,
                    householdId);

        } else {
            previousQuantity = BigDecimal.ZERO;
            newQuantity = quantity;

            String itemName = request != null
                    ? request.getRequestedItemName()
                    : product.getProductName();

            if (itemName == null || itemName.isBlank()) {
                itemName = product.getProductName();
            }

            jdbcTemplate.update("""
            INSERT INTO household_items (
                household_id,
                catalog_item_id,
                item_name,
                quantity_remaining,
                unit,
                purchase_date,
                last_purchase_price
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
                    householdId,
                    catalogItemId,
                    itemName,
                    newQuantity,
                    unit,
                    LocalDate.now(),
                    unitPrice);

            Long generatedItemId = jdbcTemplate.queryForObject(
                    "SELECT LAST_INSERT_ID()",
                    Long.class);

            if (generatedItemId == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not retrieve the created inventory item.");
            }

            itemId = generatedItemId;
        }

        // Record the purchase for household purchase history and spending reports.
        Long purchasedBy = request != null
                ? request.getRequestedBy()
                : null;

        jdbcTemplate.update("""
        INSERT INTO purchase_history (
            item_id,
            purchased_by,
            vendor_id,
            quantity_purchased,
            unit,
            unit_price,
            total_amount,
            purchase_date,
            notes
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
                itemId,
                purchasedBy,
                order.getVendorId(),
                quantity,
                unit,
                unitPrice,
                total,
                LocalDate.now(),
                "Delivered vendor order #" + order.getVendorOrderId());

        // Record the inventory increase in the existing stock transaction table.
        jdbcTemplate.update("""
        INSERT INTO stock_transactions (
            item_id,
            user_id,
            transaction_type,
            quantity,
            previous_quantity,
            new_quantity,
            notes
        )
        VALUES (?, ?, 'REPLENISH', ?, ?, ?, ?)
        """,
                itemId,
                purchasedBy,
                quantity,
                previousQuantity,
                newQuantity,
                "Vendor order #" + order.getVendorOrderId() + " delivered");
    }

    // --------------------------------------------------
    // Save a status event for the user's order tracker
    // --------------------------------------------------
    private void recordOrderStatus(
            Long orderId,
            String status,
            String description,
            LocalDateTime createdAt) {

        jdbcTemplate.update("""
            INSERT INTO vendor_order_status_history
                (vendor_order_id, status, description, created_at)
            VALUES (?, ?, ?, ?)
            """,
                orderId,
                status,
                description,
                createdAt != null ? createdAt : LocalDateTime.now());
    }


}