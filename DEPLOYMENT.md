# Public deployment and test payments

This repository is prepared for Railway with three services: a React frontend, a Spring Boot API, and MySQL. The app services build from their Dockerfiles; the frontend and API each receive a Railway HTTPS domain. The production profile requires `JWT_SECRET` and `CORS_ORIGIN` and binds the API to the container's public interface. The production API seeds the existing four-bike sample fleet only when the database contains no bikes.

## Deploy the services

1. Create a Railway project from the `main` branch of this GitHub repository.
2. Add a Railway MySQL database service and keep it private. The application only connects to it from inside the project.
3. Add a backend service from the same repository and set its root directory to `/backend`. Railway detects `backend/Dockerfile`.
4. Add a frontend service from the same repository and set its root directory to `/frontend`. Railway detects `frontend/Dockerfile`.
5. Deploy the backend after setting its variables below. In the backend service's Networking settings, generate its public domain. Railway-provided domains are HTTPS-enabled.
6. Set the frontend's `VITE_API_URL` to `https://<backend-domain>/api`, then deploy it and generate its public domain.
7. Update the backend's `CORS_ORIGIN` to the exact frontend origin, such as `https://<frontend-domain>` (no trailing slash), and redeploy the backend.
8. Open the frontend's HTTPS domain and confirm bike listings and the no-charge test payment flow work.

## Backend environment variables

Set these in Railway's backend Variables tab. Reference the generated MySQL service variables rather than copying its credentials:

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `production` |
| `MYSQLHOST` | Reference the MySQL service's `MYSQLHOST` variable |
| `MYSQLPORT` | Reference `MYSQLPORT` |
| `MYSQLDATABASE` | Reference `MYSQLDATABASE` |
| `MYSQLUSER` | Reference `MYSQLUSER` |
| `MYSQLPASSWORD` | Reference `MYSQLPASSWORD` |
| `JWT_SECRET` | A unique random secret with at least 32 bytes; keep it in Railway only |
| `CORS_ORIGIN` | The deployed frontend HTTPS origin |

The app composes the JDBC URL from the `MYSQL*` variables. Do not make the MySQL service public.

## Test payment modes

The safe default for this deployment is the simulated test payment. Set these variables on the frontend and backend services:

| Variable | Value |
| --- | --- |
| `DEMO_PAYMENTS_ENABLED` | `true` on the backend |
| `VITE_DEMO_PAYMENTS` | `true` on the frontend before its build |

This mode confirms bookings as `TEST_PAID` and clearly reports that no money was charged. It does not call Razorpay. It is for demonstrations and testing only.

To test Razorpay's sandbox checkout instead, set both demo variables to `false` and put only Razorpay **Test Mode** values for `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET` in the backend Variables tab. Never put the key secret or webhook secret in frontend variables or Git. With a public Railway backend domain, configure the sandbox webhook at `https://<backend-domain>/api/payments/webhook` for `payment.captured`. Do not add Live Mode keys to this test deployment.

## Billing and launch limits

Railway currently lists a $5/month Hobby minimum usage commitment, with extra charges possible when resource usage exceeds the included credit. The free plan is limited and should not be treated as a guaranteed always-on production host. Review Railway's live pricing before creating or upgrading a project. [Railway pricing](https://railway.com/pricing)

The sample bike fleet is demo inventory. Replace its names, prices, descriptions, and images before accepting real bookings. This configuration is intentionally test-only and does not collect money.
