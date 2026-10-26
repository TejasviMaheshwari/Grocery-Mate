
const LIVE_BACKEND_URL = "https://grocerymate-backend.onrender.com";

const IS_LOCAL =
    window.location.hostname === "localhost" ||
    window.location.hostname === "127.0.0.1";

// API_BASE is the backend origin, without /api at the end.
const API_BASE = IS_LOCAL
    ? (
        window.location.port === "8080"
            ? ""
            : "http://localhost:8080"
    )
    : LIVE_BACKEND_URL;

const response = await fetch(`${API_BASE}/api/csrf`, {
  method: "GET",
  credentials: "include",
  headers: {
    Accept: "application/json"
  }
});



async function getCsrfToken() {
  const response = await fetch(`${API_BASE}/api/csrf`, {
    method: "GET",
    credentials: "include",
    headers: {
      "Accept": "application/json"
    }
  });

  if (!response.ok) {
    throw new Error(
        `Could not obtain CSRF token (${response.status}).`
    );
  }

  const data = await response.json();

  const cookieToken = document.cookie
      .split("; ")
      .find(cookie => cookie.startsWith("XSRF-TOKEN="))
      ?.split("=")
      .slice(1)
      .join("=");

  const token = cookieToken
      ? decodeURIComponent(cookieToken)
      : data.token;

  if (!token) {
    throw new Error("CSRF token is missing.");
  }

  return token;
}




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
  const joining = householdActionSelect.value === "JOIN";

  createHouseholdFields.hidden = joining;
  joinHouseholdFields.hidden = !joining;
}
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
  updateHouseholdActionFields();
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
  updateHouseholdActionFields();
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
    if (currentRole === "user") {
      if (householdActionSelect.value === "CREATE") {
        if (!value("householdName")) {
          setFieldError("householdName", "Enter your household name.");
          valid = false;
        }
      } else if (householdActionSelect.value === "JOIN") {
        const code = value("invitationCode");

        if (!code) {
          setFieldError("invitationCode", "Enter the invitation code.");
          valid = false;
        } else if (!/^[A-Za-z0-9]{12}$/.test(code)) {
          setFieldError(
            "invitationCode",
            "Enter the 12-character invitation code."
          );
          valid = false;
        }
      }
    }
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
    householdAction: householdActionSelect.value,
    householdName: householdActionSelect.value === "CREATE"
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
      payload = {
        email: value("email").toLowerCase(),
        password: password.value,
        role: currentRole
      };
    }

    
    const csrfToken = await getCsrfToken();

    const response = await fetch(url, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "X-XSRF-TOKEN": csrfToken
      },
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
    if (
      currentRole === "user" &&
      householdActionSelect.value === "JOIN"
    ) {
      sessionStorage.setItem(
        "grocerymate_pending_invitation_code",
        value("invitationCode").toUpperCase()
      );
    } else {
      sessionStorage.removeItem("grocerymate_pending_invitation_code");
    }

    showStatus(
      data.message || "Account created successfully. You can now log in.",
      "success"
    );

    setMode("login");
    password.value = "";
  }else {
      // Save the account details returned by the backend.
      if (
        data.householdId != null &&
        Number(data.householdId) > 0
      ) {
        localStorage.setItem(
          "grocerymate_household_id",
          String(data.householdId)
        );
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

      // Your current backend does not return a token.
      if (data.token) {
        localStorage.setItem("grocerymate_token", data.token);
      } else {
        localStorage.removeItem("grocerymate_token");
      }

      showStatus(data.message || "Login successful.", "success");
      
      if (role === "USER") {
        const pendingCode = sessionStorage.getItem(
          "grocerymate_pending_invitation_code"
        );

        if (pendingCode) {
          const csrfToken = await getCsrfToken();

          const acceptResponse = await fetch(
            `${API_BASE}/api/household/invitations/accept`,
            {
              method: "POST",
              credentials: "include",
              headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                "X-XSRF-TOKEN": csrfToken
              },
              body: JSON.stringify({
                invitationCode: pendingCode
              })
            }
          );

          const acceptData = await readResponse(acceptResponse);

          if (!acceptResponse.ok) {
            showStatus(
              acceptData.message || "Could not accept the household invitation.",
              "error"
            );
            return;
          }

          // Save the household returned by the backend.
          localStorage.setItem(
            "grocerymate_household_id",
            String(acceptData.householdId)
          );

          sessionStorage.removeItem("grocerymate_pending_invitation_code");

          showStatus(
            "Invitation accepted. You have joined the household!",
            "success"
          );
        }
      }

      if (role === "VENDOR") {
        window.location.assign("vendor-dashboard.html");
      } else if (role === "USER") {
        window.location.assign("user-dashboard.html");
      } else {
        showStatus("Login succeeded, but the account role is unrecognized.", "error");
      }
    
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
householdActionSelect.addEventListener(
  "change",
  updateHouseholdActionFields
);

setRole("user");
setMode("signup");
