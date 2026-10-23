const ITEMS_API = "/household/items";
const SPENDING_API = "/household/spending/monthly";
const UPCOMING_API = "/household/purchases/upcoming";
const PREDICTION_API = "/household/items";
const SPENDING_ANALYTICS_API = "/household/spending/analytics"; 
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


/* ---------------- Spending Analytics ---------------- */

function formatAnalyticsMonth(value) {
    if (!value) return "—";

    const parts = String(value).split("-");
    if (parts.length !== 2) return String(value);

    const date = new Date(
        Number(parts[0]),
        Number(parts[1]) - 1,
        1
    );

    return date.toLocaleDateString("en-IN", {
        month: "short",
        year: "2-digit"
    });
}

function analyticsMoney(value) {
    const amount = Number(value);

    return "₹" + (Number.isFinite(amount) ? amount : 0)
        .toLocaleString("en-IN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
}



function renderMonthlyTrend(trend) {
    const container = document.getElementById("monthlyTrendChart");
    if (!container) return;

    const entries = Object.entries(trend || {}).sort(
        ([monthA], [monthB]) => monthA.localeCompare(monthB)
    );

    const maxValue = Math.max(
        1,
        ...entries.map(([, amount]) => Number(amount) || 0)
    );

    container.replaceChildren();

    entries.forEach(([month, amount]) => {
        const value = Number(amount) || 0;
        const row = document.createElement("div");
        row.className = "trend-row";

        const monthLabel = document.createElement("span");
        monthLabel.className = "trend-month";
        monthLabel.textContent = formatAnalyticsMonth(month);

        const track = document.createElement("div");
        track.className = "trend-bar-track";

        const bar = document.createElement("div");
        bar.className = "trend-bar";
        bar.style.width = value > 0
            ? `${Math.max(3, (value / maxValue) * 100)}%`
            : "0%";

        track.appendChild(bar);

        const valueLabel = document.createElement("span");
        valueLabel.className = "trend-value";

        if (value === 0) {
            valueLabel.classList.add("no-purchases");
            valueLabel.textContent = "No purchases recorded";
        } else {
            valueLabel.textContent = analyticsMoney(value);
        }

        row.append(monthLabel, track, valueLabel);
        container.appendChild(row);
    });
}



function renderSpendingByItem(items, monthlyTotal) {
    const container = document.getElementById("spendingByItemChart");
    container.replaceChildren();

    if (!Array.isArray(items) || items.length === 0) {
        container.textContent =
            "No recorded purchases for the current month.";
        return;
    }

    items.forEach(item => {
        const name = String(item.itemName || "Unnamed item");
        const amount = Math.max(0, Number(item.totalSpent) || 0);

        const share = monthlyTotal > 0
            ? (amount / monthlyTotal) * 100
            : 0;

        const row = document.createElement("div");
        row.className = "item-spending-row";

        const heading = document.createElement("div");
        heading.className = "item-spending-heading";

        const label = document.createElement("span");
        label.className = "item-spending-name";
        label.textContent = name;

        const total = document.createElement("span");
        total.className = "item-spending-total";
        total.textContent = analyticsMoney(amount);

        heading.append(label, total);

        const track = document.createElement("div");
        track.className = "item-spending-track";

        const bar = document.createElement("div");
        bar.className = "item-spending-bar";
        bar.style.width = `${Math.min(100, share)}%`;
        bar.setAttribute("aria-hidden", "true");

        track.appendChild(bar);

        const shareLabel = document.createElement("span");
        shareLabel.className = "item-spending-share";
        shareLabel.textContent = `${share.toFixed(1)}% of monthly spending`;

        row.append(heading, track, shareLabel);
        container.appendChild(row);
    });
}

function renderSpendingAnalytics(data) {
    const status = document.getElementById("analyticsStatus");
    const comparison = document.getElementById("spendingComparison");
    const insights = document.getElementById("analyticsInsights");

    const currentTotal = Number(data.currentMonthTotal) || 0;
    const previousTotal = Number(data.previousMonthTotal) || 0;
    const difference = Number(data.difference) || 0;
    
    
    const purchaseCountElement = document.getElementById(
        "currentMonthPurchaseCount"
    );

    if (purchaseCountElement) {
        const items = Array.isArray(data.spendingByItem)
            ? data.spendingByItem
            : [];

        if (items.length > 0) {
            purchaseCountElement.textContent =
                `${items.length} item${items.length === 1 ? "" : "s"} purchased this month`;
        } else {
            purchaseCountElement.textContent =
                "No purchases recorded this month";
        }
    }


    document.getElementById("analyticsPeriod").textContent =
        `${formatAnalyticsMonth(data.monthlyTrend
            ? Object.keys(data.monthlyTrend).sort()[0]
            : data.currentMonth)} – ${formatAnalyticsMonth(data.currentMonth)}`;

    document.getElementById("analyticsCurrentLabel").textContent =
        formatAnalyticsMonth(data.currentMonth);

    document.getElementById("analyticsPreviousLabel").textContent =
        formatAnalyticsMonth(data.previousMonth);

    document.getElementById("analyticsCurrentTotal").textContent =
        analyticsMoney(currentTotal);

    document.getElementById("analyticsPreviousTotal").textContent =
        analyticsMoney(previousTotal);

    const differenceElement =
        document.getElementById("analyticsDifference");

    differenceElement.textContent =
        `${difference > 0 ? "+" : difference < 0 ? "−" : ""}` +
        analyticsMoney(Math.abs(difference));

    differenceElement.classList.toggle("spending-increased", difference > 0);
    differenceElement.classList.toggle("spending-decreased", difference < 0);

    const changeNote = document.getElementById("analyticsChangeNote");

    if (data.percentageChange == null) {
        changeNote.textContent = previousTotal === 0 && currentTotal > 0
            ? "No percentage available: previous month had zero spending"
            : "No percentage change available";
    } else {
        const percentage = Number(data.percentageChange);
        changeNote.textContent =
            `${percentage > 0 ? "+" : ""}${percentage.toFixed(2)}% vs previous month`;
    }

    renderMonthlyTrend(data.monthlyTrend);
    renderSpendingByItem(data.spendingByItem, currentTotal);

    const insightText = document.getElementById("analyticsInsightText");
    const topItem = Array.isArray(data.spendingByItem)
        ? data.spendingByItem[0]
        : null;

    let insight = "";

    if (currentTotal === 0 && previousTotal === 0) {
        insight = "No spending was recorded in either of the two months.";
    } else if (previousTotal === 0 && currentTotal > 0) {
        insight =
            `Recorded spending this month is ${analyticsMoney(currentTotal)}. ` +
            "The previous month had no recorded spending, so a percentage comparison is unavailable.";
    } else if (difference > 0) {
        insight =
            `Spending increased by ${analyticsMoney(difference)} ` +
            "compared with the previous month.";
    } else if (difference < 0) {
        insight =
            `Spending decreased by ${analyticsMoney(Math.abs(difference))} ` +
            "compared with the previous month.";
    } else {
        insight = "Recorded spending is unchanged from the previous month.";
    }

    if (topItem && currentTotal > 0) {
        const itemAmount = Number(topItem.totalSpent) || 0;
        const share = (itemAmount / currentTotal) * 100;

        insight +=
            ` ${topItem.itemName} is the highest-spending item at ` +
            `${analyticsMoney(itemAmount)} (${share.toFixed(1)}% of this month's recorded spending).`;
    }

    insightText.textContent = insight;

    comparison.hidden = false;
    insights.hidden = false;
    status.textContent = "";
}

function showAnalyticsError(error) {
    console.error("Could not load spending analytics:", error);

    const status = document.getElementById("analyticsStatus");
    status.textContent =
        "Spending analytics could not be loaded. Please try refreshing the page.";

    document.getElementById("spendingComparison").hidden = true;
    document.getElementById("analyticsInsights").hidden = true;

    document.getElementById("monthlyTrendChart").replaceChildren();
    document.getElementById("spendingByItemChart").replaceChildren();
}

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

        // Analytics is loaded independently so an analytics failure
        // does not prevent the existing dashboard from loading.
        try {
            const analytics = await apiRequest(SPENDING_ANALYTICS_API);
            renderSpendingAnalytics(analytics);
        } catch (analyticsError) {
            showAnalyticsError(analyticsError);
        }

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

/* ---------------- Household Invitations ---------------- */

const INVITATIONS_API = "/household/invitations";

const generateInvitationButton = document.getElementById(
    "generateInvitationButton"
);
const copyInvitationButton = document.getElementById(
    "copyInvitationButton"
);
const invitationResult = document.getElementById("invitationResult");
const invitationCodeDisplay = document.getElementById(
    "invitationCodeDisplay"
);
const invitationMessage = document.getElementById("invitationMessage");

function setInvitationMessage(message, success = false) {
    invitationMessage.textContent = message;
    invitationMessage.style.color = success ? "#17642b" : "#b42318";
}

generateInvitationButton.addEventListener("click", async () => {
    generateInvitationButton.disabled = true;
    generateInvitationButton.textContent = "Generating...";
    setInvitationMessage("");

    try {
        const result = await apiRequest(INVITATIONS_API, {
            method: "POST",
            body: JSON.stringify({})
        });

        if (!result?.invitationCode) {
            throw new Error(
                "The server did not return an invitation code."
            );
        }

        invitationCodeDisplay.value = result.invitationCode;
        invitationResult.hidden = false;
        copyInvitationButton.hidden = false;

        setInvitationMessage(
            "Invitation created successfully. Share this code privately.",
            true
        );
    } catch (error) {
        console.error("Invitation generation failed:", error);

        const message = String(error.message || "");

        if (
            message.includes("403") ||
            message.toLowerCase().includes("forbidden") ||
            message.toLowerCase().includes("owner")
        ) {
            setInvitationMessage(
                "Only the household owner can generate invitations."
            );
        } else {
            setInvitationMessage(
                "Could not generate an invitation: " +
                (message || "Please try again.")
            );
        }
    } finally {
        generateInvitationButton.disabled = false;
        generateInvitationButton.textContent = "Generate Invitation";
    }
});

copyInvitationButton.addEventListener("click", async () => {
    const code = invitationCodeDisplay.value;

    if (!code) {
        setInvitationMessage("Generate an invitation first.");
        return;
    }

    try {
        await navigator.clipboard.writeText(code);
        setInvitationMessage("Invitation code copied.", true);
    } catch (error) {
        // Fallback for browsers that block clipboard access.
        invitationCodeDisplay.focus();
        invitationCodeDisplay.select();

        setInvitationMessage(
            "Automatic copying is unavailable. Select the code and copy it manually."
        );
    }
});

/* ---------------- Household Members ---------------- */

const HOUSEHOLD_MEMBERS_API = "/household/members";

const householdMembersList = document.getElementById(
    "householdMembersList"
);

const householdMembersStatus = document.getElementById(
    "householdMembersStatus"
);

const refreshHouseholdMembersButton = document.getElementById(
    "refreshHouseholdMembers"
);

function formatMemberJoinDate(value) {
    if (!value) {
        return "Join date unavailable";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "Join date unavailable";
    }

    return "Joined " + date.toLocaleDateString("en-IN", {
        day: "numeric",
        month: "short",
        year: "numeric"
    });
}

function renderHouseholdMembers(members) {
    householdMembersList.replaceChildren();

    if (!members.length) {
        householdMembersStatus.textContent =
            "No household members found.";
        return;
    }

    householdMembersStatus.textContent =
        `${members.length} household member(s) found.`;

    members.forEach(member => {
        const card = document.createElement("article");
        card.className = "household-member-card";

        const avatar = document.createElement("div");
        avatar.className = "household-member-avatar";

        const name = String(member.fullName || "Unknown user").trim();

        avatar.textContent = name
            ? name.charAt(0).toUpperCase()
            : "?";

        avatar.setAttribute("aria-hidden", "true");

        const details = document.createElement("div");
        details.className = "household-member-details";

        const nameElement = document.createElement("h4");
        nameElement.textContent = name;

        const emailElement = document.createElement("p");
        emailElement.className = "household-member-email";
        emailElement.textContent = member.email || "Email unavailable";

        const joinedElement = document.createElement("p");
        joinedElement.className = "household-member-joined";
        joinedElement.textContent = formatMemberJoinDate(
            member.joinedAt
        );

        details.append(
            nameElement,
            emailElement,
            joinedElement
        );

        const role = String(member.role || "MEMBER").toUpperCase();

        const roleBadge = document.createElement("span");
        roleBadge.className = "household-member-role";

        if (role === "OWNER") {
            roleBadge.classList.add("owner");
            roleBadge.textContent = "Owner";
        } else {
            roleBadge.classList.add("member");
            roleBadge.textContent = "Member";
        }

        card.append(avatar, details, roleBadge);
        householdMembersList.appendChild(card);
    });
}

async function loadHouseholdMembers() {
    refreshHouseholdMembersButton.disabled = true;
    refreshHouseholdMembersButton.textContent = "Loading...";

    householdMembersStatus.textContent =
        "Loading household members...";

    try {
        const result = await apiRequest(HOUSEHOLD_MEMBERS_API);

        const members = Array.isArray(result?.members)
            ? result.members
            : [];

        renderHouseholdMembers(members);

    } catch (error) {
        console.error("Could not load household members:", error);

        householdMembersList.replaceChildren();

        householdMembersStatus.textContent =
            "Could not load household members. " +
            "Please try refreshing.";

    } finally {
        refreshHouseholdMembersButton.disabled = false;
        refreshHouseholdMembersButton.textContent = "Refresh";
    }
}

refreshHouseholdMembersButton.addEventListener(
    "click",
    loadHouseholdMembers
);

loadHouseholdMembers();

/* ---------------- Vendor Item Requests ---------------- */

const HOUSEHOLD_REQUESTS_API = "/household/requests";

const requestItemForm = document.getElementById("requestItemForm");
const requestItemMessage = document.getElementById("requestItemMessage");
const submitItemRequestButton =
    document.getElementById("submitItemRequest");

function showRequestItemMessage(message, success = false) {
    if (!requestItemMessage) return;

    requestItemMessage.textContent = message;
    requestItemMessage.style.color = success ? "#17642b" : "#b42318";
}

if (requestItemForm) {
    requestItemForm.addEventListener("submit", async function (event) {
        event.preventDefault();

        const itemName =
            document.getElementById("requestItemName").value.trim();

        const quantity = Number(
            document.getElementById("requestQuantity").value
        );

        const unit =
            document.getElementById("requestUnit").value;

        const notes =
            document.getElementById("requestNotes").value.trim();

        if (!itemName) {
            showRequestItemMessage("Please enter an item name.");
            return;
        }

        if (!Number.isFinite(quantity) || quantity <= 0) {
            showRequestItemMessage(
                "Please enter a quantity greater than zero."
            );
            return;
        }

        // Property names match CreateRequestBody in the Java controller.
        const requestBody = {
            requestedItemName: itemName,
            requestedQuantity: quantity,
            unit: unit,
            notes: notes || null
        };

        submitItemRequestButton.disabled = true;
        submitItemRequestButton.textContent = "Submitting...";
        showRequestItemMessage("");

        try {
            const result = await apiRequest(HOUSEHOLD_REQUESTS_API, {
                method: "POST",
                body: JSON.stringify(requestBody)
            });

            if (!result || !result.request) {
                throw new Error("The server did not return the saved request.");
            }

            const savedRequest = result.request;

            const requestId =
                savedRequest.requestId ??
                savedRequest.request_id;

            const status = savedRequest.status;
            const assigned = result.assigned;

            let message = result.message ||
                "Your item request was created.";

            if (requestId != null) {
                message += ` Request ID: ${requestId}.`;
            }

            if (status) {
                message += ` Status: ${status}.`;
            }

            if (assigned === true) {
                message += " A vendor has been assigned.";
                showRequestItemMessage(message, true);
            } else {
                message += " No eligible vendor was found yet.";
                showRequestItemMessage(message, false);
            }

            requestItemForm.reset();

        } catch (error) {
            console.error("Could not submit item request:", error);

            showRequestItemMessage(
                "Could not submit your request: " +
                (error.message || "Please try again.")
            );
        } finally {
            submitItemRequestButton.disabled = false;
            submitItemRequestButton.textContent = "Submit Request";
        }
    });
}
async function logout() {
    try {
        await apiRequest("/auth/logout", { method: "POST" });
    } catch (error) {
        console.error("Logout request failed:", error);
    } finally {
        localStorage.removeItem("grocerymate_vendor_id");
        localStorage.removeItem("grocerymate_role");
        window.location.replace("index.html");
    }
}
loadDashboard();