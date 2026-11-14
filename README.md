# Easy Bike Rentals

A full-stack academic bike rental demonstration built with React, Vite, Spring Boot, Spring Security, JWT, Spring Data JPA and MySQL. The app includes a responsive marketing site, bike fleet and detail pages, customer registration/login, booking requests with overlap protection, a customer booking dashboard, an admin booking/fleet view, test-payment API, and persisted contact messages.

> **Payments are simulated.** The API marks a test transaction `TEST_PAID`; no real card or UPI transaction is processed.

## Features

- Browse bikes from the REST API, search by name/brand, and filter by category.
- Register customers with BCrypt password hashes; log in for a signed JWT.
- Request future bookings; the API checks time overlap and serializes booking attempts per bike.
- View customer bookings, cancel eligible bookings, and update booking status as an administrator.
- Record academic test payment entries for Cash, UPI, and Card labels.
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

Open `http://localhost:5173`. Set `VITE_API_URL` in `frontend/.env.local` if the API URL differs. An example is in `frontend/.env.example`.

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
| POST | `/api/payments` | Booking owner | Create clearly labeled test payment (`CASH`, `UPI`, `CARD`) |
| GET | `/api/payments` | Admin | List payments |
| POST | `/api/contact` | Public | Save contact message |

Send authenticated requests with `Authorization: Bearer <token>`.

## Booking and pricing behavior

The API requires a future pickup time and a return time after pickup. It takes a row lock on the selected bike inside a transaction, checks pending/confirmed/picked-up booking windows for overlap, and rejects a conflict with HTTP 409. Price is rounded up to whole rental days using the daily rate; the security deposit is added to the estimate. Availability is period-based and bikes are not globally disabled by a single booking.

## Testing status and known limits

This generation environment has Node.js but no npm executable, Maven, a usable JDK compiler, or verified MySQL server. Therefore a production frontend build, Maven compile/tests, database migration, and live API walkthrough could not be run here. Install the prerequisites above and run `npm run build` and `mvn test` locally before presenting. Automated tests have not been added yet.

The included implementation is an academic starter with functional core flows, not every requested enterprise module. The frontend does not yet include a complete bike CRUD editor, customer-management screens, reports/analytics, password reset/profile editor, booking receipt printing, reviews UI, full booking history filters, or payment checkout/receipt screen. Those remain enhancements. Bike image URLs are remote Unsplash sample imagery.

## Screenshots

Add screenshots to `screenshots/` and link them here.

## Future work

Add automated unit/integration tests, admin fleet/customer CRUD, revenue reporting, receipt generation, more robust validation and authorization, database migrations, and a real payment gateway only if the project later needs real transactions.

## Contributors and license

See [CONTRIBUTING.md](CONTRIBUTING.md). Released under the MIT License; see [LICENSE](LICENSE).
