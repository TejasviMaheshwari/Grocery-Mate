
const API_BASE = window.location.protocol === "file:" ? "http://localhost:8080/api" :(window.location.port === "8080" ? "/api": "http://localhost:8080/api") ;

async function apiRequest(path, options = {}) {
    const token = localStorage.getItem("grocerymate_token");

    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...options.headers
        }
    });

    if (response.status === 401 || response.status === 403) {
        throw new Error("Session expired or access denied.");
    }

    const text = await response.text();
    const data = text ? JSON.parse(text) : null;

    if (!response.ok) {
        throw new Error(data?.message || `HTTP ${response.status}`);
    }

    return data;
}

function money(amount) {
    return new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR"
    }).format(Number(amount || 0));
}

function logout() {
    localStorage.removeItem("grocerymate_token");
    localStorage.removeItem("grocerymate_role");
    localStorage.removeItem("grocerymate_user");
    window.location.href = "login.html";
}
