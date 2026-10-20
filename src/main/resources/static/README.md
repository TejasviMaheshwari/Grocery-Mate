# GroceryMate Login & Signup UI

Plain HTML/CSS/JavaScript frontend for the GroceryMate MySQL schema.

## Files
- `index.html` — form markup
- `style.css` — responsive design
- `app.js` — validation and API calls

## Run the frontend
Open `index.html` for a quick UI preview. For development, it is better to serve this folder with a local static server, such as VS Code Live Server.

## Expected Spring Boot API contract
The Java backend endpoints below are the defaults in `app.js`. If your controller uses different URLs or JSON field names, change `API_BASE` and `ENDPOINTS` in `app.js`, or adapt the Java DTOs to match.

### `POST /api/auth/register/user`
Request:
```json
{
  "fullName": "Aditi Sharma",
  "email": "aditi@example.com",
  "phone": "9876543210",
  "password": "a-strong-password",
  "householdName": "Sharma Family",
  "address": "12 Example Road",
  "city": "Ajmer",
  "state": "Rajasthan",
  "pincode": "305001"
}
```
Backend should:
1. Validate the request.
2. Hash the password using BCrypt (never save the raw password).
3. Insert the identity into `Users` (`full_name`, `email`, `phone`, `password_hash`).
4. Insert a row into `Households` (`household_name`, `head_user_id`, address/city/state/pincode).
5. Insert the supervising user into `Household_Members` with `member_role='HEAD'`.
6. Use a transaction so all inserts succeed or roll back together.

### `POST /api/auth/register/vendor`
Request:
```json
{
  "fullName": "Ravi Sharma",
  "email": "ravi@example.com",
  "phone": "9876543210",
  "password": "a-strong-password",
  "shopName": "Sharma General Store",
  "address": "14 Market Road",
  "city": "Ajmer",
  "state": "Rajasthan",
  "pincode": "305001"
}
```
Backend should insert the `Users` identity first, then `Vendors` using that generated `user_id`. Hash password with BCrypt and use a transaction.

### `POST /api/auth/login`
Request:
```json
{ "email": "aditi@example.com", "password": "a-strong-password" }
```
Recommended response:
```json
{ "message": "Login successful", "role": "USER", "redirectUrl": "/user-dashboard.html" }
```
For vendor login, return `role: "VENDOR"` and the vendor dashboard path. Authenticate against `Users.password_hash` with BCrypt. Determine the role by checking the appropriate profile table(s), not by trusting a role sent by the browser. If an email has both profiles, define and enforce a clear policy.

## CORS
If frontend is served from a different origin/port than Spring Boot, configure CORS in Spring Boot for your local frontend origin (for example `http://127.0.0.1:5500`). Do not use wildcard origins with credentials in production.

## Important
- This UI does not connect directly to MySQL; the browser calls Java HTTP endpoints.
- The API paths above are a proposed contract because existing controller routes were not provided.
- Never return or expose `password_hash`.
- Enforce validation and authorization on the backend, even if the frontend validates fields.
