
    package com.grocerymate.controller;

    import com.grocerymate.security.AuthenticatedAccount;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.ResponseEntity;
    import org.springframework.jdbc.core.JdbcTemplate;
    import org.springframework.security.core.Authentication;
    import org.springframework.web.bind.annotation.*;

    import java.util.List;
    import java.util.Map;

    @RestController
    @RequestMapping("/api/household/notifications")
    public class NotificationController {

        private final JdbcTemplate jdbcTemplate;

        public NotificationController(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        // GET /api/household/notifications
        // GET /api/household/notifications?limit=10
        @GetMapping
        public ResponseEntity<?> getNotifications(
                @RequestParam(defaultValue = "20") int limit,
                Authentication authentication) {

            AuthenticatedAccount account = getAccount(authentication);
            if (account == null) {
                return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
            }

            if (!isHouseholdUser(account)) {
                return error(HttpStatus.FORBIDDEN,
                        "You are not authorized to view these notifications.");
            }

            // Prevent unnecessarily large requests.
            limit = Math.max(1, Math.min(limit, 100));

            List<Map<String, Object>> notifications =
                    jdbcTemplate.queryForList("""
                        SELECT
                            notification_id,
                            household_id,
                            request_id,
                            reminder_id,
                            message,
                            notification_type,
                            is_read,
                            created_at
                        FROM notifications
                        WHERE user_id = ?
                          AND (household_id IS NULL OR household_id = ?)
                        ORDER BY created_at DESC, notification_id DESC
                        LIMIT ?
                        """,
                            account.id(),
                            account.householdId(),
                            limit);

            return ResponseEntity.ok(notifications);
        }

        // GET /api/household/notifications/unread-count
        @GetMapping("/unread-count")
        public ResponseEntity<?> getUnreadCount(
                Authentication authentication) {

            AuthenticatedAccount account = getAccount(authentication);
            if (account == null) {
                return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
            }

            if (!isHouseholdUser(account)) {
                return error(HttpStatus.FORBIDDEN,
                        "You are not authorized to view these notifications.");
            }

            Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notifications
                WHERE user_id = ?
                  AND (household_id IS NULL OR household_id = ?)
                  AND is_read = 0
                """,
                    Integer.class,
                    account.id(),
                    account.householdId());

            return ResponseEntity.ok(Map.of(
                    "unreadCount", count == null ? 0 : count
            ));
        }

        // PATCH /api/household/notifications/{notificationId}/read
        @PatchMapping("/{notificationId}/read")
        public ResponseEntity<?> markAsRead(
                @PathVariable Long notificationId,
                Authentication authentication) {

            AuthenticatedAccount account = getAccount(authentication);
            if (account == null) {
                return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
            }

            if (!isHouseholdUser(account)) {
                return error(HttpStatus.FORBIDDEN,
                        "You are not authorized to update these notifications.");
            }

            if (notificationId == null || notificationId <= 0) {
                return error(HttpStatus.BAD_REQUEST,
                        "Invalid notification ID.");
            }

            int updated = jdbcTemplate.update("""
                UPDATE notifications
                SET is_read = 1
                WHERE notification_id = ?
                  AND user_id = ?
                  AND (household_id IS NULL OR household_id = ?)
                """,
                    notificationId,
                    account.id(),
                    account.householdId());

            if (updated == 0) {
                return error(HttpStatus.NOT_FOUND,
                        "Notification not found.");
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Notification marked as read.",
                    "notificationId", notificationId
            ));
        }

        // PATCH /api/household/notifications/read-all
        @PatchMapping("/read-all")
        public ResponseEntity<?> markAllAsRead(
                Authentication authentication) {

            AuthenticatedAccount account = getAccount(authentication);
            if (account == null) {
                return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
            }

            if (!isHouseholdUser(account)) {
                return error(HttpStatus.FORBIDDEN,
                        "You are not authorized to update these notifications.");
            }

            int updated = jdbcTemplate.update("""
                UPDATE notifications
                SET is_read = 1
                WHERE user_id = ?
                  AND (household_id IS NULL OR household_id = ?)
                  AND is_read = 0
                """,
                    account.id(),
                    account.householdId());

            return ResponseEntity.ok(Map.of(
                    "message", "Notifications marked as read.",
                    "updatedCount", updated
            ));
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

        private boolean isHouseholdUser(AuthenticatedAccount account) {
            return "USER".equalsIgnoreCase(account.role())
                    && account.householdId() != null;
        }

        private ResponseEntity<Map<String, String>> error(
                HttpStatus status, String message) {

            return ResponseEntity.status(status)
                    .body(Map.of("message", message));
        }
    }
