# ADR 0003: Paquetes por feature en un único servicio

**Estado:** aceptada · 2026-10-07

## Contexto
Ingesta y API viven en el mismo servicio Spring Boot (ver plan). Hay que evitar que se mezclen y que dividirlos en dos servicios después sea barato.

## Decisión
Paquetes por feature bajo `com.promethea.atlas.waypoint`, cada uno con sus capas internas (`web`, `service`, `repository`, `dto`, modelo):

- `fleet`: vehículos y conductores
- `delivery`: entregas
- `telemetry`: posiciones, consumidor Kafka, ingesta
- `alert`: reglas y alertas
- `security`: usuarios, roles, JWT
- `common`: configuración, manejo de errores (`ProblemDetail`) y utilidades compartidas

Un feature no importa clases internas de otro: se comunica por el `service` público o por eventos.

## Consecuencias
- Separar ingesta y API en dos servicios más adelante es mover paquetes.
- Más disciplina que una estructura por capas técnicas; sin enforcement automático por ahora (ArchUnit queda como opción).
