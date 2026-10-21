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


// function getStockInfo(item) {
//     const quantity = Number(getField(item, "quantity_remaining", "quantityRemaining", 0));
//     const duration = Number(getField(item, "expected_duration_days", "expectedDurationDays", 30));
//     const purchaseDate = getField(item, "purchase_date", "purchaseDate");

//     if (quantity <= 0) {
//         return { days: 0, label: "Out of stock", color: "red" };
//     }

//     let daysRemaining = duration;

//     if (purchaseDate) {
//         const purchased = new Date(`${purchaseDate}T00:00:00`);
//         const today = new Date();
//         today.setHours(0, 0, 0, 0);

//         if (!Number.isNaN(purchased.getTime())) {
//             const elapsed = Math.max(0, Math.floor((today - purchased) / 86400000));
//             daysRemaining = Math.max(0, duration - elapsed);
//         }
//     }

//     if (daysRemaining <= 3) {
//         return { days: daysRemaining, label: `Critical · ${daysRemaining} day(s)`, color: "red" };
//     }

//     if (daysRemaining <= 7) {
//         return { days: daysRemaining, label: `Low · ${daysRemaining} days`, color: "amber" };
//     }

//     return { days: daysRemaining, label: `Good · ${daysRemaining} days`, color: "green" };
// }

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
        row.firstElementChild.colSpan = 9;
        body.appendChild(row);
        document.getElementById("totalItems").textContent = "0";
        document.getElementById("lowItems").textContent = "0";
        return;
    }

    // items.forEach(item => {
    items.forEach((item, index) => {
        const name = getField(item, "item_name", "itemName", "Unnamed item");
        const quantity = Number(getField(item, "quantity_remaining", "quantityRemaining", 0));
        const unit = getField(item, "unit", "unit", "");
        const itemId = getField(item, "item_id", "itemId");
        const prediction = predictions[itemId];
        const duration = getStockInfo(item, prediction);
        // const duration = getStockInfo(item);

        const row = document.createElement("tr");
        row.className = `stock-row-${duration.color}`;

        addCell(row, name);
        addCell(row, `${quantity} ${unit}`.trim());
        addCell(
            row,
            quantity <= 0
                ? "Finished"
                : duration.days == null
                    ? duration.label
                    : `${duration.days} day(s)`
        );
        // addCell(row, quantity <= 0 ? "Finished" : `${duration.days} day(s)`);

        const statusCell = document.createElement("td");
        const badge = document.createElement("span");
        badge.className = `stock-badge ${duration.color}`;
        badge.textContent = duration.label;
        statusCell.appendChild(badge);
        row.appendChild(statusCell);

        addCell(row, getField(item, "purchase_date", "purchaseDate", "—"));

        const price = getField(item, "last_purchase_price", "lastPurchasePrice");
        addCell(row, price == null ? "—" : money(price));
                addCell(
            row,
            duration.refillDate ||
            getField(item, "estimated_refill_date", "estimatedRefillDate", "—")
        );
        // addCell(row, getField(item, "estimated_refill_date", "estimatedRefillDate", "—"));

        addCell(row, getField(item, "reminder_days_before", "reminderDaysBefore", 3));
        
        addCell(
            row,
            formatDate(getField(item, "next_stock_check_at", "nextStockCheckAt"))
        );

        // Actions column
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

        const replenishButton = document.createElement("button");
        replenishButton.type = "button";
        replenishButton.textContent = "Replenish";
        replenishButton.className = "secondary";
        replenishButton.addEventListener("click", () => {
            if (itemId != null) {
                updateStock(itemId, "REPLENISH", name);
            } else {
                showMessage("Cannot update stock: item ID is missing.");
            }
        });

        actionsCell.append(consumeButton, replenishButton);
        row.appendChild(actionsCell);

        body.appendChild(row);

        if (duration.color === "red") {
            lowCount++;

            const alert = document.createElement("li");
            alert.className = "critical";
            alert.textContent =
                `${name}: ${quantity} ${unit} remaining — ${duration.label}`;

            alerts.appendChild(alert);
        } else if (duration.color === "amber") {
            const alert = document.createElement("li");
            alert.textContent =
                `${name}: ${quantity} ${unit} remaining — ${duration.label}`;

            alerts.appendChild(alert);
        }

        if (index === items.length - 1 && alerts.children.length === 0) {
            const alert = document.createElement("li");
            alert.textContent = "No low-stock alerts.";
            alerts.appendChild(alert);
        }

        // if (duration.color !== "green") {
        //     const alert = document.createElement("li");
        //     alert.className = duration.color === "red" ? "critical" : "";
        //     alert.textContent =
        //         `${name}: ${quantity} ${unit} remaining — ${duration.label}`;
        //     alerts.appendChild(alert);
        // }

        // if (duration.color === "red") {
        //     lowCount++;
        // }
    });

    if (!lowCount) {
        const alert = document.createElement("li");
        alert.textContent = "All tracked items have more than 7 estimated days remaining.";
        alerts.appendChild(alert);
    }

    document.getElementById("totalItems").textContent = items.length;
    document.getElementById("lowItems").textContent = lowCount;
}

function renderUpcoming(data) {
    const list = document.getElementById("upcomingList");
    list.replaceChildren();

    const items = Array.isArray(data) ? data : (data?.items || []);

    if (!items.length) {
        list.textContent = "No upcoming purchases returned by the server.";
        document.getElementById("upcomingItems").textContent = "0";
        return;
    }

    document.getElementById("upcomingItems").textContent = items.length;

    items.forEach(item => {
        const li = document.createElement("li");
        const name = getField(item, "item_name", "itemName", getField(item, "name", "name", "Item"));
        const date = getField(item, "estimated_refill_date", "estimatedRefillDate", getField(item, "expectedDate", "expectedDate", null));

        li.textContent = `${name} — ${date || "Date not set"}`;
        list.appendChild(li);
    });
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

    
    // const token = localStorage.getItem("grocerymate_token");
    // const role = localStorage.getItem("grocerymate_role");
    // const householdId = localStorage.getItem("grocerymate_household_id");

    // if (!token) {
    //     window.location.href = "index.html";
    //     return;
    // }

    // if (role && role.toUpperCase() !== "USER") {
    //     window.location.href = "vendor-dashboard.html";
    //     return;
    // }

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
        quantity_remaining: Number(document.getElementById("quantity").value),
        unit: document.getElementById("unit").value,
        purchase_date: document.getElementById("purchaseDate").value || null,
        last_purchase_price: document.getElementById("lastPrice").value === "" ? null : Number(document.getElementById("lastPrice").value),
        expected_duration_days: Number(document.getElementById("duration").value),
        estimated_refill_date: document.getElementById("refillDate").value || null,
        reminder_days_before: Number(document.getElementById("reminderDays").value),
        next_stock_check_at: document.getElementById("stockCheck").value ? document.getElementById("stockCheck").value + ":00" : null
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

loadDashboard();



// const ITEMS_API = "/household/items";
// const SPENDING_API = "/household/spending/monthly";
// const UPCOMING_API = "/household/purchases/upcoming";

// function getField(item, snake, camel, fallback = null) {
//     return item[snake] ?? item[camel] ?? fallback;
// }

// function showMessage(message, success = false) {
//     const element = document.getElementById("message");
//     element.textContent = message;
//     element.style.color = success ? "#17642b" : "#b42318";
// }

// function formatDate(value) {
//     if (!value) return "—";
//     return String(value).replace("T", " ").slice(0, 16);
// }


// function getStockInfo(item) {
//     const quantity = Number(
//         getField(item, "quantity_remaining", "quantityRemaining", 0)
//     );

//     const duration = Number(
//         getField(item, "expected_duration_days", "expectedDurationDays", 30)
//     );

//     const purchaseDate = getField(
//         item, "purchase_date", "purchaseDate"
//     );

//     if (quantity <= 0) {
//         return { days: 0, label: "Out of stock", color: "red" };
//     }

//     let daysRemaining = duration;

//     if (purchaseDate) {
//         const purchased = new Date(`${purchaseDate}T00:00:00`);
//         const today = new Date();
//         today.setHours(0, 0, 0, 0);

//         if (!Number.isNaN(purchased.getTime())) {
//             const elapsed = Math.max(
//                 0,
//                 Math.floor((today - purchased) / 86400000)
//             );

//             daysRemaining = Math.max(0, duration - elapsed);
//         }
//     }

//     if (daysRemaining <= 3) {
//         return {
//             days: daysRemaining,
//             label: `Critical · ${daysRemaining} day(s)`,
//             color: "red"
//         };
//     }

//     if (daysRemaining <= 7) {
//         return {
//             days: daysRemaining,
//             label: `Low · ${daysRemaining} days`,
//             color: "amber"
//         };
//     }

//     return {
//         days: daysRemaining,
//         label: `Good · ${daysRemaining} days`,
//         color: "green"
//     };
// }


// function addCell(row, value) {
//     const cell = document.createElement("td");
//     cell.textContent = value ?? "—";
//     row.appendChild(cell);
//     return cell;
// }

// function renderItems(items) {
//     const body = document.getElementById("itemsBody");
//     const alerts = document.getElementById("alertsList");

//     body.replaceChildren();
//     alerts.replaceChildren();

//     let lowCount = 0;

//     if (!items.length) {
//         const row = document.createElement("tr");
//         addCell(row, "No household essentials added yet.");
//         const cell = row.firstElementChild;
//         cell.colSpan = 9;
//         body.appendChild(row);
//     }

//     items.forEach(item => {
//         const name = getField(item, "item_name", "itemName", "Unnamed item");
//         const quantity = Number(
//             getField(item, "quantity_remaining", "quantityRemaining", 0)
//         );
//         const unit = getField(item, "unit", "unit", "");
//         const duration = getStockInfo(item);

//         const row = document.createElement("tr");
//         row.className = `stock-row-${duration.color}`;

//         addCell(row, name);
//         addCell(row, `${quantity} ${unit}`.trim());

//         addCell(
//             row,
//             quantity <= 0 ? "Finished" : `${duration.days} day(s)`
//         );

//         const statusCell = document.createElement("td");
//         const badge = document.createElement("span");
//         badge.className = `stock-badge ${duration.color}`;
//         badge.textContent = duration.label;
//         statusCell.appendChild(badge);
//         row.appendChild(statusCell);

//         addCell(row, getField(item, "purchase_date", "purchaseDate", "—"));

//         const price = getField(
//             item, "last_purchase_price", "lastPurchasePrice"
//         );
//         addCell(row, price == null ? "—" : money(price));

//         addCell(
//             row,
//             getField(item, "estimated_refill_date", "estimatedRefillDate", "—")
//         );

//         addCell(
//             row,
//             getField(item, "reminder_days_before", "reminderDaysBefore", 3)
//         );

//         addCell(
//             row,
//             formatDate(
//                 getField(item, "next_stock_check_at", "nextStockCheckAt")
//             )
//         );

//         body.appendChild(row);

//         if (duration.color !== "green") {
//             lowCount++;

//             const alert = document.createElement("li");
//             alert.className = duration.color === "red" ? "critical" : "";
//             alert.textContent =
//                 `${name}: ${quantity} ${unit} remaining — ${duration.label}`;

//             alerts.appendChild(alert);
//         }
//     });

//     if (!lowCount) {
//         const alert = document.createElement("li");
//         alert.textContent = "All tracked items have more than 7 estimated days remaining.";
//         alerts.appendChild(alert);
//     }

//     document.getElementById("totalItems").textContent = items.length;
//     document.getElementById("lowItems").textContent = lowCount;
// }

// function renderUpcoming(data) {
//     const list = document.getElementById("upcomingList");
//     list.replaceChildren();

//     const items = Array.isArray(data) ? data : (data?.items || []);

//     // If the endpoint returns purchase records, display those records.
//     // Otherwise derive upcoming refills from the household items.
//     if (!items.length) {
//         list.textContent = "No upcoming purchases returned by the server.";
//         document.getElementById("upcomingItems").textContent = "0";
//         return;
//     }

//     document.getElementById("upcomingItems").textContent = items.length;

//     items.forEach(item => {
//         const li = document.createElement("li");
//         const name = getField(item, "item_name", "itemName",
//             getField(item, "name", "name", "Item"));

//         const date = getField(
//             item, "estimated_refill_date", "estimatedRefillDate",
//             getField(item, "expectedDate", "expectedDate", null)
//         );

//         li.textContent = `${name} — ${date || "Date not set"}`;
//         list.appendChild(li);
//     });
// }

// async function loadDashboard() {
//     const token = localStorage.getItem("grocerymate_token");
//     const role = localStorage.getItem("grocerymate_role");

//     if (!token) {
//         window.location.href = "index.html";
//         return;
//     }

//     if (role && role.toUpperCase() !== "USER") {
//         window.location.href = "vendor-dashboard.html";
//         return;
//     }

//     try {
//         const [itemsResponse, spendingResponse, upcomingResponse] =
//             await Promise.all([
//                 apiRequest(ITEMS_API),
//                 apiRequest(SPENDING_API),
//                 apiRequest(UPCOMING_API)
//             ]);

//         const items = Array.isArray(itemsResponse)
//             ? itemsResponse
//             : (itemsResponse?.items || []);

//         renderItems(items);

//         const spending = typeof spendingResponse === "number"
//             ? spendingResponse
//             : (spendingResponse?.total ??
//                spendingResponse?.amount ?? 0);

//         document.getElementById("monthlySpend").textContent =
//             money(spending);

//         renderUpcoming(upcomingResponse);

//         showMessage("Dashboard data loaded successfully.", true);
//     } catch (error) {
//         showMessage(
//             "Could not load dashboard data: " + error.message
//         );
//     }
// }

// document.getElementById("itemForm").addEventListener("submit",
//     async function(event) {
//         event.preventDefault();

//         const item = {
//             item_name: document.getElementById("itemName").value.trim(),
//             quantity_remaining:
//                 Number(document.getElementById("quantity").value),
//             unit: document.getElementById("unit").value,
//             purchase_date:
//                 document.getElementById("purchaseDate").value || null,
//             last_purchase_price:
//                 document.getElementById("lastPrice").value === ""
//                     ? null
//                     : Number(document.getElementById("lastPrice").value),
//             expected_duration_days:
//                 Number(document.getElementById("duration").value),
//             estimated_refill_date:
//                 document.getElementById("refillDate").value || null,
//             reminder_days_before:
//                 Number(document.getElementById("reminderDays").value),
//             next_stock_check_at:document.getElementById("stockCheck").value? document.getElementById("stockCheck").value + ":00": null
//         };

//         try {
//             await apiRequest(ITEMS_API, {
//                 method: "POST",
//                 body: JSON.stringify(item)
//             });

//             showMessage("Household essential saved successfully.", true);
//             event.target.reset();

//             document.getElementById("duration").value = 30;
//             document.getElementById("reminderDays").value = 3;

//             await loadDashboard();
//         } catch (error) {
//             showMessage("Could not save item: " + error.message);
//         }
//     }
// );

// loadDashboard();


