package com.grocerymate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "household_items")
public class HouseholdItems {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "household_id", nullable = false)
    private Long householdId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "quantity_remaining", nullable = false)
    private BigDecimal quantityRemaining;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "expected_duration_days", nullable = false)
    private Integer expectedDurationDays = 30;

    @Column(name = "reminder_days_before", nullable = false)
    private Integer reminderDaysBefore = 3;

    @Column(name = "planning_period", nullable = false)
    private String planningPeriod = "MONTHLY";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.isActive == null) this.isActive = true;
        if (this.expectedDurationDays == null) this.expectedDurationDays = 30;
        if (this.reminderDaysBefore == null) this.reminderDaysBefore = 3;
        if (this.planningPeriod == null) this.planningPeriod = "MONTHLY";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public HouseholdItems() {}

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public BigDecimal getQuantityRemaining() { return quantityRemaining; }
    public void setQuantityRemaining(BigDecimal quantityRemaining) { this.quantityRemaining = quantityRemaining; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getExpectedDurationDays() { return expectedDurationDays; }
    public void setExpectedDurationDays(Integer expectedDurationDays) { this.expectedDurationDays = expectedDurationDays; }

    public Integer getReminderDaysBefore() { return reminderDaysBefore; }
    public void setReminderDaysBefore(Integer reminderDaysBefore) { this.reminderDaysBefore = reminderDaysBefore; }

    public String getPlanningPeriod() { return planningPeriod; }
    public void setPlanningPeriod(String planningPeriod) { this.planningPeriod = planningPeriod; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}




//package com.grocerymate.entity;
//
//import jakarta.persistence.*;
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//
//@Entity
//@Table(name = "household_items")
//public class HouseholdItems {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    @Column(name = "item_id", nullable = false)
//    private Long itemId;
//
//    @Column(name = "household_id", nullable = false)
//    private Long householdId;
//
//    @Column(name = "item_name", nullable = false)
//    private String itemName;
//
//    @Column(name = "category", nullable = false)
//    private String category;
//
//    @Column(name = "quantity_remaining", nullable = false)
//    private java.math.BigDecimal quantityRemaining;
//
//    @Column(name = "unit", nullable = false)
//    private String unit;
//
//    @Column(name = "purchase_date")
//    private java.time.LocalDate purchaseDate;
//
//    @Column(name = "last_purchase_price")
//    private java.math.BigDecimal lastPurchasePrice;
//
//    @Column(name = "expected_duration_days", nullable = false)
//    private Integer expectedDurationDays;
//
//    @Column(name = "estimated_refill_date")
//    private java.time.LocalDate estimatedRefillDate;
//
//    @Column(name = "reminder_days_before", nullable = false)
//    private Integer reminderDaysBefore;
//
//    @Column(name = "planning_period", nullable = false)
//    private String planningPeriod;
//
//    @Column(name = "next_stock_check_at")
//    private java.time.LocalDateTime nextStockCheckAt;
//
//    @Column(name = "is_active", nullable = false)
//    private Boolean isActive;
//
//    @Column(name = "created_at", nullable = false)
//    private java.time.LocalDateTime createdAt;
//
//    @Column(name = "updated_at", nullable = false)
//    private java.time.LocalDateTime updatedAt;
//
//    @Column(name = "ON")
//    private String on;
//
//    public HouseholdItems() {}
//
//    public Long getItemId() { return itemId; }
//    public void setItemId(Long itemId) { this.itemId = itemId; }
//
//    public Long getHouseholdId() { return householdId; }
//    public void setHouseholdId(Long householdId) { this.householdId = householdId; }
//
//    public String getItemName() { return itemName; }
//    public void setItemName(String itemName) { this.itemName = itemName; }
//
//    public String getCategory() { return category; }
//    public void setCategory(String category) { this.category = category; }
//
//    public java.math.BigDecimal getQuantityRemaining() { return quantityRemaining; }
//    public void setQuantityRemaining(java.math.BigDecimal quantityRemaining) { this.quantityRemaining = quantityRemaining; }
//
//    public String getUnit() { return unit; }
//    public void setUnit(String unit) { this.unit = unit; }
//
//    public java.time.LocalDate getPurchaseDate() { return purchaseDate; }
//    public void setPurchaseDate(java.time.LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
//
//    public java.math.BigDecimal getLastPurchasePrice() { return lastPurchasePrice; }
//    public void setLastPurchasePrice(java.math.BigDecimal lastPurchasePrice) { this.lastPurchasePrice = lastPurchasePrice; }
//
//    public Integer getExpectedDurationDays() { return expectedDurationDays; }
//    public void setExpectedDurationDays(Integer expectedDurationDays) { this.expectedDurationDays = expectedDurationDays; }
//
//    public java.time.LocalDate getEstimatedRefillDate() { return estimatedRefillDate; }
//    public void setEstimatedRefillDate(java.time.LocalDate estimatedRefillDate) { this.estimatedRefillDate = estimatedRefillDate; }
//
//    public Integer getReminderDaysBefore() { return reminderDaysBefore; }
//    public void setReminderDaysBefore(Integer reminderDaysBefore) { this.reminderDaysBefore = reminderDaysBefore; }
//
//    public String getPlanningPeriod() { return planningPeriod; }
//    public void setPlanningPeriod(String planningPeriod) { this.planningPeriod = planningPeriod; }
//
//    public java.time.LocalDateTime getNextStockCheckAt() { return nextStockCheckAt; }
//    public void setNextStockCheckAt(java.time.LocalDateTime nextStockCheckAt) { this.nextStockCheckAt = nextStockCheckAt; }
//
//    public Boolean getIsActive() { return isActive; }
//    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
//
//    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
//    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
//
//    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
//    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
//
//    public String getOn() { return on; }
//    public void setOn(String on) { this.on = on; }
//
//}