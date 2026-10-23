
package com.grocerymate.controller;

import com.grocerymate.dto.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

import com.grocerymate.security.AuthenticatedAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;

@RestController
//@CrossOrigin(origins = "*")
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }


    @PostMapping("/signup/user")
    @Transactional
    public ResponseEntity<?> registerUser(
            @RequestBody RegisterRequest request) {

        if (request.getFullName() == null
                || request.getFullName().isBlank()
                || request.getEmail() == null
                || request.getEmail().isBlank()
                || request.getPassword() == null
                || request.getPassword().length() < 8) {

            return error(
                    HttpStatus.BAD_REQUEST,
                    "Name, email and a password of at least 8 characters are required."
            );
        }

        String email = request.getEmail().trim().toLowerCase();

        if (emailExists(email)) {
            return error(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists."
            );
        }

        // Backward compatibility: older frontend requests default to CREATE.
        String householdAction = request.getHouseholdAction();

        if (householdAction == null || householdAction.isBlank()) {
            householdAction = "CREATE";
        } else {
            householdAction = householdAction.trim().toUpperCase();
        }

        if (!"CREATE".equals(householdAction)
                && !"JOIN".equals(householdAction)) {

            return error(
                    HttpStatus.BAD_REQUEST,
                    "householdAction must be CREATE or JOIN."
            );
        }

        // Create the user account.
        String userSql = """
        INSERT INTO users
            (full_name, email, password_hash, phone,
             account_status, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
        """;

        KeyHolder userKey = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    userSql, Statement.RETURN_GENERATED_KEYS
            );

            ps.setString(1, request.getFullName().trim());
            ps.setString(2, email);
            ps.setString(3,
                    passwordEncoder.encode(request.getPassword()));
            ps.setString(4, request.getPhone());

            return ps;
        }, userKey);

        Number generatedUserId = userKey.getKey();

        if (generatedUserId == null) {
            throw new IllegalStateException("Could not create user.");
        }

        long userId = generatedUserId.longValue();

        // JOIN: leave the user without household membership for now.
        if ("JOIN".equals(householdAction)) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message",
                    "Account registered successfully. Log in and accept a household invitation to join.",
                    "userId", userId,
                    "householdAction", "JOIN"
            ));
        }

        // CREATE: create a new household and make the user its owner.
        String householdName =
                request.getHouseholdName() != null
                        && !request.getHouseholdName().isBlank()
                        ? request.getHouseholdName().trim()
                        : request.getFullName().trim() + "'s Household";

        jdbc.update("""
        INSERT INTO households
            (household_name, head_user_id, address,
             city, state, pincode, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
        """,
                householdName,
                userId,
                request.getAddress(),
                request.getCity(),
                request.getState(),
                request.getPincode());

        Long householdId = jdbc.queryForObject("""
        SELECT household_id
        FROM households
        WHERE head_user_id = ?
        ORDER BY household_id DESC
        LIMIT 1
        """, Long.class, userId);

        if (householdId == null) {
            throw new IllegalStateException("Could not create household.");
        }

        jdbc.update("""
        INSERT INTO household_members
            (household_id, user_id, member_role)
        VALUES (?, ?, 'OWNER')
        """, householdId, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Household account registered successfully.",
                "userId", userId,
                "householdId", householdId,
                "householdAction", "CREATE"
        ));
    }

    @PostMapping("/signup/vendor")
    public ResponseEntity<?> registerVendor(
            @RequestBody RegisterRequest request) {

        if (request.getFullName() == null
                || request.getFullName().isBlank()
                || request.getEmail() == null
                || request.getEmail().isBlank()
                || request.getPassword() == null
                || request.getPassword().length() < 8) {
            return error(HttpStatus.BAD_REQUEST,
                    "Name, email and a password of at least 8 characters are required.");
        }

        String email = request.getEmail().trim().toLowerCase();

        if (emailExists(email)) {
            return error(HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        }

        String shopName =
                request.getShopName() != null
                        && !request.getShopName().isBlank()
                        ? request.getShopName().trim()
                        : request.getFullName().trim();

        jdbc.update("""
            INSERT INTO vendors
                (vendor_name, shop_name, email, password_hash,
                 phone, address, city, state, pincode,
                 is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, NOW(), NOW())
            """,
                request.getFullName().trim(),
                shopName,
                email,
                passwordEncoder.encode(request.getPassword()),
                request.getPhone(),
                request.getAddress(),
                request.getCity(),
                request.getState(),
                request.getPincode());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Vendor account registered successfully."
        ));
    }


    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> credentials,
            HttpServletRequest request,
            HttpServletResponse response) {


        String email = credentials.get("email");
        String password = credentials.get("password");
        String requestedRole = credentials.get("role");

        if (email == null || password == null
                || email.isBlank() || password.isBlank()) {
            return error(HttpStatus.BAD_REQUEST,
                    "Email and password are required.");
        }

        email = email.trim().toLowerCase();

        boolean userExists = count("""
            SELECT COUNT(*) FROM users WHERE email = ?
            """, email) > 0;

        boolean vendorExists = count("""
            SELECT COUNT(*) FROM vendors WHERE email = ?
            """, email) > 0;

        boolean wantsVendor =
                "vendor".equalsIgnoreCase(requestedRole);

        boolean wantsUser =
                "user".equalsIgnoreCase(requestedRole)
                        || "household".equalsIgnoreCase(requestedRole);

        // If the same email exists in both tables, require role selection.
        if (userExists && vendorExists
                && requestedRole == null) {
            return error(HttpStatus.BAD_REQUEST,
                    "This email has multiple account types. Send role as user or vendor.");
        }

        if (wantsVendor || (!wantsUser && vendorExists && !userExists)) {
            return loginVendor(email, password, request, response);
        }

        if (wantsUser || userExists) {
            return loginUser(email, password, request, response);
        }

        return error(HttpStatus.UNAUTHORIZED,
                "Invalid email or password.");
    }

    private ResponseEntity<?> loginUser(
            String email,
            String password,
            HttpServletRequest request,
            HttpServletResponse response) {

        var rows = jdbc.queryForList("""
            SELECT user_id, full_name, password_hash
            FROM users
            WHERE email = ? AND account_status = 'ACTIVE'
            """, email);

        if (rows.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Invalid email or password.");
        }

        Map<String, Object> user = rows.get(0);
        long userId = ((Number) user.get("user_id")).longValue();

        if (!verifyAndUpgradePassword(
                "users", "user_id", userId,
                (String) user.get("password_hash"), password)) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Invalid email or password.");
        }


        var households = jdbc.queryForList("""
            SELECT household_id
            FROM household_members
            WHERE user_id = ?
            ORDER BY household_member_id
            LIMIT 1
            """, userId);

            Object householdId = households.isEmpty()
                    ? null
                    : households.get(0).get("household_id");

            Long sessionHouseholdId = householdId == null
                    ? null
                    : ((Number) householdId).longValue();

        establishSession(
                request,
                response,
                new AuthenticatedAccount(userId, "USER", sessionHouseholdId)
        );
        return ResponseEntity.ok(Map.of(
                "message", "Household login successful!",
                "role", "USER",
                "userId", userId,
                "householdId", householdId == null ? 0 : householdId
        ));
    }

    private ResponseEntity<?> loginVendor(
            String email,
            String password,
            HttpServletRequest request,
            HttpServletResponse response) {

        var rows = jdbc.queryForList("""
            SELECT vendor_id, vendor_name, password_hash
            FROM vendors
            WHERE email = ? AND is_active = 1
            """, email);

        if (rows.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Invalid email or password.");
        }

        Map<String, Object> vendor = rows.get(0);
        long vendorId =
                ((Number) vendor.get("vendor_id")).longValue();

        if (!verifyAndUpgradePassword(
                "vendors", "vendor_id", vendorId,
                (String) vendor.get("password_hash"), password)) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Invalid email or password.");
        }
        establishSession(
                request,
                response,
                new AuthenticatedAccount(vendorId, "VENDOR", null)
        );
        return ResponseEntity.ok(Map.of(
                "message", "Vendor login successful!",
                "role", "VENDOR",
                "vendorId", vendorId
        ));
    }

    private boolean verifyAndUpgradePassword(
            String table, String idColumn, long id,
            String storedPassword, String submittedPassword) {

        if (storedPassword == null) {
            return false;
        }

        if (passwordEncoder.matches(submittedPassword, storedPassword)) {
            return true;
        }

        // Compatibility for accounts created before BCrypt was added.
        // On a successful legacy login, replace the plain-text value.
        if (!storedPassword.startsWith("$2a$")
                && !storedPassword.startsWith("$2b$")
                && !storedPassword.startsWith("$2y$")
                && storedPassword.equals(submittedPassword)) {

            String encoded = passwordEncoder.encode(submittedPassword);

            // Table/column names here are fixed constants, not user input.
            jdbc.update(
                    "UPDATE " + table
                            + " SET password_hash = ? WHERE "
                            + idColumn + " = ?",
                    encoded, id);
            return true;
        }

        return false;
    }

    private boolean emailExists(String email) {
        return count("SELECT COUNT(*) FROM users WHERE email = ?", email) > 0
                || count("SELECT COUNT(*) FROM vendors WHERE email = ?", email) > 0;
    }

    private int count(String sql, Object... args) {
        Integer result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private ResponseEntity<Map<String, String>> error(
            HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("message", message));
    }

    private void establishSession(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticatedAccount account) {

        // Prevent session fixation if a session already exists.
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            request.changeSessionId();
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                account,
                null,
                List.of(new SimpleGrantedAuthority(
                        "ROLE_" + account.role()
                ))
        );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Explicitly persist the authentication in the HTTP session.
        HttpSessionSecurityContextRepository repository =
                new HttpSessionSecurityContextRepository();

        repository.saveContext(context, request, response);
    }

    @GetMapping("/session")
    public ResponseEntity<?> getSession(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "Not logged in."
                    ));
        }

        Map<String, Object> sessionData = new java.util.HashMap<>();

        sessionData.put("id", account.id());
        sessionData.put("role", account.role());

        if ("VENDOR".equalsIgnoreCase(account.role())) {
            sessionData.put("vendorId", account.id());
        } else {
            sessionData.put("userId", account.id());

            if (account.householdId() != null) {
                sessionData.put("householdId", account.householdId());
            }
        }

        return ResponseEntity.ok(sessionData);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(
                Map.of("message", "Logged out successfully.")
        );
    }

}
