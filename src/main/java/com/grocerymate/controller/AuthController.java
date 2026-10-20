package com.grocerymate.controller;

import com.grocerymate.dto.RegisterRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;

    public AuthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 1. HOUSEHOLD / USER SIGNUP (Saves strictly to `users`)
    // 1. HOUSEHOLD / USER SIGNUP -> Writes to users AND households
    @PostMapping("/signup/user")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {
        // Step A: Insert user credentials into users table
        String userSql = """
            INSERT INTO users (full_name, email, password_hash, phone, account_status, created_at, updated_at) 
            VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
        """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.getFullName());
            ps.setString(2, request.getEmail());
            ps.setString(3, request.getPassword());
            ps.setString(4, request.getPhone());
            return ps;
        }, keyHolder);

        long newUserId = keyHolder.getKey().longValue();

        // Step B: Insert household details into households table
        String householdSql = """
            INSERT INTO households (
                household_name, head_user_id, address, city, state, pincode, created_at, updated_at
            ) 
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
        """;

        String householdName = (request.getHouseholdName() != null && !request.getHouseholdName().isBlank())
                ? request.getHouseholdName()
                : (request.getFullName() + "'s Household");

        jdbcTemplate.update(householdSql,
                householdName,
                newUserId,
                request.getAddress(),
                request.getCity(),
                request.getState(),
                request.getPincode()
        );

        return ResponseEntity.ok(Map.of("message", "Household account registered successfully!"));
    }

    // 2. VENDOR SIGNUP (Saves strictly to `vendors`)
    @PostMapping("/signup/vendor")
    public ResponseEntity<?> registerVendor(@RequestBody RegisterRequest request) {
        String sql = """
            INSERT INTO vendors (
                vendor_name, shop_name, email, password_hash, 
                phone, address, city, state, pincode, is_active, 
                created_at, updated_at
            ) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, NOW(), NOW())
        """;

        String shopName = (request.getShopName() != null && !request.getShopName().isBlank())
                ? request.getShopName()
                : request.getFullName();

        jdbcTemplate.update(sql,
                request.getFullName(),
                shopName,
                request.getEmail(),
                request.getPassword(),
                request.getPhone(),
                request.getAddress(),
                request.getCity(),
                request.getState(),
                request.getPincode()
        );

        return ResponseEntity.ok(Map.of("message", "Vendor account registered successfully!"));
    }

    // 3. SEPARATED LOGIN ENDPOINT
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.get("email");
        String password = credentials.get("password");
        String role = credentials.get("role"); // "user" or "vendor"

        if ("vendor".equalsIgnoreCase(role)) {
            // Check only the vendors table
            String sql = "SELECT vendor_id, vendor_name, password_hash FROM vendors WHERE email = ?";
            var rows = jdbcTemplate.queryForList(sql, email);

            if (rows.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "No vendor account found with this email."));
            }

            String dbPassword = (String) rows.get(0).get("password_hash");
            if (!password.equals(dbPassword)) {
                return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password."));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Vendor login successful!",
                    "role", "VENDOR",
                    "vendorId", rows.get(0).get("vendor_id")
            ));
        } else {
            // Check only the users table (Households)
            String sql = "SELECT user_id, full_name, password_hash FROM users WHERE email = ?";
            var rows = jdbcTemplate.queryForList(sql, email);

            if (rows.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "No household account found with this email."));
            }

            String dbPassword = (String) rows.get(0).get("password_hash");
            if (!password.equals(dbPassword)) {
                return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password."));
            }

            // Query household_id linked to this user
            String householdSql = "SELECT household_id FROM households WHERE head_user_id = ?";
            var householdRows = jdbcTemplate.queryForList(householdSql, rows.get(0).get("user_id"));
            Object householdId = householdRows.isEmpty() ? null : householdRows.get(0).get("household_id");

            return ResponseEntity.ok(Map.of(
                    "message", "Household login successful!",
                    "role", "USER",
                    "userId", rows.get(0).get("user_id"),
                    "householdId", householdId != null ? householdId : 0
            ));
        }
    }
}



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


//package com.grocerymate.controller;
//
//import com.grocerymate.entity.Users;
//import com.grocerymate.entity.Vendors;
//import com.grocerymate.repository.UsersRepository;
//import com.grocerymate.repository.VendorsRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.Map;
//import java.util.Optional;
//
//@RestController
//@RequestMapping("/api/auth")
//@CrossOrigin(origins = "*")
//public class AuthController {
//
//    @Autowired
//    private UsersRepository usersRepository;
//
//    @Autowired
//    private VendorsRepository vendorsRepository;
//
//    @PostMapping("/signup/user")
//    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> payload) {
//        try {
//            Users user = new Users();
//            user.setFullName(payload.get("fullName"));
//            user.setEmail(payload.get("email"));
//            user.setPhone(payload.get("phone"));
//            user.setPasswordHash(payload.get("password"));
//            user.setAccountStatus("ACTIVE");
//            user.setCreatedAt(LocalDateTime.now());
//            user.setUpdatedAt(LocalDateTime.now());
//
//            usersRepository.save(user);
//            return ResponseEntity.ok("User registered successfully!");
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body("Error creating user: " + e.getMessage());
//        }
//    }
//
//    @PostMapping("/signup/vendor")
//    public ResponseEntity<?> registerVendor(@RequestBody Map<String, String> payload) {
//        try {
//            // 1. Create the base credentials record in the users table
//
//            Users user = new Users();
//            user.setFullName(payload.get("fullName"));
//            user.setEmail(payload.get("email"));
//            user.setPhone(payload.get("phone"));
//
//// Replace with BCrypt hashing before using real accounts.
//            user.setPasswordHash(payload.get("password"));
//
//            user.setAccountStatus("ACTIVE");
//            user.setCreatedAt(LocalDateTime.now());
//            user.setUpdatedAt(LocalDateTime.now());
//
//            Users savedUser = usersRepository.save(user);
//
//            Vendors vendor = new Vendors();
//            vendor.setUserId(savedUser.getUserId());
//
//// Vendor's personal/business contact name
//            vendor.setVendorName(payload.get("fullName"));
//
//// Shop's displayed name
//            vendor.setShopName(payload.get("shopName"));
//
//            vendor.setPhone(payload.get("phone"));
//            vendor.setAddress(payload.get("address"));
//            vendor.setCity(payload.get("city"));
//            vendor.setState(payload.get("state"));
//            vendor.setPincode(payload.get("pincode"));
//
//            vendor.setIsActive(true);
//            vendor.setCreatedAt(LocalDateTime.now());
//            vendor.setUpdatedAt(LocalDateTime.now());
//
//            vendorsRepository.save(vendor);
//
//
//            return ResponseEntity.ok("Vendor registered successfully!");
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(500).body("Error creating vendor: " + e.getMessage());
//        }
//    }
//
//    @PostMapping("/login")
//    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
//        try {
//            String email = payload.get("email");
//            String password = payload.get("password");
//
//            Optional<Users> userOpt = usersRepository.findAll().stream()
//                    .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(email))
//                    .findFirst();
//
//            if (userOpt.isPresent() && userOpt.get().getPasswordHash().equals(password)) {
//                return ResponseEntity.ok("Login successful!");
//            }
//            return ResponseEntity.status(401).body("Invalid email or password");
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body("Login error: " + e.getMessage());
//        }
//    }
//}



//package com.grocerymate.controller;
//
//import com.grocerymate.entity.Users;
//import com.grocerymate.entity.Vendors;
//import com.grocerymate.repository.UsersRepository;
//import com.grocerymate.repository.VendorsRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.Map;
//import java.util.Optional;
//
//@RestController
//@RequestMapping("/api/auth")
//@CrossOrigin(origins = "*")
//public class AuthController {
//
//    @Autowired
//    private UsersRepository usersRepository;
//
//    @Autowired
//    private VendorsRepository vendorsRepository;
//
//    @PostMapping("/signup/user")
//    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> payload) {
//        try {
//            Users user = new Users();
//            user.setFullName(payload.get("fullName"));
//            user.setEmail(payload.get("email"));
//            user.setPhone(payload.get("phone"));
//            user.setPasswordHash(payload.get("password")); // Maps to password_hash column
//            user.setAccountStatus("ACTIVE");
//            user.setCreatedAt(LocalDateTime.now());
//            user.setUpdatedAt(LocalDateTime.now());
//
//            usersRepository.save(user);
//            return ResponseEntity.ok("User registered successfully!");
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body("Error creating user: " + e.getMessage());
//        }
//    }
//
//    @PostMapping("/signup/vendor")
//    public ResponseEntity<?> registerVendor(@RequestBody Map<String, String> payload) {
//        try {
//            // 1. Create the user credentials record
//            Users user = new Users();
//            user.setFullName(payload.get("name"));
//            user.setEmail(payload.get("email"));
//            user.setPhone(payload.get("phone"));
//            user.setPasswordHash(payload.get("password"));
//            user.setAccountStatus("ACTIVE");
//            user.setCreatedAt(LocalDateTime.now());
//            user.setUpdatedAt(LocalDateTime.now());
//            Users savedUser = usersRepository.save(user);
//
//            // 2. Create the vendor profile entry (stores address)
//            Vendors vendor = new Vendors();
//            vendor.setUserId(savedUser.getUserId());
//            vendor.setShopName(payload.get("name"));
//            vendor.setPhone(payload.get("phone"));
//            vendor.setAddress(payload.get("address")); // Vendors table has the address field
//            vendor.setIsActive(true);
//            vendor.setCreatedAt(LocalDateTime.now());
//            vendor.setUpdatedAt(LocalDateTime.now());
//            vendorsRepository.save(vendor);
//
//            return ResponseEntity.ok("Vendor registered successfully!");
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body("Error creating vendor: " + e.getMessage());
//        }
//    }
//
//    @PostMapping("/login")
//    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
//        String email = payload.get("email");
//        String password = payload.get("password");
//
//        // Simple search by email
//        Optional<Users> userOpt = usersRepository.findAll().stream()
//                .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(email))
//                .findFirst();
//
//        if (userOpt.isPresent() && userOpt.get().getPasswordHash().equals(password)) {
//            return ResponseEntity.ok("Login successful!");
//        }
//        return ResponseEntity.status(401).body("Invalid email or password");
//    }
//}
//
//
//
