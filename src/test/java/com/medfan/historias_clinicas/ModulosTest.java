package com.medfan.historias_clinicas;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import static org.assertj.core.api.Assertions.assertThat;

class ModulosTest {
    ApplicationModules modulos = ApplicationModules.of(HistoriasClinicasApplication.class);

    @Test
    void lasFronterasSeRespetan() {
        modulos.forEach(System.out::println);
        modulos.verify();
        assertThat(modulos.stream().map(modulo -> modulo.getIdentifier().toString()).toList())
            .containsExactlyInAnyOrder("historias", "seguridad", "auditoria");
    }

    @Test
    void generarDocumentacion() {
        new Documenter(modulos).writeDocumentation();
    }
}
