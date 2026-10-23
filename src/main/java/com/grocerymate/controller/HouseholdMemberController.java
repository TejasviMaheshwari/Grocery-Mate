
package com.grocerymate.controller;

import com.grocerymate.security.AuthenticatedAccount;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/household/members")
public class HouseholdMemberController {

    private final JdbcTemplate jdbcTemplate;

    public HouseholdMemberController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<?> getHouseholdMembers(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please log in again."));
        }

        if (!"USER".equalsIgnoreCase(account.role())
                || account.householdId() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "You do not belong to a household."));
        }

        long userId = account.id();
        long householdId = account.householdId();

        Integer membershipCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM household_members
            WHERE household_id = ?
              AND user_id = ?
            """, Integer.class, householdId, userId);

        if (membershipCount == null || membershipCount == 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "You are not a member of this household."));
        }

        List<Map<String, Object>> members = jdbcTemplate.queryForList("""
            SELECT
                u.user_id AS userId,
                u.full_name AS fullName,
                u.email AS email,
                hm.member_role AS role,
                hm.joined_at AS joinedAt
            FROM household_members hm
            JOIN users u ON u.user_id = hm.user_id
            WHERE hm.household_id = ?
            ORDER BY
                CASE WHEN hm.member_role = 'OWNER' THEN 0 ELSE 1 END,
                hm.joined_at ASC
            """, householdId);

        return ResponseEntity.ok(Map.of(
                "householdId", householdId,
                "members", members
        ));
    }
}
