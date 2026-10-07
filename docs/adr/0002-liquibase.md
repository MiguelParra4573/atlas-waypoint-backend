# ADR 0002: Liquibase para migraciones

**Estado:** aceptada · 2026-10-07

## Contexto
El esquema de Postgres necesita migraciones versionadas y reproducibles en local, CI (Testcontainers) y despliegue. El plan original contemplaba Flyway.

## Decisión
Liquibase, con changelog maestro en `src/main/resources/db/changelog/db.changelog-master.yaml` que incluye en orden alfabético los archivos `NNN-descripcion.yaml` de `db/changelog/changes/`.

## Consecuencias
- Liquibase requiere JDBC: el servicio incluye el driver `postgresql` además de `r2dbc-postgresql`. JDBC se usa solo al arrancar; el acceso a datos en runtime es R2DBC.
- En tests, `@ServiceConnection` sobre el contenedor Postgres configura ambos (JDBC para Liquibase, R2DBC para la app).
- Cambios aplicados nunca se editan: se agrega un changeSet nuevo.
