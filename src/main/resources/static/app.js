/*
 * GroceryMate authentication UI.
 * Expected Spring Boot endpoints (change API_BASE / paths below if your backend differs):
 * POST /api/auth/register/user
 * POST /api/auth/register/vendor
 * POST /api/auth/login
 *
 * This frontend never connects directly to MySQL. Password hashing and SQL writes belong
 * in the Java backend. Do not store plain-text passwords or trust a role sent by the browser.
 */


const API_BASE = "http://localhost:8080";

const ENDPOINTS = {
  userRegister: `${API_BASE}/api/auth/signup/user`,
  vendorRegister: `${API_BASE}/api/auth/signup/vendor`,
  login: `${API_BASE}/api/auth/login`
};



const form = document.getElementById("authForm");
const statusMessage = document.getElementById("statusMessage");
const signupFields = document.getElementById("signupFields");
const vendorFields = document.getElementById("vendorFields");
const householdFields = document.getElementById("householdFields");
const submitButton = document.getElementById("submitButton");
const submitLabel = document.getElementById("submitLabel");
const password = document.getElementById("password");
const togglePassword = document.getElementById("togglePassword");
const switchModeButton = document.getElementById("switchMode");
const switchPromptText = document.getElementById("switchPromptText");
const passwordHint = document.getElementById("passwordHint");
const termsText = document.getElementById("termsText");

let currentRole = "user";
let currentMode = "signup";

function setRole(role) {
  currentRole = role;
  document.querySelectorAll("[data-role]").forEach(button => {
    const selected = button.dataset.role === role;
    button.classList.toggle("active", selected);
    button.setAttribute("aria-selected", String(selected));
  });
  vendorFields.hidden = role !== "vendor" || currentMode !== "signup";
  householdFields.hidden = role !== "user" || currentMode !== "signup";
  updateLabels();
  clearStatus();
}

function setMode(mode) {
  currentMode = mode;
  document.querySelectorAll("[data-mode]").forEach(button => {
    const selected = button.dataset.mode === mode;
    button.classList.toggle("active", selected);
    button.setAttribute("aria-selected", String(selected));
  });
  signupFields.hidden = mode === "login";
  password.autocomplete = mode === "login" ? "current-password" : "new-password";
  passwordHint.hidden = mode === "login";
  vendorFields.hidden = mode !== "signup" || currentRole !== "vendor";
  householdFields.hidden = mode !== "signup" || currentRole !== "user";
  clearErrors();
  clearStatus();
  updateLabels();
}

function updateLabels() {
  const roleName = currentRole === "vendor" ? "vendor" : "household";
  submitLabel.textContent = currentMode === "login" ? "Log in to GroceryMate" : `Create ${roleName} account`;
  switchPromptText.textContent = currentMode === "login" ? "New to GroceryMate?" : "Already have an account?";
  switchModeButton.textContent = currentMode === "login" ? "Create account" : "Log in";
  termsText.hidden = currentMode === "login";
}

function clearStatus() {
  statusMessage.hidden = true;
  statusMessage.textContent = "";
  statusMessage.className = "status-message";
}
function showStatus(message, type = "error") {
  statusMessage.textContent = message;
  statusMessage.className = `status-message ${type}`;
  statusMessage.hidden = false;
}
function clearErrors() {
  document.querySelectorAll(".field-error").forEach(node => node.textContent = "");
  document.querySelectorAll("input.invalid").forEach(input => input.classList.remove("invalid"));
}
function setFieldError(name, message) {
  const error = document.querySelector(`[data-error-for="${name}"]`);
  const input = document.getElementById(name);
  if (error) error.textContent = message;
  if (input) input.classList.add("invalid");
}
function value(name) {
  return document.getElementById(name).value.trim();
}
function validate() {
  clearErrors();
  let valid = true;
  const email = value("email");
  const pass = password.value;
  
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    setFieldError("email", "Enter a valid email address.");
    valid = false;
  }

  if (!pass || (currentMode === "signup" && pass.length < 8)) {
    setFieldError("password", currentMode === "login" ? "Enter your password." : "Password must be at least 8 characters.");
    valid = false;
  }
  if (currentMode === "signup") {
    if (!value("fullName")) { setFieldError("fullName", "Enter your full name."); valid = false; }
    if (currentRole === "vendor" && !value("shopName")) { setFieldError("shopName", "Enter your shop name."); valid = false; }
    if (currentRole === "user" && !value("householdName")) { setFieldError("householdName", "Enter your household name."); valid = false; }
    const phone = value("phone");
    if (phone && !/^[0-9+\-\s()]{7,15}$/.test(phone)) { setFieldError("phone", "Enter a valid phone number."); valid = false; }
    const pin = value("pincode");
    if (pin && !/^[0-9]{6}$/.test(pin)) { setFieldError("pincode", "PIN code must contain 6 digits."); valid = false; }
  }
  return valid;
}

function buildRegistrationPayload() {
  const common = {
    fullName: value("fullName"),
    email: value("email").toLowerCase(),
    phone: value("phone") || null,
    password: password.value
  };
  if (currentRole === "vendor") {
    return {
      ...common,
      shopName: value("shopName"),
      address: value("address") || null,
      city: value("city") || null,
      state: value("state") || null,
      pincode: value("pincode") || null
    };
  }
  return {
    ...common,
    householdName: value("householdName"),
    address: value("address") || null,
    city: value("city") || null,
    state: value("state") || null,
    pincode: value("pincode") || null
  };
}

async function readResponse(response) {
  const text = await response.text();
  if (!text) return {};
  try { return JSON.parse(text); }
  catch { return { message: text }; }
}

form.addEventListener("submit", async event => {
  event.preventDefault();
  clearStatus();
  if (!validate()) return;

  submitButton.disabled = true;
  submitLabel.textContent = currentMode === "login" ? "Logging in…" : "Creating account…";
  try {
    let url;
    let payload;
    if (currentMode === "signup") {
      url = currentRole === "vendor" ? ENDPOINTS.vendorRegister : ENDPOINTS.userRegister;
      payload = buildRegistrationPayload();
    } else {
      url = ENDPOINTS.login;
      // Backend should determine role from its own stored account/profile data.
      payload = { email: value("email").toLowerCase(), password: password.value };
    }

    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", "Accept": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await readResponse(response);

    if (!response.ok) {
      const message = data.message || data.error || (response.status === 409
        ? "An account with these details already exists."
        : `Request failed (${response.status}). Check that the Java backend is running and its endpoint matches app.js.`);
      showStatus(message, "error");
      return;
    }

    if (currentMode === "signup") {
      showStatus(data.message || "Account created successfully. You can now log in.", "success");
      setMode("login");
      password.value = "";
      // Keep email filled to make the next step easy.
    } else {
      if (data.householdId) {
        localStorage.setItem("grocerymate_household_id", data.householdId);
      }
      showStatus(data.message || "Login successful.", "success");
      /*
       * Adapt this section to your backend's auth strategy:
       * - For cookie/session auth, let the server set an HttpOnly cookie.
       * - For token auth, use a secure design; avoid storing sensitive tokens in localStorage.
       * The backend response may return a role or redirect path; do not trust a client-side role
       * selector as authorization.
       */ 
    // Save auth token and role required by the dashboards
      if (data.token) {
        localStorage.setItem("grocerymate_token", data.token);
      } else {
        // Fallback so it doesn't immediately bounce if your backend doesn't return a token yet
        localStorage.setItem("grocerymate_token", "dummy_session_token");
      }

      const role = (data.role || currentRole).toUpperCase();
      localStorage.setItem("grocerymate_role", role);

      if (data.redirectUrl) {
        window.location.assign(data.redirectUrl);
      } else if (role === "VENDOR") {
        window.location.href = "vendor-dashboard.html";
      } else {
        window.location.href = "user-dashboard.html";
      }
    //   if (data.redirectUrl) {
    //     window.location.assign(data.redirectUrl);
    //   } else if (data.role === "VENDOR") {
    //     window.location.href = "vendor-dashboard.html";
    //     showStatus(data.message || "Login successful. Vendor dashboard can be connected next.", "success");
    //   } else if (data.role === "USER" || data.role === "HOUSEHOLD") {
    //     window.location.href = "user-dashboard.html";
    //     showStatus(data.message || "Login successful. Household dashboard can be connected next.", "success");
    //   }
    // }
    }
  } catch (error) {
    showStatus(`Could not connect to the Java backend at ${API_BASE}. Start Spring Boot and check the endpoint/CORS settings.`, "error");
    console.error("GroceryMate auth request failed:", error);
  } finally {
    submitButton.disabled = false;
    updateLabels();
  }
});

document.querySelectorAll("[data-role]").forEach(button => {
  button.addEventListener("click", () => setRole(button.dataset.role));
});
document.querySelectorAll("[data-mode]").forEach(button => {
  button.addEventListener("click", () => setMode(button.dataset.mode));
});
switchModeButton.addEventListener("click", () => setMode(currentMode === "login" ? "signup" : "login"));
togglePassword.addEventListener("click", () => {
  const show = password.type === "password";
  password.type = show ? "text" : "password";
  togglePassword.textContent = show ? "Hide" : "Show";
});

setRole("user");
setMode("signup");
