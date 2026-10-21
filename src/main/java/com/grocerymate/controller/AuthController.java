
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
            return error(HttpStatus.BAD_REQUEST,
                    "Name, email and a password of at least 8 characters are required.");
        }

        String email = request.getEmail().trim().toLowerCase();

        if (emailExists(email)) {
            return error(HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        }

        String userSql = """
            INSERT INTO users
                (full_name, email, password_hash, phone,
                 account_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
            """;

        KeyHolder userKey = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    userSql, Statement.RETURN_GENERATED_KEYS);
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
                householdName, userId, request.getAddress(),
                request.getCity(), request.getState(),
                request.getPincode());

        Long householdId = jdbc.queryForObject("""
            SELECT household_id FROM households
            WHERE head_user_id = ?
            ORDER BY household_id DESC LIMIT 1
            """, Long.class, userId);

        // Also register the owner as a household member.
        jdbc.update("""
            INSERT INTO household_members
                (household_id, user_id, member_role)
            VALUES (?, ?, 'OWNER')
            """, householdId, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Household account registered successfully.",
                "userId", userId,
                "householdId", householdId
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
            SELECT household_id FROM households
            WHERE head_user_id = ?
            ORDER BY household_id
            LIMIT 1
            """, userId);

        Object householdId = households.isEmpty()
                ? null : households.get(0).get("household_id");
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

//package com.grocerymate.controller;
//
//import com.grocerymate.dto.RegisterRequest;
//import org.springframework.http.ResponseEntity;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.jdbc.support.GeneratedKeyHolder;
//import org.springframework.jdbc.support.KeyHolder;
//import java.sql.PreparedStatement;
//import java.sql.Statement;
//import java.util.Map;
//
//@RestController
//@CrossOrigin(origins = "*")
//@RequestMapping("/api/auth")
//public class AuthController {
//
//    private final JdbcTemplate jdbcTemplate;
//
//    public AuthController(JdbcTemplate jdbcTemplate) {
//        this.jdbcTemplate = jdbcTemplate;
//    }
//
//    // 1. HOUSEHOLD / USER SIGNUP (Saves strictly to `users`)
//    // 1. HOUSEHOLD / USER SIGNUP -> Writes to users AND households
//    @PostMapping("/signup/user")
//    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {
//        // Step A: Insert user credentials into users table
//        String userSql = """
//            INSERT INTO users (full_name, email, password_hash, phone, account_status, created_at, updated_at)
//            VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
//        """;
//
//        KeyHolder keyHolder = new GeneratedKeyHolder();
//
//        jdbcTemplate.update(connection -> {
//            PreparedStatement ps = connection.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS);
//            ps.setString(1, request.getFullName());
//            ps.setString(2, request.getEmail());
//            ps.setString(3, request.getPassword());
//            ps.setString(4, request.getPhone());
//            return ps;
//        }, keyHolder);
//
//        long newUserId = keyHolder.getKey().longValue();
//
//        // Step B: Insert household details into households table
//        String householdSql = """
//            INSERT INTO households (
//                household_name, head_user_id, address, city, state, pincode, created_at, updated_at
//            )
//            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
//        """;
//
//        String householdName = (request.getHouseholdName() != null && !request.getHouseholdName().isBlank())
//                ? request.getHouseholdName()
//                : (request.getFullName() + "'s Household");
//
//        jdbcTemplate.update(householdSql,
//                householdName,
//                newUserId,
//                request.getAddress(),
//                request.getCity(),
//                request.getState(),
//                request.getPincode()
//        );
//
//        return ResponseEntity.ok(Map.of("message", "Household account registered successfully!"));
//    }
//
//    // 2. VENDOR SIGNUP (Saves strictly to `vendors`)
//    @PostMapping("/signup/vendor")
//    public ResponseEntity<?> registerVendor(@RequestBody RegisterRequest request) {
//        String sql = """
//            INSERT INTO vendors (
//                vendor_name, shop_name, email, password_hash,
//                phone, address, city, state, pincode, is_active,
//                created_at, updated_at
//            )
//            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, NOW(), NOW())
//        """;
//
//        String shopName = (request.getShopName() != null && !request.getShopName().isBlank())
//                ? request.getShopName()
//                : request.getFullName();
//
//        jdbcTemplate.update(sql,
//                request.getFullName(),
//                shopName,
//                request.getEmail(),
//                request.getPassword(),
//                request.getPhone(),
//                request.getAddress(),
//                request.getCity(),
//                request.getState(),
//                request.getPincode()
//        );
//
//        return ResponseEntity.ok(Map.of("message", "Vendor account registered successfully!"));
//    }
//
//    // 3. SEPARATED LOGIN ENDPOINT
//    @PostMapping("/login")
//    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
//        String email = credentials.get("email");
//        String password = credentials.get("password");
//        String role = credentials.get("role"); // "user" or "vendor"
//
//        if ("vendor".equalsIgnoreCase(role)) {
//            // Check only the vendors table
//            String sql = "SELECT vendor_id, vendor_name, password_hash FROM vendors WHERE email = ?";
//            var rows = jdbcTemplate.queryForList(sql, email);
//
//            if (rows.isEmpty()) {
//                return ResponseEntity.status(401).body(Map.of("message", "No vendor account found with this email."));
//            }
//
//            String dbPassword = (String) rows.get(0).get("password_hash");
//            if (!password.equals(dbPassword)) {
//                return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password."));
//            }
//
//            return ResponseEntity.ok(Map.of(
//                    "message", "Vendor login successful!",
//                    "role", "VENDOR",
//                    "vendorId", rows.get(0).get("vendor_id")
//            ));
//        } else {
//            // Check only the users table (Households)
//            String sql = "SELECT user_id, full_name, password_hash FROM users WHERE email = ?";
//            var rows = jdbcTemplate.queryForList(sql, email);
//
//            if (rows.isEmpty()) {
//                return ResponseEntity.status(401).body(Map.of("message", "No household account found with this email."));
//            }
//
//            String dbPassword = (String) rows.get(0).get("password_hash");
//            if (!password.equals(dbPassword)) {
//                return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password."));
//            }
//
//            // Query household_id linked to this user
//            String householdSql = "SELECT household_id FROM households WHERE head_user_id = ?";
//            var householdRows = jdbcTemplate.queryForList(householdSql, rows.get(0).get("user_id"));
//            Object householdId = householdRows.isEmpty() ? null : householdRows.get(0).get("household_id");
//
//            return ResponseEntity.ok(Map.of(
//                    "message", "Household login successful!",
//                    "role", "USER",
//                    "userId", rows.get(0).get("user_id"),
//                    "householdId", householdId != null ? householdId : 0
//            ));
//        }
//    }
//}



//package com.grocerymate.controller;
//
//import com.grocerymate.dto.RegisterRequest;
//import org.springframework.http.ResponseEntity;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.jdbc.support.GeneratedKeyHolder;
//import org.springframework.jdbc.support.KeyHolder;
//import org.springframework.web.bind.annotation.*;
//
//import java.sql.PreparedStatement;
//import java.sql.Statement;
//import java.util.Map;
//
//@RestController
//@CrossOrigin(origins = "*")
//@RequestMapping("/api/auth")
//public class AuthController {
//
//    private final JdbcTemplate jdbcTemplate;
//
//    public AuthController(JdbcTemplate jdbcTemplate) {
//        this.jdbcTemplate = jdbcTemplate;
//    }
//
//    // 1. HOUSEHOLD / USER SIGNUP -> Writes to users table
//    @PostMapping("/signup/user")
//    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {
//        String sql = """
//            INSERT INTO users (full_name, email, password_hash, phone, account_status, created_at, updated_at)
//            VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
//        """;
//
//        jdbcTemplate.update(sql,
//                request.getFullName(),
//                request.getEmail(),
//                request.getPassword(),
//                request.getPhone()
//        );
//
//        return ResponseEntity.ok(Map.of("message", "User account registered successfully!"));
//    }
//
//    // 2. VENDOR SIGNUP -> Stores credentials in users, then links profile in vendors
//    @PostMapping("/signup/vendor")
//    public ResponseEntity<?> registerVendor(@RequestBody RegisterRequest request) {
//        // Step A: Insert into users table to generate a user_id
//        String userSql = """
//            INSERT INTO users (full_name, email, password_hash, phone, account_status, created_at, updated_at)
//            VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
//        """;
//
//        KeyHolder keyHolder = new GeneratedKeyHolder();
//
//        jdbcTemplate.update(connection -> {
//            PreparedStatement ps = connection.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS);
//            ps.setString(1, request.getFullName());
//            ps.setString(2, request.getEmail());
//            ps.setString(3, request.getPassword());
//            ps.setString(4, request.getPhone());
//            return ps;
//        }, keyHolder);
//
//        long newUserId = keyHolder.getKey().longValue();
//
//        // Step B: Insert into vendors table using the columns created by Hibernate
//        String vendorSql = """
//            INSERT INTO vendors (
//                user_id, vendor_name, shop_name, phone,
//                address, city, state, pincode, is_active,
//                created_at, updated_at
//            )
//            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, NOW(), NOW())
//        """;
//
//        String shopName = (request.getShopName() != null && !request.getShopName().isBlank())
//                ? request.getShopName()
//                : request.getFullName();
//
//        jdbcTemplate.update(vendorSql,
//                newUserId,
//                request.getFullName(),
//                shopName,
//                request.getPhone(),
//                request.getAddress(),
//                request.getCity(),
//                request.getState(),
//                request.getPincode()
//        );
//
//        return ResponseEntity.ok(Map.of("message", "Vendor account registered successfully!"));
//    }
//
//    // 3. LOGIN ENDPOINT
//    // 3. LOGIN ENDPOINT WITH ROLE VALIDATION
//    @PostMapping("/login")
//    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
//        String email = credentials.get("email");
//        String password = credentials.get("password");
//        String requestedRole = credentials.get("role"); // "user" or "vendor"
//
//        // 1. Verify email and password in users table
//        String userSql = "SELECT user_id, full_name, password_hash FROM users WHERE email = ?";
//        var userList = jdbcTemplate.queryForList(userSql, email);
//
//        if (userList.isEmpty()) {
//            return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password."));
//        }
//
//        Map<String, Object> user = userList.get(0);
//        String dbPassword = (String) user.get("password_hash");
//
//        if (!password.equals(dbPassword)) {
//            return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password."));
//        }
//
//        long userId = ((Number) user.get("user_id")).longValue();
//
//        // 2. Check if the user is a registered vendor
//        String vendorCheckSql = "SELECT COUNT(*) FROM vendors WHERE user_id = ?";
//        Integer vendorCount = jdbcTemplate.queryForObject(vendorCheckSql, Integer.class, userId);
//        boolean isVendor = (vendorCount != null && vendorCount > 0);
//
//        // 3. Enforce tab-specific access
//        if ("vendor".equalsIgnoreCase(requestedRole)) {
//            if (!isVendor) {
//                return ResponseEntity.status(403).body(Map.of(
//                        "message", "This account is registered as a Household, not a Vendor."
//                ));
//            }
//            return ResponseEntity.ok(Map.of(
//                    "message", "Vendor login successful!",
//                    "role", "VENDOR",
//                    "userId", userId
//            ));
//        } else {
//            // Household login
//            return ResponseEntity.ok(Map.of(
//                    "message", "Household login successful!",
//                    "role", isVendor ? "VENDOR" : "USER",
//                    "userId", userId
//            ));
//        }
//    }
//}

