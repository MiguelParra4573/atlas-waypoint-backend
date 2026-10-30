package com.promethea.atlas.waypoint;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * El grafo de clases que comparten {@code ArquitecturaTests} y {@code ConvencionesDeCodigoTests}.
 *
 * <p>Existe por dos razones. La primera es el costo: sin el motor de JUnit 5 de ArchUnit no hay
 * caché entre clases de prueba, así que cada una volvería a importar el bytecode del proyecto y
 * pagaría el segundo de arranque por su cuenta.
 *
 * <p>La segunda es el invariante. {@link ImportOption.DoNotIncludeTests} no es un detalle de
 * configuración: es lo que hace seguras las reglas de {@code ConvencionesDeCodigoTests}. En {@code
 * src/test} un {@code block()} y un {@code @Autowired} en un campo son legítimos —un test bloquea a
 * propósito para afirmar, y un test de Spring recibe sus colaboradores del contenedor—, así que una
 * regla que mirara también los tests sería una regla que hay que desactivar. Si alguien quita esta
 * opción, las dos clases de reglas empiezan a mentir a la vez.
 */
final class ClasesDelProyecto {

    /** Paquete raíz del proyecto. Es también el prefijo que usan todas las reglas. */
    static final String RAIZ = "com.promethea.atlas.waypoint";

    /** Solo {@code src/main}. Ver el Javadoc de la clase. */
    static final JavaClasses CLASES =
            new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages(RAIZ);

    private ClasesDelProyecto() {}
}
