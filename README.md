# Easy Bike Rentals

A full-stack academic bike rental demonstration built with React, Vite, Spring Boot, Spring Security, JWT, Spring Data JPA and MySQL. The app includes a responsive marketing site, bike fleet and detail pages, customer registration/login, booking requests with overlap protection, a customer booking dashboard, an admin booking/fleet view, Razorpay Standard Checkout, and persisted contact messages.

> **Payments use Razorpay Standard Checkout.** Use test-mode keys while integrating. Switch to live-mode keys only after your Razorpay account is activated and the site is deployed over HTTPS.

## Features

- Browse bikes from the REST API, search by name/brand, and filter by category.
- Register customers with BCrypt password hashes; log in for a signed JWT.
- Request future bookings; the API checks time overlap and serializes booking attempts per bike. A signature-verified, captured Razorpay payment confirms the booking.
- View customer bookings, cancel eligible bookings, and update booking status as an administrator.
- Pay bookings through Razorpay Standard Checkout; the backend verifies the checkout signature and captured payment before confirming a booking.
- Store contact form submissions in MySQL.

## Technology and architecture

`React + Vite + Axios → Spring Boot REST API → Spring Security/JWT + JPA → MySQL`

- Frontend: HTML, CSS, JavaScript, React, React Router DOM, Axios, Lucide icons, Vite.
- Backend: Java 17+, Spring Boot, Spring Web, Spring Data JPA, Spring Security, Hibernate, Maven, JWT.
- Database: MySQL 8+ and MySQL Workbench.

## Project structure

```text
Easy-Bike-Rentals/
├── frontend/        # React/Vite application
├── backend/         # Maven/Spring Boot REST API
├── database/        # MySQL schema and seed data
├── screenshots/     # Add project screenshots here
├── .env.example
├── README.md
└── CONTRIBUTING.md
```

## Requirements

- JDK 17 or newer.
- Maven 3.8+ (or IntelliJ IDEA's bundled Maven integration).
- Node.js 20+ and npm.
- MySQL 8+.

## Database setup

In MySQL Workbench, execute `database/schema.sql`, then `database/sample-data.sql`. Create a database user with access to `easy_bike_rentals_db`; do not commit its password. The backend defaults to the local MySQL URL and `root`/`change-me`. Set real local credentials in environment variables instead:

```powershell
$env:DB_USERNAME='your_mysql_user'
$env:DB_PASSWORD='your_mysql_password'
$env:JWT_SECRET='use-a-long-random-secret-of-at-least-32-characters'
```

Hibernate's `ddl-auto=update` creates the core JPA entity tables. The SQL scripts also include the wider academic tables for roles, customer profile mapping, categories, payments, reviews and locations.

## Open and run in IntelliJ IDEA

1. In IntelliJ IDEA, choose **File → Open** and select the `Easy-Bike-Rentals` project folder. The root `pom.xml` imports the backend Maven module. Set Project SDK to JDK 17+ and allow Maven to load dependencies. IntelliJ IDEA Ultimate also provides the JavaScript and npm integrations; Community Edition can run the same commands in its built-in terminal.
2. Start MySQL and run the schema and seed scripts above.
3. In IntelliJ's terminal, run the backend from the project root:

```powershell
mvn -pl backend spring-boot:run
```

The API listens on `http://localhost:8080`. Environment variables can instead be configured in the IntelliJ Run Configuration.

4. In a second IntelliJ terminal, install and start the frontend:

```powershell
npm --prefix frontend install
npm --prefix frontend run dev
```

Open `http://127.0.0.1:5174`. The Vite server proxies `/api` to the backend, so frontend requests stay same-origin and do not depend on browser CORS rules. The default proxy target is `http://127.0.0.1:8080`; if the backend uses another port, set `VITE_API_TARGET` in `frontend/.env.local` (for example, `http://127.0.0.1:8081`). Keep `VITE_API_URL=/api`.

## Accounts

Register a customer at `/register`. To promote a local account to administrator, use a trusted MySQL connection:

```sql
UPDATE users SET role='ADMIN' WHERE email='your@email';
```

Log in again to get a JWT carrying the new role. The project intentionally seeds no weak demo password.

## REST API

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register customer |
| POST | `/api/auth/login` | Public | Authenticate and receive JWT |
| GET | `/api/bikes` | Public | List active bikes |
| GET | `/api/bikes/{id}` | Public | Read bike details |
| POST | `/api/bikes` | Admin | Add bike |
| PUT | `/api/bikes/{id}` | Admin | Update bike |
| DELETE | `/api/bikes/{id}` | Admin | Deactivate bike |
| GET | `/api/bookings` | Authenticated | List own bookings; admin sees all |
| POST | `/api/bookings` | Customer | Request booking; validates dates and conflicts |
| DELETE | `/api/bookings/{id}` | Booking owner | Cancel pending/confirmed booking |
| PATCH | `/api/bookings/{id}/status?value=CONFIRMED` | Admin | Change booking lifecycle status |
| POST | `/api/payments/orders` | Booking owner | Create or resume a Razorpay order |
| POST | `/api/payments/verify` | Booking owner | Verify the checkout signature and captured payment |
| POST | `/api/payments/webhook` | Razorpay | Verify and process `payment.captured` events |
| GET | `/api/payments` | Admin | List payments |
| POST | `/api/contact` | Public | Save contact message |

Send authenticated requests with `Authorization: Bearer <token>`.

## Booking and pricing behavior

The API requires a future pickup time and a return time after pickup. It takes a row lock on the selected bike inside a transaction, checks pending/confirmed/picked-up booking windows for overlap, and rejects a conflict with HTTP 409. Price is rounded up to whole rental days using the daily rate; the security deposit is added to the estimate. New bookings start as `PENDING`; only a signature-verified, captured Razorpay payment moves an eligible booking to `CONFIRMED`. Booking owners cannot cancel captured payments in the app; refunds must first be processed in Razorpay. Availability is period-based and bikes are not globally disabled by a single booking.

## Razorpay setup

1. Activate your Razorpay account and create API keys in the Dashboard. Start with **Test Mode** keys.
2. Set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET` in the IntelliJ backend Run Configuration or backend server environment. Keep both secret values on the backend only; do not add them to frontend variables or Git.
3. Enable automatic payment capture in Razorpay. The backend confirms bookings only after it verifies a captured payment with Razorpay.
4. For webhook recovery, configure the public HTTPS endpoint `https://<your-domain>/api/payments/webhook` in Razorpay and subscribe to `payment.captured`. Use the separate webhook secret generated for that webhook. Razorpay cannot deliver webhooks to `localhost`.
5. Test with Test Mode keys first. For live payments, deploy the frontend and backend to a public HTTPS domain and use Live Mode keys only after Razorpay activates your account and site. Restart the backend whenever credentials change.

Without API keys, the rest of the site remains available and payment attempts return a clear configuration error.

For a public Railway deployment, see [DEPLOYMENT.md](DEPLOYMENT.md). It covers the frontend, Spring Boot API, MySQL variables, HTTPS domains, and Razorpay Live Mode setup.

## Local verification and known limits

The frontend production build completes successfully. The local frontend and backend have also been verified together: bike listings and bike detail records load from the seeded MySQL data. Automated tests have not been added or run.

The included implementation is an academic starter with functional core flows, not every requested enterprise module. The frontend does not yet include a complete bike CRUD editor, customer-management screens, reports/analytics, password reset/profile editor, booking receipt printing, reviews UI, or full booking history filters. Those remain enhancements. Bike image URLs are remote Unsplash sample imagery.

## Screenshots

Add screenshots to `screenshots/` and link them here.

## Future work

Add automated unit/integration tests, admin fleet/customer CRUD, revenue reporting, receipt generation, more robust validation and authorization, database migrations, and a real payment gateway only if the project later needs real transactions.

## Contributors and license

See [CONTRIBUTING.md](CONTRIBUTING.md). Released under the MIT License; see [LICENSE](LICENSE).
