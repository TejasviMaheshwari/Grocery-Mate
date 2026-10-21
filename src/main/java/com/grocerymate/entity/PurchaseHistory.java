package com.grocerymate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_history")
public class PurchaseHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "purchased_by")
    private Long purchasedBy;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "quantity_purchased", nullable = false)
    private java.math.BigDecimal quantityPurchased;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "unit_price")
    private java.math.BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false)
    private java.math.BigDecimal totalAmount;

    @Column(name = "purchase_date", nullable = false)
    private java.time.LocalDate purchaseDate;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;

//    @Column(name = "ON")
//    private String on;


    public PurchaseHistory() {
    }

    public Long getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(Long purchaseId) {
        this.purchaseId = purchaseId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getPurchasedBy() {
        return purchasedBy;
    }

    public void setPurchasedBy(Long purchasedBy) {
        this.purchasedBy = purchasedBy;
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public java.math.BigDecimal getQuantityPurchased() {
        return quantityPurchased;
    }

    public void setQuantityPurchased(java.math.BigDecimal quantityPurchased) {
        this.quantityPurchased = quantityPurchased;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public java.math.BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(java.math.BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public java.math.BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(java.math.BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public java.time.LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(java.time.LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

//    public String getOn() {return on;}
//    public void setOn(String on) {this.on = on;}
}
