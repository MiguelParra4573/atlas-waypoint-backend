# ADR 0001: Stack reactivo (WebFlux + R2DBC)

**Estado:** aceptada · 2026-10-07

## Contexto
La torre de control empuja posiciones al navegador por SSE y consume telemetría de Kafka de forma continua. Con 100 a 500 vehículos publicando cada 2 a 5 s, el servicio mantiene muchas conexiones abiertas y mucho I/O concurrente.

## Decisión
Spring WebFlux + R2DBC + reactor-kafka + Redis reactivo, de punta a punta. Sin llamadas bloqueantes en el camino reactivo.

## Consecuencias
- Pocas hebras sostienen miles de conexiones SSE; backpressure explícito (`onBackpressureLatest`, `sample`).
- Código más difícil de depurar y de probar (`StepVerifier`, `WebTestClient`).
- R2DBC no tiene ORM: relaciones y mapeos a mano. Aceptable con un modelo de 5 entidades.
- Las migraciones usan JDBC solo al arrancar (ver ADR 0002).
