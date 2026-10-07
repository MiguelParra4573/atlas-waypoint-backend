# Atlas Waypoint — Torre de control de flota

Torre de control que muestra en vivo la posición de 50 a 200 vehículos simulados, el estado de sus entregas y alertas operativas, con acceso por rol.

Stack: Spring Boot (WebFlux) · Kafka · Redis · Postgres · React + TypeScript. Plan completo en `docs/Plan Torre de control de flota (portafolio).md`.

## Estructura

| Carpeta | Contenido |
| --- | --- |
| `backend` | Servicio Spring Boot (API, ingesta y reglas) |
| `frontend` | React + Vite + TypeScript |
| `simulator` | Simulador de flota (Fase 3) |
| `docs` | ADRs y documentación |

## Cómo levantar el entorno

Requisitos: Java 21, Node 22, Docker.

```bash
cp .env.example .env
docker compose up -d          # Postgres :5434, Redis :6379, Kafka :9094 (KRaft), Kafka UI :8088
cd backend && ./mvnw spring-boot:run
curl localhost:8080/actuator/health   # {"status":"UP"}
cd frontend && npm install && npm run dev
```

## Tests

```bash
cd backend && ./mvnw verify
cd frontend && npm test
```

Commits convencionales (`feat:`, `fix:`, `chore:`...). Una fase = una rama + un PR + un tag.
