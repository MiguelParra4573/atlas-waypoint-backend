# Convenciones de código — atlas-waypoint-backend

Cómo se escribe el código de este repo y qué lo verifica. El **por qué** de cada elección está en
`docs/adr/0004-harness-de-calidad.md`; acá está el **cómo**.

La arquitectura —qué depende de qué— es otro documento: `../docs/arquitectura.md` en la raíz del
workspace, que sirve a los dos repos.

## Puesta a punto

Una vez por clon:

```bash
git config core.hooksPath .githooks                      # activa los hooks de git
git config blame.ignoreRevsFile .git-blame-ignore-revs    # saca el reformateo del blame
```

Hacen falta además **JDK 21** —no 22, no 25: el rango está cerrado a propósito, ver abajo— y
**Podman o Docker corriendo** para `./mvnw verify`, porque los tests de repositorio levantan un
Postgres real con Testcontainers.

## Los gates que rompen el build

| Gate | Fase de Maven | Qué verifica | Cómo se arregla |
| --- | --- | --- | --- |
| `maven-enforcer-plugin` | `validate` | Versión de Maven y de Java, sin dependencias duplicadas, sin versiones dinámicas ni snapshots, sin repositorios propios | El mensaje lo dice; suele ser usar `./mvnw` y no `mvn` |
| `spotless:check` | `validate` | Formato de todo `src/**/*.java` | `./mvnw spotless:apply` |
| `-Werror` (javac) | `compile` | Cualquier warning de javac | Arreglar el warning; como último recurso, el escape de abajo |
| Error Prone | `compile` | Bugs verificables (`SelfAssignment`, `ReturnValueIgnored`, `EqualsHashCode`…) | Arreglar; Error Prone propone el parche en el mensaje |
| NullAway | `compile` | Nulos en los paquetes `@NullMarked` | Anotar `@Nullable` lo que de verdad puede ser nulo, o corregir el flujo |
| ArchUnit | `test` | Dependencias entre capas y módulos, más las convenciones de código | Ver los dos archivos de reglas, abajo |
| `jacoco:check` | `verify` | Umbral de cobertura | Escribir el test que falta |

Los dos primeros están en `validate` a propósito: fallan en segundos, antes de que Testcontainers
baje una imagen de Postgres.

## Formato

**google-java-format en modo AOSP**: 4 espacios de bloque, 8 de continuación, 100 columnas. No se
discute el estilo; lo aplica Spotless.

```bash
./mvnw spotless:apply    # formatea
./mvnw spotless:check    # verifica (lo corre validate)
```

El `.editorconfig` declara el mismo ancho de 100 columnas, así que el IDE y el formateador dicen lo
mismo.

No hay pasos de `importOrder` ni `removeUnusedImports`: los hace el propio google-java-format, y dos
motores sobre el mismo archivo se pelean en cada `apply`. El markdown **no** se formatea: lo gobierna
el `.editorconfig` y nada más, porque el motor de markdown de Spotless normaliza tablas de forma
agresiva y el `CLAUDE.md` tiene tablas hechas a mano.

Un commit de reformateo masivo **no lleva ningún otro cambio, nunca**, y su SHA va a
`.git-blame-ignore-revs`.

## Nulabilidad

Se usa **JSpecify**, que es lo que Spring Framework 7 y Reactor 3.8 ya usan de forma nativa.

El alcance de NullAway lo declara el código, no el `pom.xml`: un `package-info.java` por paquete que
se quiera analizar.

```java
@org.jspecify.annotations.NullMarked
package com.promethea.atlas.waypoint.fleet.domain;
```

Dentro de un paquete `@NullMarked`, todo es no-nulo salvo lo que se marque. En un `record` se anota
el componente:

```java
public record Vehicle(
        @Id @Nullable UUID id,          // nulo antes de insertar: lo genera Postgres
        String plate,
        @Nullable Integer capacityKg,
        VehicleStatus status) {
```

Funciona porque `org.jspecify.annotations.Nullable` es `TYPE_USE` y en un componente de record se
propaga al campo, al accesor y al parámetro del constructor canónico, los tres a la vez. Las
anotaciones de Spring Data (`@Id`, `@CreatedDate`) son `FIELD`/`METHOD`, así que no colisionan.

**Esto no cambia el comportamiento en runtime.** El mapeo de Spring Data R2DBC usa el constructor
persistente por reflexión y no lee `@Nullable`. Es documentación que el compilador verifica, nada
más: no se ganó ninguna validación de entrada.

Paquetes a anotar: `..domain..` y `..application..`. Los adaptadores se dejan sin anotar a propósito
—repositorios con métodos abstractos, converters, configs con beans que rellena el framework— porque
ahí NullAway produce falsos positivos. Error Prone, en cambio, **sí** corre en los adaptadores: es
donde viven los bugs que atrapa.

## Cobertura

```bash
./mvnw verify                       # genera target/site/jacoco/index.html
```

Umbral vigente: ver `jacoco.linea.minima` y `jacoco.rama.minima` en el `pom.xml`. Están en `0.00`
mientras se mide, porque fijar un número a ojo sobre records con accesores triviales es elegir entre
uno que se cumple sin escribir un test y uno que rompe el build el primer día.

Procedimiento para fijarlo: correr `verify`, leer el reporte, y poner la medición **redondeada hacia
abajo al 0,05**. No al 0,01: un umbral en 0,78 exacto rompe el build con un refactor de tres líneas,
y entonces lo primero que se aprende del harness es a bajarle el número.

**El umbral solo sube.** Subirlo es parte del cierre de cada fase del plan: se lee el reporte y se
redondea hacia abajo el valor nuevo. Bajarlo pide una entrada en `docs/bitacora.md` con la razón.
Destino declarado: **0,70 de LINE**, por coherencia con `atlas/services`.

`BRANCH` importa más que `LINE` para reglas de negocio: una transición de estado con cuatro ramas
cubierta en una línea de test da `LINE` alto y `BRANCH` bajo, y `BRANCH` es la verdad.

## Las reglas verificadas

Dos archivos en `src/test`, con dos propósitos distintos:

| Archivo | Qué gobierna | Documento |
| --- | --- | --- |
| `ArquitecturaTests.java` | Dependencias entre capas y entre módulos | `../docs/arquitectura.md` § Verificación de las fronteras |
| `ConvencionesDeCodigoTests.java` | Buenas prácticas dentro de una clase | este documento |

Los dos analizan **solo `src/main`**: los tests usan `@Autowired` en campos y `block()` a propósito,
y eso es legítimo. Ese `DO_NOT_INCLUDE_TESTS` no es un detalle, es el invariante que hace seguras las
reglas.

Lo que `ConvencionesDeCodigoTests` prohíbe:

- **Llamadas bloqueantes** (`block`, `blockFirst`, `blockLast`, `blockOptional`, `toIterable`,
  `toStream`). Hace ejecutable el ADR 0001, que antes era solo prosa.
- **Inyección por campo** (`@Autowired`, `@Value`, `@Inject` en campos). La inyección es por
  constructor: hace la dependencia visible y la clase testeable sin contenedor.
- Anotaciones de Spring (`@Component`, `@Service`, `@Repository`) en `..domain..`.
- `System.out`, `System.err`, `printStackTrace`.
- `java.util.Date`, `Calendar`, `SimpleDateFormat`. Se usa `OffsetDateTime`.
- `Thread.sleep`, que es la otra forma de parar un event loop. Lo correcto es `Mono.delay`.
- Leer la hora del sistema desde `..domain..` (`OffsetDateTime.now`, `Instant.now`,
  `System.currentTimeMillis`): el instante entra como parámetro o por un `Clock`, o la regla no se
  puede probar.
- Acceso a los repositorios de Spring Data desde fuera de `..infrastructure..` y `..application..`.
- Entidades de dominio devueltas desde `..rest..`: los DTOs van separados.
- Nombres que no ubican la capa: `*Controller` fuera de `..rest..`, `*Repository` fuera de
  `..infrastructure..`.

Si una regla molesta, se discute el documento y se cambia la regla. No se desactiva.

Dos límites declarados, en vez de disimulados:

- La regla de los DTOs mira el **tipo de retorno crudo**. Un `Mono<Vehicle>` borra su parámetro en
  bytecode, así que la regla ve `Mono` y lo deja pasar. Atrapa el retorno directo, que es como se
  escribe el atajo.
- Varias reglas llevan `allowEmptyShould(true)` mientras el paquete al que apuntan no exista. Cada
  válvula dice al lado qué la retira, y las que apuntan a `..domain..` y `..rest..` se retiran con
  el código de la Fase 1.

Y una trampa de ArchUnit que vale escribir, porque se descubrió en este repo y cuesta horas: una
`ArchCondition` propia usada con `noClasses().should(...)` tiene que registrar el hallazgo con
`SimpleConditionEvent.satisfied`, **no** con `violated`. `noClasses()` envuelve la condición en una
negación que da vuelta la bandera de cada evento, así que con `violated` la regla pasa sobre código
que la viola, sin dar ninguna señal. Se encontró inyectando la violación a propósito, que es la
única forma de encontrarlo.

## Idioma y nombres

Este repo **diverge a propósito** de `atlas/services/docs/convenciones.md` § Convenciones
transversales, que manda identificadores en inglés.

- En **inglés**: nombres de tabla y de columna, campos de DTO, nombres de evento, nombres de tipo
  (`Vehicle`, `DriverAssignment`, `VehicleStatus`).
- En **español**: la prosa, el Javadoc, y los **nombres de método de negocio** (`nuevo`,
  `conEstado`, `vigente`, `findOperativos`), porque nombran reglas del dominio y el dominio se
  discute en español.

Razón de la divergencia: es un proyecto de portafolio de un autor, no un ERP multiequipo. El costo
de coordinación que justifica el inglés en `atlas/services` no existe acá.

## Commits

Convencionales, validado por `.githooks/commit-msg`:

```
<tipo>(<alcance opcional>): <asunto en minúscula, hasta 80 caracteres>
```

Tipos: `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `style`, `test`. El cuerpo
y el pie quedan libres. Los mensajes que genera git solo (`Merge`, `Revert`, `fixup!`, `squash!`) no
se validan.

El `pre-commit` corre `validate` —formato y reglas de entorno— y sale en milisegundos si el commit no
toca `.java` ni `pom.xml`. **Verifica, nunca arregla**: un hook que reescribe archivos con staging
parcial deja el índice desincronizado y commitea algo que nadie leyó.

## Escapes documentados

Dos, para el día de un upgrade de Boot o de JDK. **CI no los pasa nunca**, así que CI siempre es
estricto.

```bash
./mvnw verify -Dcompilacion.fallarPorWarning=false   # apaga -Werror
./mvnw verify -Dsin-calidad-estricta                 # apaga Error Prone y NullAway
```

Un `-Xlint:-xxx` nuevo en el `pom.xml` requiere un comentario que nombre el warning literal y, si es
por un API de tercero, la condición para retirarlo.

## Trampas conocidas

Cuatro cosas que no se deducen leyendo el `pom.xml`:

1. **`.mvn/jvm.config` no es basura del wrapper.** Lo necesitan google-java-format **y** Error Prone:
   los dos usan internals de `com.sun.tools.javac` y sin esos `add-exports` fallan con
   `IllegalAccessError`. Si se borra, se rompen las dos piezas a la vez. No admite comentarios:
   Maven pasa el archivo entero como argumentos y un `#` rompe el arranque.
2. **Declarar `annotationProcessorPaths` apaga el descubrimiento de procesadores del classpath.**
   Hoy no hay ninguno. Cuando entre `spring-boot-configuration-processor`, hay que listarlo ahí o
   deja de correr **en silencio**: no da error, simplemente no genera el
   `spring-configuration-metadata.json`.
3. **Si alguna vez se configura `<argLine>` en Surefire, tiene que ser `<argLine>@{argLine} …`.** Sin
   el `@{argLine}` el agente de JaCoCo desaparece y la cobertura se reporta **0 sin ningún error**.
4. **`-parameters` y `--release 21` los pone `spring-boot-starter-parent`**, no este `pom.xml`. No se
   repiten.

Y una del entorno: los hooks se ejecutan con el `sh` de Git for Windows, así que `./mvnw` resuelve y
`mvnw.cmd` no se usa. Si un hook tuviera CRLF fallaría con `bad interpreter: /bin/sh^M` y **git no
avisa**, se lo saltaría en silencio; de eso se encarga la regla `/.githooks/** text eol=lf` del
`.gitattributes`.
