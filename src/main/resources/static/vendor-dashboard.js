/* GroceryMate vendor product management.
   Uses the existing apiRequest helper and session-based authentication. */

"use strict";

const productForm = document.getElementById("productForm");
const productsBody = document.getElementById("productsBody");
const productCount = document.getElementById("productCount");
const message = document.getElementById("message");

const editModal = document.getElementById("editProductModal");
const editForm = document.getElementById("editProductForm");
const editMessage = document.getElementById("editMessage");
const saveEditButton = document.getElementById("saveEditButton");

let vendorProducts = [];
let lastFocusedElement = null;

function showMessage(text, isError = false) {
    if (!message) return;
    message.textContent = text;
    message.classList.toggle("gm-error-text", isError);
    message.classList.toggle("gm-success-text", !isError && Boolean(text));
}

function showEditMessage(text, isError = false) {
    editMessage.textContent = text;
    editMessage.classList.toggle("gm-error-text", isError);
    editMessage.classList.toggle("gm-success-text", !isError && Boolean(text));
}

function escapeText(value) {
    return value == null ? "" : String(value);
}

async function loadVendorDashboard() {
    try {
        const session = await apiRequest("/auth/session");
        if (String(session.role || "").toUpperCase() !== "VENDOR") {
            window.location.replace(
                String(session.role || "").toUpperCase() === "USER"
                    ? "user-dashboard.html"
                    : "index.html"
            );
            return;
        }

        // These values are for UI convenience only; authorization is enforced by the server.
        localStorage.setItem("grocerymate_vendor_id", String(session.vendorId ?? session.id ?? ""));
        localStorage.setItem("grocerymate_role", "VENDOR");

        await Promise.allSettled([
            loadProducts(),
            loadRequests(),
            loadOrders()
        ]);
    } catch (error) {
        showMessage(error.message || "Could not verify vendor session. Please log in again.", true);
    }
}

async function loadProducts() {
    try {
        const result = await apiRequest("/vendor/products");
        vendorProducts = Array.isArray(result) ? result : [];
        renderProducts();
    } catch (error) {
        showMessage(error.message || "Could not load products.", true);
        throw error;
    }
}

function renderProducts() {
    productsBody.replaceChildren();
    productCount.textContent = String(vendorProducts.length);

    if (vendorProducts.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 6;
        cell.textContent = "No products added yet.";
        row.appendChild(cell);
        productsBody.appendChild(row);
        return;
    }

    vendorProducts.forEach(product => {
        const row = document.createElement("tr");

        const values = [
            product.productName,
            product.category,
            `₹${Number(product.price).toFixed(2)}`,
            `${product.stockQuantity ?? 0} ${product.unit || ""}`.trim(),
            product.available ? "Available" : "Unavailable"
        ];

        values.forEach(value => {
            const cell = document.createElement("td");
            cell.textContent = escapeText(value);
            row.appendChild(cell);
        });

        const actionsCell = document.createElement("td");
        actionsCell.className = "gm-product-actions";

        const editButton = document.createElement("button");
        editButton.type = "button";
        editButton.className = "gm-button gm-button--small gm-button--primary";
        editButton.textContent = "Edit";
        editButton.addEventListener("click", () => openEditModal(product.productId));

        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "gm-button gm-button--small gm-button--danger";
        deleteButton.textContent = "Delete";
        deleteButton.addEventListener("click", () => deleteProduct(product.productId, product.productName));

        actionsCell.append(editButton, deleteButton);
        row.appendChild(actionsCell);
        productsBody.appendChild(row);
    });
}

function openEditModal(productId) {
    const product = vendorProducts.find(item => String(item.productId) === String(productId));
    if (!product) {
        showMessage("That product could not be found. Refresh the list and try again.", true);
        return;
    }

    lastFocusedElement = document.activeElement;
    document.getElementById("editProductId").value = product.productId;
    document.getElementById("editProductName").value = product.productName || "";
    document.getElementById("editCategory").value = product.category || "";
    document.getElementById("editPrice").value = product.price ?? "";
    document.getElementById("editStock").value = product.stockQuantity ?? 0;
    document.getElementById("editUnit").value = product.unit || "piece";
    document.getElementById("editDescription").value = product.description || "";
    document.getElementById("editAvailable").checked = Boolean(product.available);

    showEditMessage("");
    editModal.classList.add("is-open");
    editModal.setAttribute("aria-hidden", "false");
    document.body.classList.add("gm-modal-open");
    document.getElementById("editProductName").focus();
}

function closeEditModal() {
    editModal.classList.remove("is-open");
    editModal.setAttribute("aria-hidden", "true");
    document.body.classList.remove("gm-modal-open");
    editForm.reset();
    showEditMessage("");
    if (lastFocusedElement && typeof lastFocusedElement.focus === "function") {
        lastFocusedElement.focus();
    }
}

async function saveProductChanges(event) {
    event.preventDefault();

    const productId = document.getElementById("editProductId").value;
    const updatedProduct = {
        productName: document.getElementById("editProductName").value.trim(),
        category: document.getElementById("editCategory").value.trim(),
        price: Number(document.getElementById("editPrice").value),
        stockQuantity: Number(document.getElementById("editStock").value),
        unit: document.getElementById("editUnit").value.trim() || "piece",
        description: document.getElementById("editDescription").value.trim(),
        available: document.getElementById("editAvailable").checked
    };

    if (!updatedProduct.productName || !updatedProduct.category ||
        !Number.isFinite(updatedProduct.price) || updatedProduct.price <= 0 ||
        !Number.isInteger(updatedProduct.stockQuantity) || updatedProduct.stockQuantity < 0) {
        showEditMessage("Enter a name, category, valid price, and whole-number stock quantity.", true);
        return;
    }

    saveEditButton.disabled = true;
    saveEditButton.textContent = "Saving...";
    try {
        await apiRequest(`/vendor/products/${encodeURIComponent(productId)}`, {
            method: "PUT",
            body: JSON.stringify(updatedProduct)
        });
        closeEditModal();
        await loadProducts();
        showMessage("Product updated successfully.");
    } catch (error) {
        showEditMessage(error.message || "Could not update product.", true);
    } finally {
        saveEditButton.disabled = false;
        saveEditButton.textContent = "Save Changes";
    }
}

async function deleteProduct(productId, productName) {
    const confirmed = window.confirm(
        `Delete "${productName || "this product"}"? This action cannot be undone.`
    );
    if (!confirmed) return;

    try {
        await apiRequest(`/vendor/products/${encodeURIComponent(productId)}`, {
            method: "DELETE"
        });
        await loadProducts();
        showMessage("Product deleted successfully.");
    } catch (error) {
        showMessage(error.message || "Could not delete product.", true);
    }
}

productForm?.addEventListener("submit", async event => {
    event.preventDefault();

    const form = event.currentTarget;
    const saveButton = form.querySelector('button[type="submit"]');
    const product = {
        productName: document.getElementById("productName").value.trim(),
        category: document.getElementById("category").value.trim(),
        price: Number(document.getElementById("price").value),
        stockQuantity: Number(document.getElementById("stock").value),
        unit: document.getElementById("unit").value.trim() || "piece",
        description: document.getElementById("description").value.trim(),
        available: true
    };

    if (!product.productName || !product.category ||
        !Number.isFinite(product.price) || product.price <= 0 ||
        !Number.isInteger(product.stockQuantity) || product.stockQuantity < 0) {
        showMessage("Please enter valid product details.", true);
        return;
    }

    saveButton.disabled = true;
    try {
        await apiRequest("/vendor/products", {
            method: "POST",
            body: JSON.stringify(product)
        });
        form.reset();
        document.getElementById("unit").value = "piece";
        await loadProducts();
        showMessage("Product saved successfully.");
    } catch (error) {
        showMessage(error.message || "Could not save product.", true);
    } finally {
        saveButton.disabled = false;
    }
});

editForm?.addEventListener("submit", saveProductChanges);
document.getElementById("closeEditModal")?.addEventListener("click", closeEditModal);
document.getElementById("cancelEdit")?.addEventListener("click", closeEditModal);
editModal?.addEventListener("click", event => {
    if (event.target.matches("[data-close-modal='true']")) closeEditModal();
});
document.addEventListener("keydown", event => {
    if (event.key === "Escape" && editModal.classList.contains("is-open")) {
        closeEditModal();
    }
});

/* ---------------- Purchase Requests & Orders ---------------- */

const requestsList = document.getElementById("requestsList");
const requestsMessage = document.getElementById("requestsMessage");
const ordersList = document.getElementById("vendorOrdersList");
const ordersMessage = document.getElementById("ordersMessage");

const requestCountElement = document.getElementById("requestCount");

const refreshRequestsButton =
    document.getElementById("refreshRequestsButton");

const refreshOrdersButton =
    document.getElementById("refreshOrdersButton");

let vendorRequests = [];
let vendorOrders = [];

function getValue(object, ...keys) {
    for (const key of keys) {
        if (object?.[key] !== undefined && object[key] !== null) {
            return object[key];
        }
    }
    return null;
}

function setWorkflowMessage(element, text, isError = false) {
    if (!element) return;

    element.textContent = text;
    element.classList.toggle("gm-error-text", isError);
    element.classList.toggle("gm-success-text", !isError && Boolean(text));
}

function createTextElement(tag, text, className = "") {
    const element = document.createElement(tag);
    element.textContent = text == null ? "" : String(text);

    if (className) {
        element.className = className;
    }

    return element;
}

async function loadRequests() {
    if (!requestsList) return;

    requestsList.replaceChildren(
        createTextElement("li", "Loading purchase requests...")
    );

    try {
        const result = await apiRequest("/vendor/requests");

        vendorRequests = Array.isArray(result) ? result : [];

        if (requestCountElement) {
            requestCountElement.textContent =
                String(vendorRequests.length);
        }

        renderRequests();
        setWorkflowMessage(requestsMessage, "");

    } catch (error) {
        vendorRequests = [];

        if (requestCountElement) {
            requestCountElement.textContent = "—";
        }

        requestsList.replaceChildren(
            createTextElement("li", "Could not load purchase requests.")
        );

        setWorkflowMessage(
            requestsMessage,
            error.message || "Could not load requests.",
            true
        );
    }
}

function renderRequests() {
    requestsList.replaceChildren();

    if (vendorRequests.length === 0) {
        requestsList.appendChild(
            createTextElement("li", "No open purchase requests assigned to you.")
        );
        return;
    }

    vendorRequests.forEach(request => {
        const requestId = getValue(
            request, "requestId", "request_id", "id"
        );

        const itemName = getValue(
            request, "requestedItemName", "requested_item_name"
        ) ?? "Unnamed item";

        const quantity = getValue(
            request, "requestedQuantity", "requested_quantity"
        ) ?? "—";

        const unit = getValue(request, "unit") ?? "";

        const status = getValue(request, "status") ?? "OPEN";

        const card = document.createElement("li");
        card.className = "gm-workflow-card";

        const heading = document.createElement("div");
        heading.className = "gm-workflow-card__heading";

        heading.append(
            createTextElement("h3", `Request #${requestId}`),
            createTextElement("span", status, "gm-status-badge")
        );

        const details = document.createElement("p");
        details.textContent =
            `Item: ${itemName} · Quantity: ${quantity} ${unit}`;

        const productLabel = createTextElement("label", "Select your product");
        const productSelect = document.createElement("select");
        productSelect.required = true;

        const placeholder = document.createElement("option");
        placeholder.value = "";
        placeholder.textContent = "Choose a product...";
        productSelect.appendChild(placeholder);

        const requestUnit = String(unit).trim().toLowerCase();
        const requestedQuantity = Number(quantity);

        const eligibleProducts = vendorProducts.filter(product => {
            const productUnit =
                String(product.unit ?? "").trim().toLowerCase();

            return product.available === true
                && productUnit === requestUnit
                && Number(product.stockQuantity) >= requestedQuantity;
        });

        eligibleProducts.forEach(product => {
            const option = document.createElement("option");
            option.value = String(product.productId);
            option.textContent =
                `${product.productName} — ₹${Number(product.price).toFixed(2)} ` +
                `(${product.stockQuantity} ${product.unit} in stock)`;

            productSelect.appendChild(option);
        });

        productLabel.htmlFor = `request-product-${requestId}`;
        productSelect.id = `request-product-${requestId}`;

        const acceptButton = document.createElement("button");
        acceptButton.type = "button";
        acceptButton.className = "gm-button gm-button--primary";
        acceptButton.textContent = "Accept Request";

        if (eligibleProducts.length === 0) {
            acceptButton.disabled = true;

            const note = createTextElement(
                "p",
                "No available product has the matching unit and sufficient stock.",
                "gm-error-text"
            );

            card.append(heading, details, productLabel, productSelect, note,
                acceptButton);
        } else {
            card.append(heading, details, productLabel, productSelect,
                acceptButton);
        }

        acceptButton.addEventListener("click", async () => {
            const productId = productSelect.value;

            if (!productId) {
                setWorkflowMessage(
                    requestsMessage,
                    "Select a product before accepting the request.",
                    true
                );
                return;
            }

            acceptButton.disabled = true;
            acceptButton.textContent = "Accepting...";

            try {
                await apiRequest(
                    `/vendor/requests/${encodeURIComponent(requestId)}/accept`,
                    {
                        method: "POST",
                        body: JSON.stringify({
                            productId: Number(productId)
                        })
                    }
                );

                setWorkflowMessage(
                    requestsMessage,
                    `Request #${requestId} accepted. The order has been created.`
                );

                await Promise.allSettled([
                    loadProducts(),
                    loadRequests(),
                    loadOrders()
                ]);

            } catch (error) {
                setWorkflowMessage(
                    requestsMessage,
                    error.message || "Could not accept this request.",
                    true
                );

                acceptButton.disabled = false;
                acceptButton.textContent = "Accept Request";
            }
        });

        requestsList.appendChild(card);
    });
}

async function loadOrders() {
    if (!ordersList) return;

    ordersList.replaceChildren(
        createTextElement("li", "Loading orders...")
    );

    try {
        const result = await apiRequest("/vendor/orders");

        vendorOrders = Array.isArray(result) ? result : [];

        renderOrders();
        setWorkflowMessage(ordersMessage, "");

    } catch (error) {
        vendorOrders = [];

        ordersList.replaceChildren(
            createTextElement("li", "Could not load orders.")
        );

        setWorkflowMessage(
            ordersMessage,
            error.message || "Could not load orders.",
            true
        );
    }
}


function renderOrders() {
    ordersList.replaceChildren();

    if (vendorOrders.length === 0) {
        ordersList.appendChild(
            createTextElement("li", "No vendor orders yet.")
        );
        return;
    }

    vendorOrders.forEach(order => {
        const orderId = getValue(order, "vendorOrderId", "vendor_order_id");
        const requestId = getValue(
            order, "purchaseRequestId", "purchase_request_id"
        );
        const productId = getValue(
            order, "vendorProductId", "vendor_product_id"
        );

        const status = String(
            getValue(order, "orderStatus", "order_status") ?? "UNKNOWN"
        ).toUpperCase();

        const quantity = getValue(order, "quantity");
        const unit = getValue(order, "unit") ?? "";
        const unitPrice = getValue(order, "unitPrice", "unit_price");
        const totalAmount = getValue(order, "totalAmount", "total_amount");
        const orderedAt = getValue(order, "orderedAt", "ordered_at");

        const card = document.createElement("li");
        card.className = "gm-workflow-card";

        const heading = document.createElement("div");
        heading.className = "gm-workflow-card__heading";

        heading.append(
            createTextElement("h3", `Order #${orderId ?? "—"}`),
            createTextElement("span", status, "gm-status-badge")
        );

        const details = document.createElement("div");

        const orderDetails = [
            `Request: #${requestId ?? "—"}`,
            `Product: #${productId ?? "—"}`,
            quantity != null ? `Quantity: ${quantity} ${unit}` : null,
            unitPrice != null ? `Unit price: ${money(unitPrice)}` : null,
            totalAmount != null ? `Total: ${money(totalAmount)}` : null,
            orderedAt
                ? `Ordered: ${new Date(orderedAt).toLocaleString()}`
                : null
        ].filter(Boolean);

        details.textContent = orderDetails.join(" · ");

        card.append(heading, details);

        const nextStatus = {
            PLACED: "PROCESSING",
            PROCESSING: "SHIPPED",
            SHIPPED: "DELIVERED"
        }[status];

        if (nextStatus) {
            const updateButton = document.createElement("button");
            updateButton.type = "button";
            updateButton.className = "gm-button gm-button--primary";
            updateButton.textContent = `Mark ${nextStatus}`;

            updateButton.addEventListener("click", async () => {
                const confirmed = nextStatus !== "DELIVERED"
                    || window.confirm(
                        `Confirm delivery of order #${orderId}? ` +
                        "This will update household inventory and purchase history."
                    );

                if (!confirmed) return;

                updateButton.disabled = true;
                updateButton.textContent = "Updating...";

                try {
                    await apiRequest(
                        `/vendor/orders/${encodeURIComponent(orderId)}/status`,
                        {
                            method: "PATCH",
                            body: JSON.stringify({ status: nextStatus })
                        }
                    );

                    setWorkflowMessage(
                        ordersMessage,
                        `Order #${orderId} updated to ${nextStatus}.`
                    );

                    await Promise.allSettled([
                        loadOrders(),
                        loadRequests(),
                        loadProducts()
                    ]);

                } catch (error) {
                    setWorkflowMessage(
                        ordersMessage,
                        error.message || "Could not update order status.",
                        true
                    );

                    updateButton.disabled = false;
                    updateButton.textContent = `Mark ${nextStatus}`;
                }
            });

            card.appendChild(updateButton);

        } else if (status === "DELIVERED") {
            card.appendChild(
                createTextElement(
                    "p",
                    "Delivered successfully. Household inventory has been updated."
                )
            );
        } else if (status === "CANCELLED") {
            card.appendChild(
                createTextElement("p", "This order has been cancelled.")
            );
        } else {
            card.appendChild(
                createTextElement(
                    "p",
                    "This order has an unrecognized status. Please check the backend."
                )
            );
        }

        ordersList.appendChild(card);
    });
}


refreshRequestsButton?.addEventListener("click", loadRequests);
refreshOrdersButton?.addEventListener("click", loadOrders);

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

window.logout = logout;
loadVendorDashboard();
