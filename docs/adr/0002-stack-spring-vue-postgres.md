# Stack: Java/Spring, Vue, Postgres, JWT, Docker, Gradle

WOLF 0.1 is a multi-user-ready personal web app (data still isolated per user). We use Java 21 + Spring Boot 3 (Gradle) for the API, Vue 3 for the UI, PostgreSQL for persistence, JWT for auth, and Docker Compose for local run.

Rejected a frontend-only/localStorage prototype: calendar + Gantt + ICS/CSV import need a real data model, backup story, and per-user isolation from day one.
