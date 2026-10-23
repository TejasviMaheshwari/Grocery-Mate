
package com.grocerymate.controller;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.grocerymate.security.AuthenticatedAccount;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/household/invitations")
public class HouseholdInvitationController {

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public HouseholdInvitationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // POST /api/household/invitations
    // Create a single-use invitation that expires in 24 hours.


    @PostMapping
    @Transactional
    public ResponseEntity<?> createInvitation(Authentication authentication) {

        AuthenticatedAccount account = getAccount(authentication);

        if (account == null) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Please log in again.");
        }

        if (!"USER".equalsIgnoreCase(account.role())
                || account.householdId() == null) {
            return error(HttpStatus.FORBIDDEN,
                    "Only a household owner can create invitations.");
        }

        long householdId = account.householdId();
        long userId = account.id();

        // Lock the household row so concurrent invitation requests
        // for this household are processed one at a time.
        List<Map<String, Object>> households = jdbcTemplate.queryForList("""
        SELECT household_id
        FROM households
        WHERE household_id = ?
          AND head_user_id = ?
        FOR UPDATE
        """, householdId, userId);

        if (households.isEmpty()) {
            return error(HttpStatus.FORBIDDEN,
                    "Only the household owner can create invitations.");
        }

        // Verify that the authenticated user is also recorded as OWNER.
        Integer ownerCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM household_members
        WHERE household_id = ?
          AND user_id = ?
          AND member_role = 'OWNER'
        """, Integer.class, householdId, userId);

        if (ownerCount == null || ownerCount == 0) {
            return error(HttpStatus.FORBIDDEN,
                    "Only the household owner can create invitations.");
        }

        // Deactivate previous unused invitations for this household.
        // Keep the records for auditing; do not delete them.
        jdbcTemplate.update("""
        UPDATE household_invitations
        SET is_active = 0
        WHERE household_id = ?
          AND is_active = 1
          AND use_count < max_uses
        """, householdId);

        // Generate a code that does not already exist.
        String code = null;

        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = generateCode();

            Integer codeCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM household_invitations
            WHERE invitation_code = ?
            """, Integer.class, candidate);

            if (codeCount != null && codeCount == 0) {
                code = candidate;
                break;
            }
        }

        if (code == null) {
            throw new IllegalStateException(
                    "Could not generate a unique invitation code.");
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

        jdbcTemplate.update("""
        INSERT INTO household_invitations (
            household_id,
            invitation_code,
            created_by,
            expires_at,
            max_uses,
            use_count,
            is_active
        )
        VALUES (?, ?, ?, ?, 1, 0, 1)
        """,
                householdId,
                code,
                userId,
                expiresAt
        );

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Invitation created successfully.");
        result.put("invitationCode", code);
        result.put("householdId", householdId);
        result.put("expiresAt", expiresAt.toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }


    // POST /api/household/invitations/accept
    // Request body: {"invitationCode":"YOUR_CODE"}


    @PostMapping("/accept")
    @Transactional
    public ResponseEntity<?> acceptInvitation(
            @RequestBody Map<String, String> body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {


        AuthenticatedAccount account = getAccount(authentication);

        if (account == null) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Please log in again.");
        }

        if (!"USER".equalsIgnoreCase(account.role())) {
            return error(HttpStatus.FORBIDDEN,
                    "Only household user accounts can accept invitations.");
        }

        if (body == null || body.get("invitationCode") == null
                || body.get("invitationCode").isBlank()) {
            return error(HttpStatus.BAD_REQUEST,
                    "Invitation code is required.");
        }

        String code = body.get("invitationCode").trim()
                .toUpperCase();

        long userId = account.id();

        // Lock the invitation row to prevent concurrent redemptions.
        List<Map<String, Object>> invitations =
                jdbcTemplate.queryForList("""
                    SELECT invitation_id, household_id, created_by,
                           expires_at, max_uses, use_count, is_active
                    FROM household_invitations
                    WHERE invitation_code = ?
                    FOR UPDATE
                    """, code);

        if (invitations.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST,
                    "Invalid invitation code.");
        }

        Map<String, Object> invitation = invitations.get(0);

        long invitationId = ((Number)
                invitation.get("invitation_id")).longValue();

        long invitedHouseholdId = ((Number)
                invitation.get("household_id")).longValue();

        int maxUses = ((Number)
                invitation.get("max_uses")).intValue();

        int useCount = ((Number)
                invitation.get("use_count")).intValue();

        Object activeValue = invitation.get("is_active");

        boolean active = activeValue instanceof Boolean
                ? (Boolean) activeValue
                : ((Number) activeValue).intValue() == 1;

        Object expiryValue = invitation.get("expires_at");

        LocalDateTime expiresAt;

        if (expiryValue instanceof java.sql.Timestamp timestamp) {
            expiresAt = timestamp.toLocalDateTime();
        } else if (expiryValue instanceof LocalDateTime dateTime) {
            expiresAt = dateTime;
        } else {
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not validate invitation expiry.");
        }

        if (!active
                || useCount >= maxUses
                || !expiresAt.isAfter(LocalDateTime.now())) {

            return error(HttpStatus.BAD_REQUEST,
                    "This invitation has expired or has already been used.");
        }

        // This version supports one household per user.
        List<Map<String, Object>> existingMemberships =
                jdbcTemplate.queryForList("""
                    SELECT household_id
                    FROM household_members
                    WHERE user_id = ?
                    FOR UPDATE
                    """, userId);

        if (!existingMemberships.isEmpty()) {
            long existingHouseholdId = ((Number)
                    existingMemberships.get(0).get("household_id"))
                    .longValue();

            if (existingHouseholdId == invitedHouseholdId) {
                return error(HttpStatus.CONFLICT,
                        "You already belong to this household.");
            }

            return error(HttpStatus.CONFLICT,
                    "You already belong to another household. "
                            + "Leave that household before joining another.");
        }

        // Add the authenticated user; never accept a user ID from the body.
        jdbcTemplate.update("""
            INSERT INTO household_members (
                household_id,
                user_id,
                member_role
            )
            VALUES (?, ?, 'MEMBER')
            """, invitedHouseholdId, userId);

        int updated = jdbcTemplate.update("""
            UPDATE household_invitations
            SET use_count = use_count + 1,
                is_active = 0
            WHERE invitation_id = ?
              AND is_active = 1
              AND use_count < max_uses
              AND expires_at > NOW()
            """, invitationId);

        if (updated != 1) {
            // Throwing causes the membership insert to roll back.
            throw new IllegalStateException(
                    "Invitation was no longer available.");
        }

        AuthenticatedAccount updatedAccount = new AuthenticatedAccount(
                userId,
                "USER",
                invitedHouseholdId
        );

        var updatedAuthentication = new UsernamePasswordAuthenticationToken(
                updatedAccount,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updatedAuthentication);
        SecurityContextHolder.setContext(context);

        HttpSessionSecurityContextRepository repository =
                new HttpSessionSecurityContextRepository();

        repository.saveContext(context, request, response);

        return ResponseEntity.ok(Map.of(
                "message", "You have joined the household successfully.",
                "householdId", invitedHouseholdId,
                "role", "MEMBER"
        ));
    }

    @GetMapping("/api-placeholder")
    public ResponseEntity<?> placeholder() {
        return ResponseEntity.ok().build();
    }

    private AuthenticatedAccount getAccount(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            return null;
        }

        return account;
    }

    private String generateCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        StringBuilder code = new StringBuilder(12);

        for (int i = 0; i < 12; i++) {
            code.append(alphabet.charAt(
                    secureRandom.nextInt(alphabet.length())));
        }

        return code.toString();
    }

    private ResponseEntity<Map<String, String>> error(
            HttpStatus status, String message) {

        return ResponseEntity.status(status)
                .body(Map.of("message", message));
    }
}
