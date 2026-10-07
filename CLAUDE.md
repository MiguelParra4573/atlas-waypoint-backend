# CLAUDE.md

Guía para Claude Code en este repositorio. El plan completo y el estado por fase están en `docs/Plan Torre de control de flota (portafolio).md`.

## Qué es

Torre de control de flota (proyecto de portafolio): posición en vivo de 50-200 vehículos simulados, entregas y alertas operativas, con acceso por rol. Flujo: simulador -> Kafka -> ingesta/reglas -> Redis (última posición) + Postgres (histórico) -> API WebFlux (REST + SSE) -> React.

## Estructura

| Carpeta | Contenido |
| --- | --- |
| `backend/` | Un solo servicio Spring Boot 4.1 (Java 21, WebFlux). Ingesta y API separadas por paquetes, no por servicios |
| `frontend/` | Vite + React 19 + TypeScript. Tests con Vitest, lint con oxlint |
| `simulator/` | Simulador de flota (se construye en Fase 3) |
| `docs/` | Plan, ADRs (`docs/adr/`) |

Paquete base del backend: `com.promethea.atlas.waypoint`.

## Comandos

```bash
cp .env.example .env
docker compose up -d                       # Postgres :5434, Redis :6379, Kafka :9094, Kafka UI :8088
cd backend && ./mvnw spring-boot:run       # API en :8080, health en /actuator/health
cd backend && ./mvnw verify                # build + tests
cd frontend && npm install && npm run dev
cd frontend && npm run lint && npm test && npm run build
```

Los puertos de Postgres (5434) y Kafka (9094) están remapeados porque 5432/9092 suelen estar ocupados en la máquina del autor.

## Decisiones fijas

- **Migraciones con Liquibase, no Flyway.** Con R2DBC, Liquibase corre por JDBC solo al arrancar. Changelogs en `backend/src/main/resources/db/changelog/`.
- Stack reactivo de punta a punta: WebFlux, R2DBC, reactor-kafka, Redis reactivo. No introducir llamadas bloqueantes en el camino reactivo.
- DTOs separados de las entidades. Errores con `ProblemDetail` (RFC 7807).
- Kafka: clave = `vehicleId` para conservar orden por vehículo. Eventos JSON con `schemaVersion`.
- Seguridad (Fase 2): JWT de acceso de 15 min, refresh en cookie `httpOnly` con rotación, ninguna ruta sin regla explícita.

## Flujo de trabajo

- Una fase = una rama + un PR + un tag. No se avanza sin cumplir el "Hecho cuando" de la fase.
- Commits convencionales (`feat:`, `fix:`, `chore:`, `docs:`, `test:`...).
- Decisiones de arquitectura en ADRs cortos (una página) en `docs/adr/`.
- Pruebas: repositorios con Testcontainers (Postgres real), servicios con `StepVerifier`, componentes React con Testing Library.
- `.env` no se commitea; el contrato de variables vive en `.env.example`.
