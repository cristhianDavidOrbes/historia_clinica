package com.medfan.historias_clinicas.seguridad.internal;

import com.medfan.historias_clinicas.seguridad.SeguridadApi.Actor;
import com.medfan.historias_clinicas.seguridad.internal.SeguridadService.Atencion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
class SeguridadController {
    private final SeguridadService servicio;

    SeguridadController(SeguridadService servicio) {
        this.servicio = servicio;
    }

    // Datos de entrada dentro del controlador, siguiendo el ejemplo de Carnaval.
    record DatosAtencion(@NotNull @Positive Long pacienteId,
                         @NotBlank @Size(max = 80) String profesional,
                         @NotBlank @Pattern(regexp = "CONSULTORIO|URGENCIAS") String servicio) {}

    @GetMapping("/seguridad/sesion")
    Actor actual() {
        return servicio.actual();
    }

    @PostMapping("/atenciones")
    @ResponseStatus(HttpStatus.CREATED)
    Atencion asignar(@Valid @RequestBody DatosAtencion datos) {
        return servicio.asignarAtencion(datos.pacienteId(), datos.profesional(), datos.servicio());
    }

    @GetMapping("/atenciones")
    List<Atencion> listar() {
        return servicio.listarAtenciones();
    }

    @PutMapping("/atenciones/{id}/cierre")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cerrar(@PathVariable long id) {
        servicio.cerrarAtencion(id);
    }
}
