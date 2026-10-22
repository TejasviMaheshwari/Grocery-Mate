
package com.grocerymate.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Service
public class ReminderScheduler {

    private final JdbcTemplate jdbcTemplate;

    public ReminderScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /*
     * Runs once per minute:
     * 1. Creates reminders from inventory dates.
     * 2. Processes due reminders into notifications.
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processReminders() {
        createStockCheckReminders();
        createRefillReminders();
        sendDueReminders();
    }

    private void createStockCheckReminders() {
        jdbcTemplate.update("""
            INSERT INTO reminders
                (item_id, household_id, scheduled_at, reminder_type, status)
            SELECT
                hi.item_id,
                hi.household_id,
                hi.next_stock_check_at,
                'STOCK_CHECK',
                'PENDING'
            FROM household_items hi
            WHERE hi.is_active = 1
              AND hi.next_stock_check_at IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM reminders r
                  WHERE r.item_id = hi.item_id
                    AND r.reminder_type = 'STOCK_CHECK'
                    AND r.scheduled_at = hi.next_stock_check_at
              )
            """);
    }

    private void createRefillReminders() {
        jdbcTemplate.update("""
            INSERT INTO reminders
                (item_id, household_id, scheduled_at, reminder_type, status)
            SELECT
                hi.item_id,
                hi.household_id,
                TIMESTAMP(
                    DATE_SUB(
                        hi.estimated_refill_date,
                        INTERVAL hi.reminder_days_before DAY
                    ),
                    '09:00:00'
                ),
                'REFILL',
                'PENDING'
            FROM household_items hi
            WHERE hi.is_active = 1
              AND hi.estimated_refill_date IS NOT NULL
              AND hi.reminder_days_before >= 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM reminders r
                  WHERE r.item_id = hi.item_id
                    AND r.reminder_type = 'REFILL'
                    AND r.scheduled_at = TIMESTAMP(
                        DATE_SUB(
                            hi.estimated_refill_date,
                            INTERVAL hi.reminder_days_before DAY
                        ),
                        '09:00:00'
                    )
              )
            """);
    }

    private void sendDueReminders() {
        List<Map<String, Object>> dueReminders = jdbcTemplate.queryForList("""
            SELECT
                r.reminder_id,
                r.item_id,
                r.household_id,
                r.reminder_type,
                hi.item_name
            FROM reminders r
            JOIN household_items hi ON hi.item_id = r.item_id
            WHERE r.status = 'PENDING'
              AND r.scheduled_at <= CURRENT_TIMESTAMP
              AND hi.is_active = 1
            ORDER BY r.scheduled_at, r.reminder_id
            """);

        for (Map<String, Object> reminder : dueReminders) {
            long reminderId = ((Number) reminder.get("reminder_id")).longValue();
            long itemId = ((Number) reminder.get("item_id")).longValue();
            long householdId = ((Number) reminder.get("household_id")).longValue();

            String type = String.valueOf(reminder.get("reminder_type"));
            String itemName = String.valueOf(reminder.get("item_name"));

            String message;
            if ("STOCK_CHECK".equalsIgnoreCase(type)) {
                message = "It's time to check the remaining stock of " + itemName + ".";
            } else if ("REFILL".equalsIgnoreCase(type)) {
                message = "Refill reminder: " + itemName + " may need to be purchased soon.";
            } else {
                message = "Reminder for " + itemName + ".";
            }

            /*
             * Include household members and the household head.
             * NOT EXISTS prevents duplicate notifications for this reminder.
             */
            jdbcTemplate.update("""
                INSERT INTO notifications
                    (user_id, household_id, reminder_id,
                     message, notification_type, is_read)
                SELECT recipients.user_id, ?, ?, ?, ?, 0
                FROM (
                    SELECT hm.user_id
                    FROM household_members hm
                    WHERE hm.household_id = ?

                    UNION

                    SELECT h.head_user_id AS user_id
                    FROM households h
                    WHERE h.household_id = ?
                ) recipients
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM notifications n
                    WHERE n.user_id = recipients.user_id
                      AND n.reminder_id = ?
                )
                """,
                    householdId,
                    reminderId,
                    message,
                    type,
                    householdId,
                    householdId,
                    reminderId
            );

            jdbcTemplate.update("""
                UPDATE reminders
                SET status = 'SENT',
                    sent_at = CURRENT_TIMESTAMP
                WHERE reminder_id = ?
                  AND status = 'PENDING'
                """, reminderId);
        }
    }
}
