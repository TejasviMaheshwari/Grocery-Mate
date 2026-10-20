
async function loadVendorDashboard() {
    try {
        const role = localStorage.getItem("grocerymate_role");

        if (!localStorage.getItem("grocerymate_token")) {
            window.location.href = "index.html";
            return;
        }

        if (role && role.toUpperCase() !== "VENDOR") {
            window.location.href = "user-dashboard.html";
            return;
        }

        const products = await apiRequest("/vendor/products");

        document.getElementById("productCount").textContent =
            products.length;

        const body = document.getElementById("productsBody");
        body.replaceChildren();

        products.forEach(product => {
            const row = document.createElement("tr");

            [
                product.name,
                product.category,
                money(product.price),
                product.stockQuantity
            ].forEach(value => {
                const cell = document.createElement("td");
                cell.textContent = value ?? "";
                row.appendChild(cell);
            });

            body.appendChild(row);
        });

        // Purchase requests are optional until that feature is implemented.
        try {
            const requests = await apiRequest("/vendor/purchase-requests");

            document.getElementById("requestCount").textContent =
                requests.length;

            const list = document.getElementById("requestsList");
            list.replaceChildren();

            requests.forEach(request => {
                const li = document.createElement("li");
                li.textContent =
                    `${request.productName} — ${request.status}`;
                list.appendChild(li);
            });
        } catch {
            document.getElementById("requestsList").textContent =
                "Purchase requests are not connected yet.";
        }

    } catch (error) {
        document.getElementById("message").textContent =
            "Unable to load products: " + error.message;
    }
}

document.getElementById("productForm").addEventListener("submit",
    async event => {
        event.preventDefault();

        const product = {
            name: document.getElementById("productName").value.trim(),
            category: document.getElementById("category").value.trim(),
            price: Number(document.getElementById("price").value),
            stockQuantity: Number(document.getElementById("stock").value)
        };

        try {
            await apiRequest("/vendor/products", {
                method: "POST",
                body: JSON.stringify(product)
            });

            document.getElementById("message").textContent =
                "Product saved successfully!";

            event.target.reset();
            await loadVendorDashboard();
        } catch (error) {
            document.getElementById("message").textContent =
                error.message;
        }
    }
);

loadVendorDashboard();
