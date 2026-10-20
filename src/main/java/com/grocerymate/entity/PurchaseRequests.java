package com.grocerymate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_requests")
public class PurchaseRequests {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "household_id", nullable = false)
    private Long householdId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_item_name", nullable = false)
    private String requestedItemName;

    @Column(name = "requested_quantity", nullable = false)
    private java.math.BigDecimal requestedQuantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "assigned_buyer_id")
    private Long assignedBuyerId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private java.time.LocalDateTime requestedAt;

    @Column(name = "claimed_at")
    private java.time.LocalDateTime claimedAt;

    @Column(name = "completed_at")
    private java.time.LocalDateTime completedAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "ON")
    private String on;


    public PurchaseRequests() {}

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }

    public String getRequestedItemName() { return requestedItemName; }
    public void setRequestedItemName(String requestedItemName) { this.requestedItemName = requestedItemName; }

    public java.math.BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(java.math.BigDecimal requestedQuantity) { this.requestedQuantity = requestedQuantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Long getAssignedBuyerId() { return assignedBuyerId; }
    public void setAssignedBuyerId(Long assignedBuyerId) { this.assignedBuyerId = assignedBuyerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.time.LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(java.time.LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public java.time.LocalDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(java.time.LocalDateTime claimedAt) { this.claimedAt = claimedAt; }

    public java.time.LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(java.time.LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getOn() { return on; }
    public void setOn(String on) { this.on = on; }

}