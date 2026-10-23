package com.grocerymate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vendor_orders")
public class VendorOrders {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vendor_order_id", nullable = false)
    private Long vendorOrderId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "household_id", nullable = false)
    private Long householdId;

    @Column(name = "purchase_request_id")
    private Long purchaseRequestId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "quantity", nullable = false)
    private java.math.BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "vendor_product_id")
    private Long vendorProductId;

    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount")
    private java.math.BigDecimal totalAmount;

    @Column(name = "order_status", nullable = false)
    private String orderStatus;

    @Column(name = "ordered_at", nullable = false)
    private java.time.LocalDateTime orderedAt;

    @Column(name = "expected_delivery_at")
    private java.time.LocalDateTime expectedDeliveryAt;

    @Column(name = "delivered_at")
    private java.time.LocalDateTime deliveredAt;

    @Column(name = "notes")
    private String notes;


    public VendorOrders() {}

    public Long getVendorOrderId() { return vendorOrderId; }
    public void setVendorOrderId(Long vendorOrderId) { this.vendorOrderId = vendorOrderId; }

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }

    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }

    public Long getPurchaseRequestId() { return purchaseRequestId; }
    public void setPurchaseRequestId(Long purchaseRequestId) { this.purchaseRequestId = purchaseRequestId; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public java.math.BigDecimal getQuantity() { return quantity; }
    public void setQuantity(java.math.BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public java.math.BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(java.math.BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public java.time.LocalDateTime getOrderedAt() { return orderedAt; }
    public void setOrderedAt(java.time.LocalDateTime orderedAt) { this.orderedAt = orderedAt; }

    public java.time.LocalDateTime getExpectedDeliveryAt() { return expectedDeliveryAt; }
    public void setExpectedDeliveryAt(java.time.LocalDateTime expectedDeliveryAt) { this.expectedDeliveryAt = expectedDeliveryAt; }

    public java.time.LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(java.time.LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getVendorProductId() {return vendorProductId;}
    public void setVendorProductId(Long vendorProductId) {this.vendorProductId = vendorProductId;    }

    public BigDecimal getUnitPrice() {return unitPrice;}
    public void setUnitPrice(BigDecimal unitPrice) {this.unitPrice = unitPrice;}
}