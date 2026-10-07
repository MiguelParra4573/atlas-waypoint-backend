# Atlas Waypoint — Backend

Torre de control de flota: posición en vivo de 50 a 200 vehículos simulados, estado de entregas y alertas operativas, con acceso por rol.

Stack: Spring Boot 4 (WebFlux, Java 21) · R2DBC · Liquibase · Kafka · Redis · Postgres. El frontend (React + TypeScript) vive en [atlas-waypoint-frontend](https://github.com/MiguelParra4573/atlas-waypoint-frontend). Plan completo en `docs/Plan Torre de control de flota (portafolio).md`.

## Estructura

Sugerencia de disposición local: una carpeta `atlas-waypoint/` con este repo en `backend/` y el frontend clonado en `frontend/`.

| Ruta | Contenido |
| --- | --- |
| `src/` | Servicio Spring Boot (API, ingesta y reglas) |
| `simulator/` | Simulador de flota (Fase 3) |
| `docs/` | Plan y ADRs (`docs/adr/`) |
| `docker-compose.yml` | Postgres, Redis, Kafka (KRaft) y Kafka UI para desarrollo local |

## Cómo levantar el entorno

Requisitos: Java 21 y Docker (también para los tests, que usan Testcontainers).

```bash
cp .env.example .env
docker compose up -d          # Postgres :5434, Redis :6379, Kafka :9094 (KRaft), Kafka UI :8088
./mvnw spring-boot:run
curl localhost:8080/actuator/health   # {"status":"UP"}
```

## Tests

```bash
./mvnw verify
```

Commits convencionales (`feat:`, `fix:`, `chore:`...). Una fase = una rama + un PR + un tag.
