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

    @JsonProperty("quantity_purchased")
    private BigDecimal quantityPurchased;

    @JsonProperty("unit")
    private String unit;

    @JsonProperty("purchase_date")
    private LocalDate purchaseDate;

    @JsonProperty("total_purchase_cost")
    private BigDecimal totalPurchaseCost;

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

    public BigDecimal getQuantityPurchased() {
        return quantityPurchased;
    }

    public void setQuantityPurchased(BigDecimal quantityPurchased) {
        this.quantityPurchased = quantityPurchased;
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

    public BigDecimal getTotalPurchaseCost() {
        return totalPurchaseCost;
    }

    public void setTotalPurchaseCost(BigDecimal totalPurchaseCost) {
        this.totalPurchaseCost = totalPurchaseCost;
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


    @JsonProperty("low_stock_threshold")
    private BigDecimal lowStockThreshold;

    public BigDecimal getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(BigDecimal lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }



}