const ITEMS_API = "/household/items";
const SPENDING_API = "/household/spending/monthly";
const UPCOMING_API = "/household/purchases/upcoming";
const PREDICTION_API = "/household/items";

function getField(item, snake, camel, fallback = null) {
    return item[snake] ?? item[camel] ?? fallback;
}

function showMessage(message, success = false) {
    const element = document.getElementById("message");
    if (!element) return;
    element.textContent = message;
    element.style.color = success ? "#17642b" : "#b42318";
}

function formatDate(value) {
    if (!value) return "—";
    return String(value).replace("T", " ").slice(0, 16);
}

function money(amount) {
    const num = Number(amount || 0);
    return "₹" + num.toFixed(2);
}

async function loadPredictions(items) {
    const results = {};

    await Promise.all(items.map(async (item) => {
        const itemId = getField(item, "item_id", "itemId");

        if (itemId == null) return;

        try {
            results[itemId] = await apiRequest(
                `${PREDICTION_API}/${itemId}/prediction`
            );
        } catch (error) {
            console.error(
                `Prediction unavailable for item ${itemId}:`,
                error
            );

            results[itemId] = null;
        }
    }));

    return results;
}

function getStockInfo(item, prediction) {
    const quantity = Number(
        getField(item, "quantity_remaining", "quantityRemaining", 0)
    );

    if (quantity <= 0) {
        return {
            days: 0,
            label: "Out of stock",
            color: "red",
            refillDate: null,
            dailyUsage: null
        };
    }

    if (!prediction) {
        return {
            days: null,
            label: "Prediction unavailable",
            color: "amber",
            refillDate: null,
            dailyUsage: null
        };
    }

    if (prediction.enoughData !== true) {
        return {
            days: null,
            label: "Collecting usage data",
            color: "amber",
            refillDate: null,
            dailyUsage: null
        };
    }

    const days = prediction.estimatedDaysRemaining;
    const refillDate = prediction.predictedRefillDate;
    const dailyUsage = prediction.averageDailyConsumption;

    if (prediction.stockStatus === "OUT_OF_STOCK") {
        return {
            days: 0,
            label: "Out of stock",
            color: "red",
            refillDate,
            dailyUsage
        };
    }

    if (prediction.stockStatus === "LOW_STOCK") {
        return {
            days,
            label: `Low · ${days} day(s)`,
            color: "red",
            refillDate,
            dailyUsage
        };
    }

    return {
        days,
        label: `Good · ${days} day(s)`,
        color: "green",
        refillDate,
        dailyUsage
    };
}

function addCell(row, value) {
    const cell = document.createElement("td");
    cell.textContent = value ?? "—";
    row.appendChild(cell);
    return cell;
}

async function updateStock(itemId, action, itemName) {
    const actionLabel = action === "CONSUME" ? "consume" : "replenish";

    const input = window.prompt(
        `How much ${itemName} would you like to ${actionLabel}? Enter quantity:`
    );

    // Cancelled by the user.
    if (input === null) return;

    const quantity = Number(input);

    if (!input.trim() || !Number.isFinite(quantity) || quantity <= 0) {
        showMessage("Please enter a valid quantity greater than zero.");
        return;
    }

    try {
        const result = await apiRequest(
            `${ITEMS_API}/${itemId}/stock`,
            {
                method: "POST",
                body: JSON.stringify({
                    action: action,
                    quantity: quantity
                })
            }
        );

        // Reload items, spending, alerts and upcoming purchases.
        await loadDashboard();

        const remaining = Number(result.quantityRemaining);
        const unit = result.unit || "";

        showMessage(
            `${itemName}: stock updated successfully. ` +
            `Remaining: ${remaining} ${unit}.`,
            true
        );
    } catch (error) {
        showMessage("Could not update stock: " + error.message);
    }
}

function renderItems(items, predictions = {}) {
    const body = document.getElementById("itemsBody");
    const alerts = document.getElementById("alertsList");

    body.replaceChildren();
    alerts.replaceChildren();

    let lowCount = 0;

    if (!items.length) {
        const row = document.createElement("tr");
        addCell(row, "No household essentials added yet.");
        row.firstElementChild.colSpan = 12;
        body.appendChild(row);

        document.getElementById("totalItems").textContent = "0";
        document.getElementById("lowItems").textContent = "0";

        const alert = document.createElement("li");
        alert.textContent = "No household essentials to monitor.";
        alerts.appendChild(alert);

        return;
    }

    items.forEach((item) => {
        const name = getField(
            item, "item_name", "itemName", "Unnamed item"
        );

        const quantity = Number(
            getField(item, "quantity_remaining", "quantityRemaining", 0)
        );

        const unit = getField(item, "unit", "unit", "");
        const itemId = getField(item, "item_id", "itemId");
        const prediction = predictions[itemId];

        // const duration = getStockInfo(item, prediction);

        const duration = getStockInfo(item, prediction);

        const thresholdValue = getField(
            item,
            "low_stock_threshold",
            "lowStockThreshold"
        );

        const threshold = thresholdValue == null
            ? null
            : Number(thresholdValue);

        const isBelowThreshold =
            threshold !== null &&
            Number.isFinite(threshold) &&
            quantity > 0 &&
            quantity <= threshold;

        if (isBelowThreshold && duration.color !== "red") {
            duration.label = `Low stock · ${quantity} ${unit}`;
            duration.color = "red";
        }
        const row = document.createElement("tr");
        row.className = `stock-row-${duration.color}`;

        // 1. Item
        addCell(row, name);

        // 2. Remaining stock
        addCell(row, `${quantity} ${unit}`.trim());

        // 3. Estimated duration
        addCell(
            row,
            quantity <= 0
                ? "Finished"
                : duration.days == null
                    ? duration.label
                    : `${duration.days} day(s)`
        );

        // 4. Stock status
        const statusCell = document.createElement("td");
        const badge = document.createElement("span");

        badge.className = `stock-badge ${duration.color}`;
        badge.textContent = duration.label;

        statusCell.appendChild(badge);
        row.appendChild(statusCell);

        // 5. Last purchase date
        const lastPurchaseDate = getField(
            item, "last_purchase_date", "lastPurchaseDate"
        );

        addCell(row, formatDate(lastPurchaseDate));

        // 6. Last quantity purchased
        const lastQuantityPurchased = getField(
            item,
            "last_quantity_purchased",
            "lastQuantityPurchased"
        );

        addCell(
            row,
            lastQuantityPurchased == null
                ? "—"
                : `${lastQuantityPurchased} ${unit}`.trim()
        );

        // 7. Price per unit (from genuine purchase history)
        const lastUnitPrice = getField(
            item, "last_unit_price", "lastUnitPrice"
        );

        addCell(
            row,
            lastUnitPrice == null
                ? "—"
                : `${money(lastUnitPrice)}/${unit}`
        );

        // 8. Total cost of the last purchase
        const lastPurchaseTotal = getField(
            item, "last_purchase_total", "lastPurchaseTotal"
        );

        addCell(
            row,
            lastPurchaseTotal == null
                ? "—"
                : money(lastPurchaseTotal)
        );

        // 9. Estimated refill date
        const estimatedRefillDate =
            duration.refillDate ||
            getField(
                item,
                "estimated_refill_date",
                "estimatedRefillDate"
            );

        addCell(row, formatDate(estimatedRefillDate));

        // 10. Reminder days
        addCell(
            row,
            getField(item, "reminder_days_before", "reminderDaysBefore", 3)
        );

        // 11. Next stock check
        addCell(
            row,
            formatDate(
                getField(
                    item,
                    "next_stock_check_at",
                    "nextStockCheckAt"
                )
            )
        );

        
        // 12. Actions — consumption only
        const actionsCell = document.createElement("td");

        const consumeButton = document.createElement("button");
        consumeButton.type = "button";
        consumeButton.textContent = "Consume";

        consumeButton.addEventListener("click", () => {
            if (itemId != null) {
                updateStock(itemId, "CONSUME", name);
            } else {
                showMessage("Cannot update stock: item ID is missing.");
            }
        });

        actionsCell.appendChild(consumeButton);
        row.appendChild(actionsCell);



        body.appendChild(row);

        // Low-stock alerts
        // Low-stock alerts
        if (
            duration.color === "red" ||
            duration.label === "Out of stock"
        ) {
            lowCount++;
        }

        if (duration.label === "Out of stock") {
            const alert = document.createElement("li");
            alert.className = "critical";
            alert.textContent =
                `${name}: OUT OF STOCK — replenish immediately.`;

            alerts.appendChild(alert);

        } else if (isBelowThreshold) {
            const alert = document.createElement("li");
            alert.className = "critical";
            alert.textContent =
                `${name}: LOW STOCK — ${quantity} ${unit} remaining ` +
                `(threshold: ${threshold} ${unit}).`;

            alerts.appendChild(alert);

        } else if (duration.label.startsWith("Low")) {
            const alert = document.createElement("li");
            alert.className = "critical";
            alert.textContent =
                `${name}: LOW STOCK — ${quantity} ${unit} remaining. ` +
                `${duration.label}`;

            alerts.appendChild(alert);

        } else if (
            duration.label === "Collecting usage data" ||
            duration.label === "Prediction unavailable"
        ) {
            const alert = document.createElement("li");
            alert.className = "prediction-pending";
            alert.textContent =
                `${name}: Prediction pending — more usage data is needed.`;

            alerts.appendChild(alert);
        }
        // if (duration.color === "red") {
        //     lowCount++;
        // }
        // // Low-stock alerts and prediction-pending messages
        // if (duration.label === "Out of stock") {
        //     const alert = document.createElement("li");
        //     alert.className = "critical";
        //     alert.textContent =
        //         `${name}: OUT OF STOCK — replenish immediately.`;
        //
        //     alerts.appendChild(alert);
        //
        // } else if (duration.label.startsWith("Low ·")) {
        //     const alert = document.createElement("li");
        //     alert.className = "critical";
        //     alert.textContent =
        //         `${name}: ${quantity} ${unit} remaining — ${duration.label}`;
        //
        //     alerts.appendChild(alert);
        //
        // } else if (
        //     duration.label === "Collecting usage data" ||
        //     duration.label === "Prediction unavailable"
        // ) {
        //     const alert = document.createElement("li");
        //     alert.className = "prediction-pending";
        //     alert.textContent =
        //         `${name}: Prediction pending — more usage data is needed.`;
        //
        //     alerts.appendChild(alert);
        // }

    });

    if (alerts.children.length === 0) {
        const alert = document.createElement("li");
        alert.textContent = "No low-stock alerts.";
        alerts.appendChild(alert);
    }

    document.getElementById("totalItems").textContent = items.length;
    document.getElementById("lowItems").textContent = lowCount;
}


function renderUpcoming(data) {
    const list = document.getElementById("upcomingList");
    const countElement = document.getElementById("upcomingItems");

    list.replaceChildren();

    const items = Array.isArray(data)
        ? data
        : (data?.items || []);

    // Use today's local date, with the time set to midnight.
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    // Include today and the next 7 days.
    const sevenDaysLater = new Date(today);
    sevenDaysLater.setDate(sevenDaysLater.getDate() + 7);

    const upcomingItems = items.filter(item => {
        const refillDate = getField(
            item,
            "estimated_refill_date",
            "estimatedRefillDate",
            getField(item, "expectedDate", "expectedDate", null)
        );

        if (!refillDate) return false;

        // Parse YYYY-MM-DD as a local calendar date.
        const dateText = String(refillDate).slice(0, 10);
        const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateText);

        if (!match) return false;

        const date = new Date(
            Number(match[1]),
            Number(match[2]) - 1,
            Number(match[3])
        );

        return date >= today && date <= sevenDaysLater;
    });

    // The top card counts only purchases due within 7 days.
    countElement.textContent = upcomingItems.length;

    if (upcomingItems.length === 0) {
        const li = document.createElement("li");
        li.textContent = "No purchases expected within the next 7 days.";
        list.appendChild(li);
        return;
    }

    // Show the soonest refill dates first.
    upcomingItems.sort((a, b) => {
        const dateA = String(getField(
            a,
            "estimated_refill_date",
            "estimatedRefillDate",
            getField(a, "expectedDate", "expectedDate", "")
        )).slice(0, 10);

        const dateB = String(getField(
            b,
            "estimated_refill_date",
            "estimatedRefillDate",
            getField(b, "expectedDate", "expectedDate", "")
        )).slice(0, 10);

        return dateA.localeCompare(dateB);
    });

    upcomingItems.forEach(item => {
        const li = document.createElement("li");

        const name = getField(
            item,
            "item_name",
            "itemName",
            getField(item, "name", "name", "Item")
        );

        const date = getField(
            item,
            "estimated_refill_date",
            "estimatedRefillDate",
            getField(item, "expectedDate", "expectedDate", null)
        );

        li.textContent = `${name} — ${String(date).slice(0, 10)}`;
        list.appendChild(li);
    });
}

// function renderUpcoming(data) {
//     const list = document.getElementById("upcomingList");
//     list.replaceChildren();

//     const items = Array.isArray(data) ? data : (data?.items || []);

//     if (!items.length) {
//         list.textContent = "No upcoming purchases returned by the server.";
//         document.getElementById("upcomingItems").textContent = "0";
//         return;
//     }

//     document.getElementById("upcomingItems").textContent = items.length;

//     items.forEach(item => {
//         const li = document.createElement("li");
//         const name = getField(item, "item_name", "itemName", getField(item, "name", "name", "Item"));
//         const date = getField(item, "estimated_refill_date", "estimatedRefillDate", getField(item, "expectedDate", "expectedDate", null));

//         li.textContent = `${name} — ${date || "Date not set"}`;
//         list.appendChild(li);
//     });
// }

async function loadDashboard() {
    
    const role = localStorage.getItem("grocerymate_role");
    const householdId = localStorage.getItem("grocerymate_household_id");
    const userId = localStorage.getItem("grocerymate_user_id");

    // Require the account details returned by the successful login.
    if (!userId || !householdId || !role) {
        window.location.replace("index.html");
        return;
    }

    if (role.toUpperCase() !== "USER") {
        window.location.replace("index.html");
        return;
    }

    const queryParam = householdId ? `?householdId=${householdId}` : "";

    try {
        const [itemsResponse, spendingResponse, upcomingResponse] = await Promise.all([
            apiRequest(`${ITEMS_API}${queryParam}`),
            apiRequest(`${SPENDING_API}${queryParam}`),
            apiRequest(`${UPCOMING_API}${queryParam}`)
        ]);

        const items = Array.isArray(itemsResponse)
            ? itemsResponse
            : (itemsResponse?.items || []);

        const predictions = await loadPredictions(items);

        renderItems(items, predictions);
        // const items = Array.isArray(itemsResponse) ? itemsResponse : (itemsResponse?.items || []);
        // renderItems(items);

        const spending = typeof spendingResponse === "number"
            ? spendingResponse
            : (spendingResponse?.total ?? spendingResponse?.amount ?? 0);

        document.getElementById("monthlySpend").textContent = money(spending);
        renderUpcoming(upcomingResponse);

        showMessage("Dashboard data loaded successfully.", true);
    } catch (error) {
        showMessage("Could not load dashboard data: " + error.message);
    }
}

document.getElementById("itemForm").addEventListener("submit", async function(event) {
    event.preventDefault();

    const householdId = localStorage.getItem("grocerymate_household_id");

    
    
    const item = {
        household_id: householdId ? Number(householdId) : null,
        item_name: document.getElementById("itemName").value.trim(),
        quantity_purchased: Number(
            document.getElementById("quantity").value
        ),

        // Low-stock threshold
        low_stock_threshold:
            document.getElementById("lowStockThreshold").value.trim() === ""
                ? null
                : Number(
                    document.getElementById("lowStockThreshold").value
                ),

        unit: document.getElementById("unit").value,
        purchase_date: document.getElementById("purchaseDate").value,
        total_purchase_cost: Number(
            document.getElementById("totalPurchaseCost").value
        ),
        expected_duration_days: Number(
            document.getElementById("duration").value
        ),
        estimated_refill_date:
            document.getElementById("refillDate").value || null,
        reminder_days_before: Number(
            document.getElementById("reminderDays").value
        ),
        next_stock_check_at:
            document.getElementById("stockCheck").value
                ? document.getElementById("stockCheck").value + ":00"
                : null
    };

        
    try {
        await apiRequest(ITEMS_API, {
            method: "POST",
            body: JSON.stringify(item)
        });

        showMessage("Household essential saved successfully.", true);
        event.target.reset();

        document.getElementById("duration").value = 30;
        document.getElementById("reminderDays").value = 3;

        await loadDashboard();
    } catch (error) {
        showMessage("Could not save item: " + error.message);
    }
});

/* ---------------- Notifications ---------------- */

const NOTIFICATIONS_API = "/household/notifications";

let notificationsPanelOpen = false;
let notificationRefreshInProgress = false;

const notificationBell = document.getElementById("notificationBell");
const notificationBadge = document.getElementById("notificationBadge");
const notificationPanel = document.getElementById("notificationPanel");
const notificationList = document.getElementById("notificationList");
const notificationStatus = document.getElementById("notificationStatus");
const markAllNotificationsReadButton =
    document.getElementById("markAllNotificationsRead");

function setNotificationStatus(message) {
    if (notificationStatus) {
        notificationStatus.textContent = message || "";
    }
}

function updateNotificationBadge(count) {
    const unreadCount = Math.max(0, Number(count) || 0);

    notificationBadge.textContent =
        unreadCount > 99 ? "99+" : String(unreadCount);

    notificationBadge.hidden = unreadCount === 0;

    notificationBell.setAttribute(
        "aria-label",
        `Notifications, ${unreadCount} unread`
    );
}

function formatNotificationDate(value) {
    if (!value) return "";

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return String(value).replace("T", " ").slice(0, 16);
    }

    return date.toLocaleString("en-IN", {
        dateStyle: "medium",
        timeStyle: "short"
    });
}

async function loadUnreadNotificationCount() {
    const result = await apiRequest(
        `${NOTIFICATIONS_API}/unread-count`
    );

    updateNotificationBadge(result?.unreadCount ?? 0);
}

function renderNotifications(notifications) {
    notificationList.replaceChildren();

    if (!notifications.length) {
        const empty = document.createElement("li");
        empty.className = "notification-item";
        empty.textContent = "You're all caught up. No notifications.";
        notificationList.appendChild(empty);
        return;
    }

    notifications.forEach(notification => {
        const item = document.createElement("li");
        item.className = "notification-item";

        const isRead =
            notification.is_read === true ||
            notification.is_read === 1 ||
            notification.is_read === "1";

        if (!isRead) {
            item.classList.add("unread");
        }

        const message = document.createElement("p");
        message.className = "notification-message";
        message.textContent = notification.message || "New notification";

        const meta = document.createElement("div");
        meta.className = "notification-meta";

        const date = document.createElement("span");
        date.textContent = formatNotificationDate(notification.created_at);
        meta.appendChild(date);

        if (!isRead) {
            const readButton = document.createElement("button");
            readButton.type = "button";
            readButton.className = "notification-read-button";
            readButton.textContent = "Mark read";

            readButton.addEventListener("click", async () => {
                readButton.disabled = true;

                try {
                    await apiRequest(
                        `${NOTIFICATIONS_API}/${notification.notification_id}/read`,
                        { method: "PATCH" }
                    );

                    await refreshNotifications();
                } catch (error) {
                    setNotificationStatus(
                        "Could not mark notification as read: " + error.message
                    );
                    readButton.disabled = false;
                }
            });

            meta.appendChild(readButton);
        } else {
            const readLabel = document.createElement("span");
            readLabel.textContent = "Read";
            meta.appendChild(readLabel);
        }

        item.append(message, meta);
        notificationList.appendChild(item);
    });
}

async function loadNotificationList() {
    const result = await apiRequest(
        `${NOTIFICATIONS_API}?limit=50`
    );

    const notifications = Array.isArray(result)
        ? result
        : (result?.notifications || []);

    renderNotifications(notifications);
}

async function refreshNotifications() {
    if (notificationRefreshInProgress) return;

    notificationRefreshInProgress = true;

    try {
        await loadUnreadNotificationCount();

        if (notificationsPanelOpen) {
            await loadNotificationList();
            setNotificationStatus("");
        }
    } catch (error) {
        console.error("Could not refresh notifications:", error);

        if (notificationsPanelOpen) {
            setNotificationStatus(
                "Unable to load notifications. " + error.message
            );
        }
    } finally {
        notificationRefreshInProgress = false;
    }
}

notificationBell.addEventListener("click", async () => {
    notificationsPanelOpen = !notificationsPanelOpen;

    notificationPanel.hidden = !notificationsPanelOpen;
    notificationBell.setAttribute(
        "aria-expanded",
        String(notificationsPanelOpen)
    );

    if (notificationsPanelOpen) {
        setNotificationStatus("Loading notifications...");
        await refreshNotifications();
    }
});

markAllNotificationsReadButton.addEventListener("click", async () => {
    markAllNotificationsReadButton.disabled = true;

    try {
        await apiRequest(
            `${NOTIFICATIONS_API}/read-all`,
            { method: "PATCH" }
        );

        await refreshNotifications();
        setNotificationStatus("All notifications marked as read.");
    } catch (error) {
        setNotificationStatus(
            "Could not mark notifications as read: " + error.message
        );
    } finally {
        markAllNotificationsReadButton.disabled = false;
    }
});

// Close the panel when clicking elsewhere on the page.
document.addEventListener("click", event => {
    if (
        notificationsPanelOpen &&
        !event.target.closest(".notification-area")
    ) {
        notificationsPanelOpen = false;
        notificationPanel.hidden = true;
        notificationBell.setAttribute("aria-expanded", "false");
    }
});

// Load unread count immediately and refresh periodically.
refreshNotifications();
setInterval(refreshNotifications, 60000);

loadDashboard();


