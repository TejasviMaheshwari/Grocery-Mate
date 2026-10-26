// Base API URL configuration
const LIVE_BACKEND_URL = "https://grocerymate-backend.onrender.com";

const IS_LOCAL =
  window.location.hostname === "localhost" ||
  window.location.hostname === "127.0.0.1";

const AUTH_BASE = IS_LOCAL
    ? (window.location.port === "8080"
        ? ""
        : "http://localhost:8080")
    : LIVE_BACKEND_URL;

// const AUTH_BASE = IS_LOCAL
//   ? (window.location.port === "8080"
//       ? ""
//       : "http://localhost:8080")
//   : window.location.origin;

const ENDPOINTS = {
  userRegister: `${AUTH_BASE}/api/auth/signup/user`,
  vendorRegister: `${AUTH_BASE}/api/auth/signup/vendor`,
  login: `${AUTH_BASE}/api/auth/login`
};
async function getAuthCsrfToken() {
  // CSRF is disabled on the backend REST API
  return "";
}
// async function getAuthCsrfToken() {
//   try {
//     const response = await fetch(`${AUTH_BASE}/api/csrf`, {
//       method: "GET",
//       credentials: "include",
//       headers: { "Accept": "application/json" }
//     });
//     if (!response.ok) return "";
//     const data = await response.json();
//     return data.token || "";
//   } catch {
//     return "";
//   }
// }

const form = document.getElementById("authForm");
const statusMessage = document.getElementById("statusMessage");
const signupFields = document.getElementById("signupFields");
const vendorFields = document.getElementById("vendorFields");
const householdFields = document.getElementById("householdFields");
const householdActionSelect = document.getElementById("householdAction");
const createHouseholdFields = document.getElementById("createHouseholdFields");
const joinHouseholdFields = document.getElementById("joinHouseholdFields");
const invitationCodeInput = document.getElementById("invitationCode");
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

function updateHouseholdActionFields() {
  if (!householdActionSelect) return;
  const joining = householdActionSelect.value === "JOIN";
  if (createHouseholdFields) createHouseholdFields.hidden = joining;
  if (joinHouseholdFields) joinHouseholdFields.hidden = !joining;
}

function setRole(role) {
  currentRole = role;
  document.querySelectorAll("[data-role]").forEach(button => {
    const selected = button.dataset.role === role;
    button.classList.toggle("active", selected);
    button.setAttribute("aria-selected", String(selected));
  });
  if (vendorFields) vendorFields.hidden = role !== "vendor" || currentMode !== "signup";
  if (householdFields) householdFields.hidden = role !== "user" || currentMode !== "signup";
  updateLabels();
  clearStatus();
  updateHouseholdActionFields();
}

function setMode(mode) {
  currentMode = mode;
  document.querySelectorAll("[data-mode]").forEach(button => {
    const selected = button.dataset.mode === mode;
    button.classList.toggle("active", selected);
    button.setAttribute("aria-selected", String(selected));
  });
  if (signupFields) signupFields.hidden = mode === "login";
  if (password) {
    password.autocomplete = mode === "login" ? "current-password" : "new-password";
  }
  if (passwordHint) passwordHint.hidden = mode === "login";
  if (vendorFields) vendorFields.hidden = mode !== "signup" || currentRole !== "vendor";
  if (householdFields) householdFields.hidden = mode !== "signup" || currentRole !== "user";
  clearErrors();
  clearStatus();
  updateLabels();
  updateHouseholdActionFields();
}

function updateLabels() {
  const roleName = currentRole === "vendor" ? "vendor" : "household";
  if (submitLabel) {
    submitLabel.textContent = currentMode === "login" ? "Log in to GroceryMate" : `Create ${roleName} account`;
  }
  if (switchPromptText) {
    switchPromptText.textContent = currentMode === "login" ? "New to GroceryMate?" : "Already have an account?";
  }
  if (switchModeButton) {
    switchModeButton.textContent = currentMode === "login" ? "Create account" : "Log in";
  }
  if (termsText) termsText.hidden = currentMode === "login";
}

function clearStatus() {
  if (!statusMessage) return;
  statusMessage.hidden = true;
  statusMessage.textContent = "";
  statusMessage.className = "status-message";
}

function showStatus(message, type = "error") {
  if (!statusMessage) return;
  statusMessage.textContent = message;
  statusMessage.className = `status-message ${type}`;
  statusMessage.hidden = false;
}

function clearErrors() {
  document.querySelectorAll(".field-error").forEach(node => (node.textContent = ""));
  document.querySelectorAll("input.invalid").forEach(input => input.classList.remove("invalid"));
}

function setFieldError(name, message) {
  const error = document.querySelector(`[data-error-for="${name}"]`);
  const input = document.getElementById(name);
  if (error) error.textContent = message;
  if (input) input.classList.add("invalid");
}

function value(name) {
  const elem = document.getElementById(name);
  return elem ? elem.value.trim() : "";
}

function validate() {
  clearErrors();
  let valid = true;
  const email = value("email");
  const pass = password ? password.value : "";

  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    setFieldError("email", "Enter a valid email address.");
    valid = false;
  }

  if (!pass || (currentMode === "signup" && pass.length < 8)) {
    setFieldError("password", currentMode === "login" ? "Enter your password." : "Password must be at least 8 characters.");
    valid = false;
  }

  if (currentMode === "signup") {
    if (!value("fullName")) {
      setFieldError("fullName", "Enter your full name.");
      valid = false;
    }
    if (currentRole === "vendor" && !value("shopName")) {
      setFieldError("shopName", "Enter your shop name.");
      valid = false;
    }
    if (currentRole === "user") {
      if (householdActionSelect && householdActionSelect.value === "CREATE") {
        if (!value("householdName")) {
          setFieldError("householdName", "Enter your household name.");
          valid = false;
        }
      } else if (householdActionSelect && householdActionSelect.value === "JOIN") {
        const code = value("invitationCode");
        if (!code) {
          setFieldError("invitationCode", "Enter the invitation code.");
          valid = false;
        } else if (!/^[A-Za-z0-9]{12}$/.test(code)) {
          setFieldError("invitationCode", "Enter the 12-character invitation code.");
          valid = false;
        }
      }
    }
    const phone = value("phone");
    if (phone && !/^[0-9+\-\s()]{7,15}$/.test(phone)) {
      setFieldError("phone", "Enter a valid phone number.");
      valid = false;
    }
    const pin = value("pincode");
    if (pin && !/^[0-9]{6}$/.test(pin)) {
      setFieldError("pincode", "PIN code must contain 6 digits.");
      valid = false;
    }
  }
  return valid;
}

function buildRegistrationPayload() {
  const common = {
    fullName: value("fullName"),
    email: value("email").toLowerCase(),
    phone: value("phone") || null,
    password: password ? password.value : ""
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
    householdAction: householdActionSelect ? householdActionSelect.value : "CREATE",
    householdName: (householdActionSelect && householdActionSelect.value === "CREATE")
      ? value("householdName")
      : null,
    address: value("address") || null,
    city: value("city") || null,
    state: value("state") || null,
    pincode: value("pincode") || null
  };
}

async function readResponse(response) {
  const text = await response.text();
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

if (form) {
  form.addEventListener("submit", async event => {
    event.preventDefault();
    clearStatus();
    if (!validate()) return;

    if (submitButton) submitButton.disabled = true;
    if (submitLabel) {
      submitLabel.textContent = currentMode === "login" ? "Logging in…" : "Creating account…";
    }

    try {
      let url;
      let payload;
      if (currentMode === "signup") {
        url = currentRole === "vendor" ? ENDPOINTS.vendorRegister : ENDPOINTS.userRegister;
        payload = buildRegistrationPayload();
      } else {
        url = ENDPOINTS.login;
        payload = {
          email: value("email").toLowerCase(),
          password: password ? password.value : "",
          role: currentRole
        };
      }

      const csrfToken = await getAuthCsrfToken();
      // const requestHeaders = {
      //   "Content-Type": "application/json",
      //   "Accept": "application/json"
      // };
      // if (csrfToken) {
      //   requestHeaders["X-XSRF-TOKEN"] = csrfToken;
      // }

      // const response = await fetch(url, {
      //   method: "POST",
      //   credentials: "include",
      //   headers: requestHeaders,
      //   body: JSON.stringify(payload)
      // });
      const requestHeaders = {
        "Content-Type": "application/json",
        "Accept": "application/json"
      };

      const response = await fetch(url, {
        method: "POST",
        credentials: "include",
        headers: requestHeaders,
        body: JSON.stringify(payload)
      });
      const data = await readResponse(response);

      if (!response.ok) {
        const message =
          data.message ||
          data.error ||
          (response.status === 409
            ? "An account with these details already exists."
            : `Request failed (${response.status}). Check that the backend is live.`);
        showStatus(message, "error");
        return;
      }

      if (currentMode === "signup") {
        if (
          currentRole === "user" &&
          householdActionSelect &&
          householdActionSelect.value === "JOIN"
        ) {
          sessionStorage.setItem(
            "grocerymate_pending_invitation_code",
            value("invitationCode").toUpperCase()
          );
        } else {
          sessionStorage.removeItem("grocerymate_pending_invitation_code");
        }

        showStatus(data.message || "Account created successfully. You can now log in.", "success");
        setMode("login");
        if (password) password.value = "";
      } else {
        // Successful Login
        if (data.householdId != null && Number(data.householdId) > 0) {
          localStorage.setItem("grocerymate_household_id", String(data.householdId));
        } else {
          localStorage.removeItem("grocerymate_household_id");
        }

        if (data.userId != null) {
          localStorage.setItem("grocerymate_user_id", String(data.userId));
        }

        if (data.vendorId != null) {
          localStorage.setItem("grocerymate_vendor_id", String(data.vendorId));
        }

        const role = String(data.role || currentRole).toUpperCase();
        localStorage.setItem("grocerymate_role", role);

        if (data.token) {
          localStorage.setItem("grocerymate_token", data.token);
        } else {
          localStorage.setItem("grocerymate_token", "dummy_session_token");
        }

        showStatus(data.message || "Login successful.", "success");

        if (role === "USER") {
          const pendingCode = sessionStorage.getItem("grocerymate_pending_invitation_code");
          if (pendingCode) {
            const token = await getAuthCsrfToken();
            const acceptHeaders = {
              "Content-Type": "application/json",
              "Accept": "application/json"
            };
            if (token) acceptHeaders["X-XSRF-TOKEN"] = token;

            const acceptResponse = await fetch(`${AUTH_BASE}/api/household/invitations/accept`, {
              method: "POST",
              credentials: "include",
              headers: acceptHeaders,
              body: JSON.stringify({ invitationCode: pendingCode })
            });

            const acceptData = await readResponse(acceptResponse);
            if (acceptResponse.ok && acceptData.householdId) {
              localStorage.setItem("grocerymate_household_id", String(acceptData.householdId));
              sessionStorage.removeItem("grocerymate_pending_invitation_code");
            }
          }
        }

        if (role === "VENDOR") {
          window.location.assign("vendor-dashboard.html");
        } else if (role === "USER") {
          window.location.assign("user-dashboard.html");
        } else {
          showStatus("Login succeeded, but account role is unrecognized.", "error");
        }
      }
    } catch (error) {
      showStatus(`Could not connect to backend at ${AUTH_BASE}. Please try again shortly.`, "error");
      console.error("GroceryMate auth request failed:", error);
    } finally {
      if (submitButton) submitButton.disabled = false;
      updateLabels();
    }
  });
}

document.querySelectorAll("[data-role]").forEach(button => {
  button.addEventListener("click", () => setRole(button.dataset.role));
});
document.querySelectorAll("[data-mode]").forEach(button => {
  button.addEventListener("click", () => setMode(button.dataset.mode));
});
if (switchModeButton) {
  switchModeButton.addEventListener("click", () =>
    setMode(currentMode === "login" ? "signup" : "login")
  );
}
if (togglePassword && password) {
  togglePassword.addEventListener("click", () => {
    const show = password.type === "password";
    password.type = show ? "text" : "password";
    togglePassword.textContent = show ? "Hide" : "Show";
  });
}
if (householdActionSelect) {
  householdActionSelect.addEventListener("change", updateHouseholdActionFields);
}

setRole("user");
setMode("signup");