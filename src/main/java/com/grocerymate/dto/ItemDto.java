package com.grocerymate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ItemDto {

    @JsonProperty("household_id")
    private Long householdId;

    @JsonProperty("item_name")
    private String itemName;

    @JsonProperty("quantity_remaining")
    private BigDecimal quantityRemaining;

    @JsonProperty("unit")
    private String unit;

    @JsonProperty("purchase_date")
    private LocalDate purchaseDate;

    @JsonProperty("last_purchase_price")
    private BigDecimal lastPurchasePrice;

    @JsonProperty("expected_duration_days")
    private Integer expectedDurationDays = 30;

    @JsonProperty("estimated_refill_date")
    private LocalDate estimatedRefillDate;

    @JsonProperty("reminder_days_before")
    private Integer reminderDaysBefore = 3;

    @JsonProperty("next_stock_check_at")
    private LocalDateTime nextStockCheckAt;

    public ItemDto() {}

    public Long getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(Long householdId) {
        this.householdId = householdId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public BigDecimal getQuantityRemaining() {
        return quantityRemaining;
    }

    public void setQuantityRemaining(BigDecimal quantityRemaining) {
        this.quantityRemaining = quantityRemaining;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getLastPurchasePrice() {
        return lastPurchasePrice;
    }

    public void setLastPurchasePrice(BigDecimal lastPurchasePrice) {
        this.lastPurchasePrice = lastPurchasePrice;
    }

    public Integer getExpectedDurationDays() {
        return expectedDurationDays;
    }

    public void setExpectedDurationDays(Integer expectedDurationDays) {
        this.expectedDurationDays = expectedDurationDays;
    }

    public LocalDate getEstimatedRefillDate() {
        return estimatedRefillDate;
    }

    public void setEstimatedRefillDate(LocalDate estimatedRefillDate) {
        this.estimatedRefillDate = estimatedRefillDate;
    }

    public Integer getReminderDaysBefore() {
        return reminderDaysBefore;
    }

    public void setReminderDaysBefore(Integer reminderDaysBefore) {
        this.reminderDaysBefore = reminderDaysBefore;
    }

    public LocalDateTime getNextStockCheckAt() {
        return nextStockCheckAt;
    }

    public void setNextStockCheckAt(LocalDateTime nextStockCheckAt) {
        this.nextStockCheckAt = nextStockCheckAt;
    }
}//
//package com.grocerymate.dto;
//
//import com.fasterxml.jackson.annotation.JsonProperty;
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//
//public class ItemDto {
//
//    @JsonProperty("item_name")
//    private String itemName;
//
//    @JsonProperty("quantity_remaining")
//    private BigDecimal quantityRemaining;
//
//    @JsonProperty("unit")
//    private String unit;
//
//    @JsonProperty("purchase_date")
//    private LocalDate purchaseDate;
//
//    @JsonProperty("last_purchase_price")
//    private BigDecimal lastPurchasePrice;
//
//    @JsonProperty("expected_duration_days")
//    private Integer expectedDurationDays = 30;
//
//    @JsonProperty("estimated_refill_date")
//    private LocalDate estimatedRefillDate;
//
//    @JsonProperty("reminder_days_before")
//    private Integer reminderDaysBefore = 3;
//
//    @JsonProperty("next_stock_check_at")
//    private LocalDateTime nextStockCheckAt;
//
//    public ItemDto() {}
//
//    public String getItemName() {
//        return itemName;
//    }
//
//    public void setItemName(String itemName) {
//        this.itemName = itemName;
//    }
//
//    public BigDecimal getQuantityRemaining() {
//        return quantityRemaining;
//    }
//
//    public void setQuantityRemaining(BigDecimal quantityRemaining) {
//        this.quantityRemaining = quantityRemaining;
//    }
//
//    public String getUnit() {
//        return unit;
//    }
//
//    public void setUnit(String unit) {
//        this.unit = unit;
//    }
//
//    public LocalDate getPurchaseDate() {
//        return purchaseDate;
//    }
//
//    public void setPurchaseDate(LocalDate purchaseDate) {
//        this.purchaseDate = purchaseDate;
//    }
//
//    public BigDecimal getLastPurchasePrice() {
//        return lastPurchasePrice;
//    }
//
//    public void setLastPurchasePrice(BigDecimal lastPurchasePrice) {
//        this.lastPurchasePrice = lastPurchasePrice;
//    }
//
//    public Integer getExpectedDurationDays() {
//        return expectedDurationDays;
//    }
//
//    public void setExpectedDurationDays(Integer expectedDurationDays) {
//        this.expectedDurationDays = expectedDurationDays;
//    }
//
//    public LocalDate getEstimatedRefillDate() {
//        return estimatedRefillDate;
//    }
//
//    public void setEstimatedRefillDate(LocalDate estimatedRefillDate) {
//        this.estimatedRefillDate = estimatedRefillDate;
//    }
//
//    public Integer getReminderDaysBefore() {
//        return reminderDaysBefore;
//    }
//
//    public void setReminderDaysBefore(Integer reminderDaysBefore) {
//        this.reminderDaysBefore = reminderDaysBefore;
//    }
//
//    public LocalDateTime getNextStockCheckAt() {
//        return nextStockCheckAt;
//    }
//
//    public void setNextStockCheckAt(LocalDateTime nextStockCheckAt) {
//        this.nextStockCheckAt = nextStockCheckAt;
//    }
//}
