package com.promethea.atlas.waypoint;

import static com.promethea.atlas.waypoint.ClasesDelProyecto.CLASES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Buenas prácticas dentro de una clase, verificadas en el build.
 *
 * <p>Es el archivo hermano de {@code ArquitecturaTests}, y la división entre los dos es deliberada:
 * aquel verifica la <em>forma</em> del código —qué paquete puede depender de qué— y es la tabla de
 * {@code ../docs/arquitectura.md} § Verificación de las fronteras, test por test. Este verifica
 * <em>cómo se escribe cada clase</em>, y su documento es {@code convenciones.md}. Meter estas
 * reglas en el otro archivo rompería ese mapeo 1:1 y dejaría al documento mintiendo.
 *
 * <p>Las dos clases analizan solo {@code src/main}; ver {@link ClasesDelProyecto}.
 *
 * <h2>Las válvulas</h2>
 *
 * <p>Varias reglas llevan {@code allowEmptyShould(true)}, porque apuntan a paquetes o a elementos
 * que todavía no existen. Sin esa válvula ArchUnit falla con <em>"rule failed because of an empty
 * set of classes"</em>, que es un rojo que no informa de nada. Con ella, la regla queda escrita y
 * empieza a morder el día en que nace el primer archivo del paquete, que es justo el objetivo:
 * poner el guardarraíl antes del código para que el código nazca cumpliéndolo.
 *
 * <p>Cada válvula dice al lado qué la retira.
 */
class ConvencionesDeCodigoTests {

    private static final String REACTOR_MONO = "reactor.core.publisher.Mono";
    private static final String REACTOR_FLUX = "reactor.core.publisher.Flux";

    /**
     * Las operaciones que convierten un publicador en un valor, es decir las que bloquean el hilo.
     *
     * <p>Se comparan por nombre completamente cualificado del propietario y no con {@code
     * assignableTo(Publisher.class)} a propósito. {@code Mono} y {@code Flux} no están entre los
     * paquetes importados, así que ArchUnit las resolvería de forma perezosa a un stub sin
     * jerarquía y la regla quedaría <em>verde sin haber evaluado nada</em>, que es el peor fallo
     * posible en un guardarraíl: el que no se nota.
     */
    private static final Set<String> FIRMAS_BLOQUEANTES =
            Set.of(
                    REACTOR_MONO + ".block",
                    REACTOR_MONO + ".blockOptional",
                    REACTOR_FLUX + ".blockFirst",
                    REACTOR_FLUX + ".blockLast",
                    REACTOR_FLUX + ".toIterable",
                    REACTOR_FLUX + ".toStream",
                    "java.lang.Thread.sleep");

    /** Los relojes que leen la hora del sistema, es decir los que vuelven un test no repetible. */
    private static final Set<String> FIRMAS_DE_RELOJ =
            Set.of(
                    "java.time.Instant.now",
                    "java.time.OffsetDateTime.now",
                    "java.time.LocalDate.now",
                    "java.time.LocalDateTime.now",
                    "java.time.ZonedDateTime.now",
                    "java.lang.System.currentTimeMillis");

    /**
     * Hace ejecutable el ADR 0001, que hasta ahora era prosa en un archivo que ni se versiona: el
     * camino es reactivo de punta a punta y no se bloquea. Un {@code block()} en un adaptador no
     * rompe ningún test —devuelve el valor correcto— y en carga agota el pool de event loops de
     * Netty, que son tantos como núcleos.
     *
     * <p>{@code Thread.sleep} va en la misma lista: es la otra forma de parar un event loop, y la
     * que aparece cuando alguien quiere "esperar un poco" en un reintento. Lo correcto es {@code
     * Mono.delay}.
     */
    @Test
    void no_hay_llamadas_bloqueantes() {
        noClasses()
                .should(llamarAlgunaDe("llamar a una operación bloqueante", FIRMAS_BLOQUEANTES))
                .because(
                        "el camino es reactivo de punta a punta (ADR 0001): se usa Mono.delay y se"
                                + " devuelve el publicador")
                .check(CLASES);
    }

    /**
     * La inyección es por constructor. Hace la dependencia visible en la firma, permite el campo
     * {@code final}, y deja la clase construible en un test sin levantar un contenedor.
     *
     * <p>Es la regla que motivó este archivo. Hoy hay cero {@code @Autowired} en {@code src/main}
     * porque todavía no hay ningún {@code @Service} ni ningún {@code @RestController}: el momento
     * correcto para escribirla es exactamente este.
     *
     * <p>Válvula: {@code allowEmptyShould} porque hoy ninguna clase de {@code src/main} declara
     * campos. Se retira sola —sin tocar este archivo— en cuanto nazca la primera clase con estado.
     */
    @Test
    void la_inyeccion_es_por_constructor() {
        noFields()
                .should()
                .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .orShould()
                .beAnnotatedWith("org.springframework.beans.factory.annotation.Value")
                .orShould()
                .beAnnotatedWith("jakarta.inject.Inject")
                .because(
                        "la inyección es por constructor: deja el campo final y la clase testeable"
                                + " sin contenedor")
                .allowEmptyShould(true)
                .check(CLASES);
    }

    /**
     * El dominio no se anota con estereotipos de Spring. {@code ArquitecturaTests} ya prohíbe que
     * el dominio <em>dependa</em> del framework; esta regla cubre el caso concreto que más tienta,
     * que es marcar una regla de negocio como {@code @Service} para poder inyectarla.
     *
     * <p>Las tres anotaciones van explícitas porque {@code beAnnotatedWith} mira la anotación
     * directa: {@code @Service} está meta-anotada con {@code @Component}, pero eso no la convierte
     * en {@code @Component} a los ojos de la regla.
     *
     * <p>Válvula: {@code ..domain..} no existe en esta rama. Se retira cuando vuelva el código de
     * la Fase 1.
     */
    @Test
    void el_dominio_no_lleva_anotaciones_de_spring() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .beAnnotatedWith("org.springframework.stereotype.Component")
                .orShould()
                .beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould()
                .beAnnotatedWith("org.springframework.stereotype.Repository")
                .because("el dominio es código Java sin framework: se instancia con new en un test")
                .allowEmptyShould(true)
                .check(CLASES);
    }

    /**
     * Nada escribe a la consola directo. Un {@code System.out} no lleva nivel, ni contexto de
     * traza, ni se puede apagar por configuración, y en un stack reactivo sale intercalado con la
     * salida de los otros event loops. Se usa un logger.
     *
     * <p>{@code printStackTrace} se compara solo por nombre de método, sin propietario: el
     * propietario es la excepción concreta y enumerarlas sería una lista que envejece.
     */
    @Test
    void no_se_escribe_a_la_consola() {
        noClasses()
                .should(
                        accederAlCampo(
                                "usar System.out o System.err",
                                "java.lang.System",
                                Set.of("out", "err")))
                .because(
                        "se usa un logger: lleva nivel, contexto de traza y se apaga por"
                                + " configuración")
                .check(CLASES);

        noClasses()
                .should(llamarAlMetodo("llamar a printStackTrace", "printStackTrace"))
                .because(
                        "la excepción se registra con el logger, que sí respeta el nivel y el"
                                + " appender")
                .check(CLASES);
    }

    /**
     * La API de fechas vieja no se usa. {@code java.util.Date} es mutable y no tiene zona, {@code
     * Calendar} es mutable y tiene meses que empiezan en cero, y {@code SimpleDateFormat} no es
     * seguro entre hilos —que en un servidor reactivo, donde un bean se comparte entre todos los
     * event loops, es corrupción de datos y no una advertencia teórica—.
     *
     * <p>Se usa {@code OffsetDateTime}, que es además lo que mapea R2DBC a {@code timestamptz}.
     */
    @Test
    void no_se_usa_la_api_de_fechas_vieja() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.util.Date")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.util.Calendar")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.text.SimpleDateFormat")
                .because(
                        "se usa OffsetDateTime: inmutable, con zona, y es lo que R2DBC mapea a"
                                + " timestamptz")
                .check(CLASES);
    }

    /**
     * El reloj entra por un puerto. Una regla de negocio que lee la hora del sistema no se puede
     * probar: no hay forma de afirmar sobre "vigente ayer" sin mover el reloj de la máquina.
     *
     * <p>La forma es un {@code Clock} inyectado, o recibir el instante como parámetro desde la capa
     * de aplicación, que es donde el reloj deja de ser un detalle del dominio.
     *
     * <p>Esta es la única regla de este archivo que se sabe que <strong>va a fallar</strong> cuando
     * vuelva el código de la Fase 1: {@code fleet/domain/DriverAssignment} tiene una sobrecarga de
     * {@code nueva} que llama a {@code OffsetDateTime.now(ZoneOffset.UTC)}. Se resuelve borrando la
     * sobrecarga, no aflojando la regla.
     *
     * <p>Válvula: {@code ..domain..} no existe en esta rama.
     */
    @Test
    void el_reloj_entra_por_un_puerto() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should(llamarAlgunaDe("leer la hora del sistema", FIRMAS_DE_RELOJ))
                .because(
                        "una regla que lee el reloj no se puede probar: el instante entra como"
                                + " parámetro o por un Clock")
                .allowEmptyShould(true)
                .check(CLASES);
    }

    /**
     * Los repositorios de Spring Data se usan desde la infraestructura y desde los casos de uso, y
     * de ningún otro sitio. Un controlador que inyecta un repositorio se salta la capa donde viven
     * la transacción y la regla de negocio, y es el atajo que convierte el hexágono en un CRUD.
     *
     * <p>Se identifican por nombre y paquete, no con {@code areAssignableTo(Repository.class)}: ese
     * predicado necesita resolver la jerarquía de Spring Data, y con el bytecode de la librería
     * fuera del grafo importado la regla quedaría verde sin evaluar nada. Misma trampa que la de
     * las llamadas bloqueantes.
     *
     * <p>Válvula: todavía no hay ni repositorios ni capas.
     */
    @Test
    void los_repositorios_solo_se_usan_desde_infraestructura_y_aplicacion() {
        DescribedPredicate<JavaClass> repositorios =
                JavaClass.Predicates.resideInAPackage("..infrastructure..")
                        .and(JavaClass.Predicates.simpleNameEndingWith("Repository"))
                        .as("son repositorios de Spring Data");

        noClasses()
                .that()
                .resideOutsideOfPackages("..infrastructure..", "..application..")
                .should()
                .dependOnClassesThat(repositorios)
                .because(
                        "el repositorio es un detalle de persistencia: se entra por el caso de uso")
                .allowEmptyShould(true)
                .check(CLASES);
    }

    /**
     * La capa REST no devuelve entidades de dominio. Es la decisión fija "DTOs separados de las
     * entidades" del {@code CLAUDE.md}: si la entidad sale por el JSON, cada renombre de un campo
     * del dominio es un cambio incompatible de la API pública, y cada campo nuevo se publica sin
     * que nadie lo decida.
     *
     * <p><strong>Límite conocido:</strong> mira el tipo de retorno crudo. Un {@code
     * Mono&lt;Vehicle&gt;} borra su parámetro en bytecode, así que la regla ve {@code Mono} y lo
     * deja pasar. Atrapa el retorno directo, que es como se escribe el atajo; no es una frontera
     * hermética y se declara en vez de disimularse.
     *
     * <p>Válvula: no hay capa {@code ..rest..} todavía.
     */
    @Test
    void los_controladores_no_devuelven_entidades_de_dominio() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("..rest..")
                .should()
                .haveRawReturnType(JavaClass.Predicates.resideInAPackage("..domain.."))
                .because("los DTOs van separados: la entidad no es el contrato público de la API")
                .allowEmptyShould(true)
                .check(CLASES);
    }

    /**
     * El nombre dice la capa. No es cosmética: es lo que permite que las reglas de arriba —y las de
     * {@code ArquitecturaTests}— se escriban por nombre en vez de resolver jerarquías de librerías
     * que no están en el grafo.
     *
     * <p>Válvula: las dos reglas apuntan a sufijos que hoy no existen.
     */
    @Test
    void los_nombres_siguen_la_capa() {
        classes()
                .that()
                .haveSimpleNameEndingWith("Controller")
                .should()
                .resideInAPackage("..rest..")
                .because("el nombre tiene que ubicar el archivo sin abrirlo")
                .allowEmptyShould(true)
                .check(CLASES);

        classes()
                .that()
                .haveSimpleNameEndingWith("Repository")
                .should()
                .resideInAPackage("..infrastructure..")
                .because("un repositorio es persistencia: si está en otra capa, el nombre miente")
                .allowEmptyShould(true)
                .check(CLASES);
    }

    // --- Condiciones propias -------------------------------------------------
    //
    // Las tres comparan nombres completamente cualificados en vez de tipos resueltos, por la razón
    // del Javadoc de FIRMAS_BLOQUEANTES: un tipo que no está en el grafo importado se resuelve a un
    // stub y deja la regla verde sin evaluar nada.
    //
    // Y las tres registran el hallazgo como SimpleConditionEvent.satisfied, no violated, aunque lo
    // que encuentren sea exactamente lo que se quiere prohibir. No es un descuido: se usan con
    // noClasses().should(...), que envuelve la condición en una negación y le da vuelta a la
    // bandera de cada evento. Con violated, la negación convertía el hallazgo en permitido y las
    // tres reglas pasaban sobre código que las viola. Se descubrió inyectando la violación a
    // propósito, que es la única forma de notarlo: una regla así no da ninguna señal.

    private static ArchCondition<JavaClass> llamarAlgunaDe(String descripcion, Set<String> firmas) {
        return new ArchCondition<JavaClass>(descripcion) {
            @Override
            public void check(JavaClass clase, ConditionEvents eventos) {
                for (JavaMethodCall llamada : clase.getMethodCallsFromSelf()) {
                    String firma = llamada.getTargetOwner().getFullName() + "." + llamada.getName();
                    if (firmas.contains(firma)) {
                        eventos.add(
                                SimpleConditionEvent.satisfied(llamada, llamada.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> llamarAlMetodo(String descripcion, String nombre) {
        return new ArchCondition<JavaClass>(descripcion) {
            @Override
            public void check(JavaClass clase, ConditionEvents eventos) {
                for (JavaMethodCall llamada : clase.getMethodCallsFromSelf()) {
                    if (nombre.equals(llamada.getName())) {
                        eventos.add(
                                SimpleConditionEvent.satisfied(llamada, llamada.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> accederAlCampo(
            String descripcion, String propietario, Set<String> campos) {
        return new ArchCondition<JavaClass>(descripcion) {
            @Override
            public void check(JavaClass clase, ConditionEvents eventos) {
                for (JavaFieldAccess acceso : clase.getFieldAccessesFromSelf()) {
                    if (propietario.equals(acceso.getTargetOwner().getFullName())
                            && campos.contains(acceso.getName())) {
                        eventos.add(
                                SimpleConditionEvent.satisfied(acceso, acceso.getDescription()));
                    }
                }
            }
        };
    }
}
