# GroceryMate backend starter

This project is generated from the uploaded `grocerymate_mysql_schema.sql` schema and includes:
- Spring Boot 3 / Java 17 / Maven
- MariaDB JDBC connection
- JPA entity classes and Spring Data repositories for the 11 tables
- `/api/database/status` connection-check endpoint
- `ddl-auto=validate` to avoid changing the existing schema

## Before running
1. Ensure MariaDB is running and the `grocerymate` database already exists.
2. Use JDK 17+ and Maven (or open the project in IntelliJ).
3. Set the `DB_PASSWORD` environment variable to your MariaDB password.
   Optionally set `DB_USERNAME` and `DB_URL`.
4. Run `GroceryMateApplication`.
5. Open http://localhost:8080/api/database/status

Expected JSON: `{"connected":true,"database":"grocerymate","tableCount":11}`

## Important notes
- The schema file says MySQL 8.0+ and uses collation `utf8mb4_0900_ai_ci`. If MariaDB rejects this collation or schema, check your server version and existing database collation before changing anything.
- This starter maps enum columns to Java `String` fields for compatibility. For enum validation and business rules, use DTOs/services in the next implementation step.
- Repositories provide table-level persistence operations, but public CRUD APIs, authentication, authorization, household ownership checks, and frontend integration still need to be implemented before deployment.
- Do not expose password hashes through API responses. Never store plain-text passwords.
