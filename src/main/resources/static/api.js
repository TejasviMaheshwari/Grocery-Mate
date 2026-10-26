const LIVE_BACKEND_URL = "https://grocerymate-backend.onrender.com";

const IS_LOCAL =
    window.location.hostname === "localhost" ||
    window.location.hostname === "127.0.0.1";

const API_BASE = IS_LOCAL
    ? (window.location.port === "8080"
        ? ""
        : "http://localhost:8080")
    : window.location.origin;

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

// async function getCsrfToken() {
//     const response = await fetch(`${API_BASE}/csrf`, {
//         method: "GET",
//         credentials: "include",
//         // credentials: "same-origin",
//         headers: {
//             Accept: "application/json"
//         }
//     });
//
//     if (!response.ok) {
//         throw new Error("Could not initialize security token.");
//     }
//
//     const data = await response.json();
//
//     // Prefer the cookie used by CookieCsrfTokenRepository.
//     const cookieToken = getCookie("XSRF-TOKEN");
//     const token = cookieToken || data.token || null;
//
//     if (!token) {
//         throw new Error("CSRF token is missing.");
//     }
//
//     return token;
// }

async function getCsrfToken() {
    const response = await fetch(`${API_BASE}/api/csrf`, {
        method: "GET",
        credentials: "include",
        headers: {
            Accept: "application/json"
        }
    });

    if (!response.ok) {
        console.error(
            "CSRF initialization failed:",
            response.status,
            await response.text()
        );
        throw new Error("Could not initialize security token.");
    }

    const data = await response.json();

    const cookieToken = getCookie("XSRF-TOKEN");
    const token = cookieToken || data.token || null;

    if (!token) {
        console.error("CSRF response did not contain a usable token:", data);
        throw new Error("CSRF token is missing.");
    }

    return token;
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

    const response = await fetch(`${API_BASE}/api${path}`, {
        ...options,
        method,
        headers,
        credentials: "include"
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



async function markAllNotificationsAsRead() {
    return apiRequest("/household/notifications/read-all", {
        method: "PATCH"
    });
}
