# MySQL setup
1. Open MySQL Workbench and connect to your local MySQL server.
2. Run `schema.sql`, then `sample-data.sql`.
3. Create a least-privilege database user and grant access to `easy_bike_rentals_db`; configure credentials in the backend run environment.
4. Register a user and optionally promote it for a local administrator demo with `UPDATE users SET role='ADMIN' WHERE email='your@email';`.

Hibernate's `ddl-auto=update` creates/updates JPA-managed core tables. The SQL scripts define the wider academic schema. Do not use development credentials in a deployed environment.
