package com.grocerymate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reminders")
public class Reminders {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reminder_id", nullable = false)
    private Long reminderId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "household_id", nullable = false)
    private Long householdId;

    @Column(name = "scheduled_at", nullable = false)
    private java.time.LocalDateTime scheduledAt;

    @Column(name = "reminder_type", nullable = false)
    private String reminderType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "sent_at")
    private java.time.LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "ON")
    private String on;


    public Reminders() {}

    public Long getReminderId() { return reminderId; }
    public void setReminderId(Long reminderId) { this.reminderId = reminderId; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }

    public java.time.LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(java.time.LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getReminderType() { return reminderType; }
    public void setReminderType(String reminderType) { this.reminderType = reminderType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.time.LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(java.time.LocalDateTime sentAt) { this.sentAt = sentAt; }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getOn() { return on; }
    public void setOn(String on) { this.on = on; }


}