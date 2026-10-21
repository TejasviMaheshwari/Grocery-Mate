
const API_BASE =
    window.location.protocol === "file:"
        ? "http://localhost:8080/api"
        : window.location.port === "8080"
            ? "/api"
            : "http://localhost:8080/api";

// Read the CSRF cookie created by Spring Security.
function getCookie(name) {
    const prefix = name + "=";
    const cookie = document.cookie
        .split("; ")
        .find(value => value.startsWith(prefix));

    return cookie
        ? decodeURIComponent(cookie.substring(prefix.length))
        : null;
}

// Load/initialize the CSRF token before sending protected requests.
// Assumes GET /api/csrf is configured in your Spring Boot backend.
async function getCsrfToken() {
    const response = await fetch(`${API_BASE}/csrf`, {
        method: "GET",
        credentials: "same-origin",
        headers: {
            Accept: "application/json"
        }
    });

    if (!response.ok) {
        throw new Error("Could not initialize security token.");
    }

    const data = await response.json();

    // CookieCsrfTokenRepository normally uses the XSRF-TOKEN cookie.
    return getCookie("XSRF-TOKEN") || data.token || null;
}

async function apiRequest(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = {
        Accept: "application/json",
        ...options.headers
    };

    if (options.body !== undefined) {
        headers["Content-Type"] = "application/json";
    }

    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
        const csrfToken = await getCsrfToken();

        if (!csrfToken) {
            throw new Error(
                "CSRF token missing. Check your Spring Security configuration."
            );
        }

        headers["X-XSRF-TOKEN"] = csrfToken;
    }

    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        method,
        headers,
        credentials: "same-origin"
    });

    const text = await response.text();
    let data = null;

    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            data = text;
        }
    }

    if (!response.ok) {
        if (response.status === 401) {
            throw new Error("Please log in again.");
        }

        if (response.status === 403) {
            throw new Error(
                "Access denied or CSRF validation failed. Check the backend logs."
            );
        }

        const message =
            typeof data === "object" && data !== null
                ? data.message || data.error
                : null;

        throw new Error(message || `Request failed: HTTP ${response.status}`);
    }

    return data;
}

// Keep this function for formatting prices throughout the dashboard.
function money(amount) {
    return new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR"
    }).format(Number(amount || 0));
}

// const API_BASE = window.location.protocol === "file:" ? "http://localhost:8080/api" :(window.location.port === "8080" ? "/api": "http://localhost:8080/api") ;

// async function apiRequest(path, options = {}) {
//     const token = localStorage.getItem("grocerymate_token");

//     const response = await fetch(`${API_BASE}${path}`, {
//         ...options,
//         headers: {
//             "Content-Type": "application/json",
//             ...(token ? { Authorization: `Bearer ${token}` } : {}),
//             ...options.headers
//         }
//     });

//     if (response.status === 401 || response.status === 403) {
//         throw new Error("Session expired or access denied.");
//     }

//     const text = await response.text();
//     const data = text ? JSON.parse(text) : null;

//     if (!response.ok) {
//         throw new Error(data?.message || `HTTP ${response.status}`);
//     }

//     return data;
// }

// function money(amount) {
//     return new Intl.NumberFormat("en-IN", {
//         style: "currency",
//         currency: "INR"
//     }).format(Number(amount || 0));
// }

// function logout() {
//     localStorage.removeItem("grocerymate_token");
//     localStorage.removeItem("grocerymate_role");
//     localStorage.removeItem("grocerymate_user");
//     window.location.href = "login.html";
// }
