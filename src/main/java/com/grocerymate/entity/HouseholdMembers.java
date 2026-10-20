package com.grocerymate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "household_members")
public class HouseholdMembers {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "household_member_id", nullable = false)
    private Long householdMemberId;

    @Column(name = "household_id", nullable = false)
    private Long householdId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "member_role", nullable = false)
    private String memberRole;

    @Column(name = "joined_at", nullable = false)
    private java.time.LocalDateTime joinedAt;

    @Column(name = "ON")
    private String on;


    public HouseholdMembers() {}

    public Long getHouseholdMemberId() { return householdMemberId; }
    public void setHouseholdMemberId(Long householdMemberId) { this.householdMemberId = householdMemberId; }

    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getMemberRole() { return memberRole; }
    public void setMemberRole(String memberRole) { this.memberRole = memberRole; }

    public java.time.LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(java.time.LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    public String getOn() { return on; }
    public void setOn(String on) { this.on = on; }



}