package com.grocerymate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_checkins")
public class StockCheckins {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "checkin_id", nullable = false)
    private Long checkinId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "quantity_remaining", nullable = false)
    private java.math.BigDecimal quantityRemaining;

    @Column(name = "will_last_until_date", nullable = false)
    private Boolean willLastUntilDate;

    @Column(name = "expected_extra_days")
    private Integer expectedExtraDays;

    @Column(name = "checked_at", nullable = false)
    private java.time.LocalDateTime checkedAt;

    @Column(name = "next_checkin_at")
    private java.time.LocalDateTime nextCheckinAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "ON")
    private String on;


    public StockCheckins() {}

    public Long getCheckinId() { return checkinId; }
    public void setCheckinId(Long checkinId) { this.checkinId = checkinId; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public java.math.BigDecimal getQuantityRemaining() { return quantityRemaining; }
    public void setQuantityRemaining(java.math.BigDecimal quantityRemaining) { this.quantityRemaining = quantityRemaining; }

    public Boolean getWillLastUntilDate() { return willLastUntilDate; }
    public void setWillLastUntilDate(Boolean willLastUntilDate) { this.willLastUntilDate = willLastUntilDate; }

    public Integer getExpectedExtraDays() { return expectedExtraDays; }
    public void setExpectedExtraDays(Integer expectedExtraDays) { this.expectedExtraDays = expectedExtraDays; }

    public java.time.LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(java.time.LocalDateTime checkedAt) { this.checkedAt = checkedAt; }

    public java.time.LocalDateTime getNextCheckinAt() { return nextCheckinAt; }
    public void setNextCheckinAt(java.time.LocalDateTime nextCheckinAt) { this.nextCheckinAt = nextCheckinAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getOn() { return on; }
    public void setOn(String on) { this.on = on; }

}