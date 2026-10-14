package com.medfan.historias_clinicas.historias.internal;

import com.medfan.historias_clinicas.historias.internal.HistoriasService.Paciente;
import com.medfan.historias_clinicas.historias.HistoriasApi.ResumenClinico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Recibe peticiones HTTP, convierte el JSON y delega el trabajo a HistoriasService.
// @RestController convierte los valores devueltos en la respuesta HTTP.
@RestController
class HistoriasController {
    // final impide cambiar la referencia al servicio despues de construir el controlador.
    private final HistoriasService servicio;

    // Spring entrega el servicio por el constructor; no necesitamos crearlo con new.
    HistoriasController(HistoriasService servicio) { this.servicio = servicio; }

    // Records de entrada: Spring convierte el JSON en estos datos.
    // @Valid activa las reglas @NotBlank, @Size y @Positive antes de llamar al metodo.
    record DatosPaciente(@NotBlank @Size(max = 100) String nombre,
                         @NotBlank @Size(max = 30) String documento) {}
    // versionEsperada ausente crea una historia; presente intenta actualizar esa version.
    record DatosHistoria(@NotBlank @Size(max = 4000) String antecedentes,
                         @NotBlank @Size(max = 4000) String alergias,
                         @NotBlank @Size(max = 4000) String medicamentos,
                         @Positive Long versionEsperada) {}
    record Justificacion(@NotBlank @Size(max = 2000) String motivo) {}
    record Revision(@NotBlank @Size(max = 2000) String comentario) {}

    // Comprueba que el proceso responde; esta ruta no consulta PostgreSQL.
    @GetMapping("/pacientes/estado")
    String estado() { return "Servicio de pacientes funcionando"; }

    // POST recibe nombre/documento y devuelve el paciente creado con estado HTTP 201.
    @PostMapping("/pacientes")
    @ResponseStatus(HttpStatus.CREATED)
    Paciente crearPaciente(@Valid @RequestBody DatosPaciente datos) {
        return servicio.crearPaciente(datos.nombre(), datos.documento());
    }

    // GET devuelve una lista; si no hay pacientes, la respuesta es [].
    @GetMapping("/pacientes")
    List<Paciente> listarPacientes() { return servicio.listarPacientes(); }

    // @PathVariable toma el numero de la URL: /pacientes/3 entrega id = 3.
    @GetMapping("/pacientes/{id}")
    Paciente buscarPaciente(@PathVariable long id) { return servicio.buscarPaciente(id); }

    // La autorizacion y el registro de acceso se delegan desde el servicio a sus APIs.
    @GetMapping("/historias/{pacienteId}")
    ResumenClinico consultar(@PathVariable long pacienteId) { return servicio.consultar(pacienteId); }

    // PUT guarda los datos clinicos. ResponseEntity permite elegir 201 al crear y 200 al actualizar.
    @PutMapping("/historias/{pacienteId}")
    ResponseEntity<ResumenClinico> guardar(@PathVariable long pacienteId,
                                           @Valid @RequestBody DatosHistoria datos) {
        var historia = servicio.guardar(pacienteId, datos.antecedentes(), datos.alergias(),
            datos.medicamentos(), datos.versionEsperada());
        return ResponseEntity.status(datos.versionEsperada() == null ? HttpStatus.CREATED : HttpStatus.OK)
            .body(historia);
    }

    // Consulta normal desde urgencias: sigue requiriendo autorizacion normal.
    @GetMapping("/urgencias/pacientes/{pacienteId}")
    HistoriasService.Consulta consultarUrgencias(@PathVariable long pacienteId) {
        return servicio.consultarUrgencias(pacienteId);
    }

    // Abre un acceso excepcional y devuelve su UUID para justificarlo despues.
    @PostMapping("/urgencias/pacientes/{pacienteId}/emergencias")
    @ResponseStatus(HttpStatus.CREATED)
    HistoriasService.Apertura abrirEmergencia(@PathVariable long pacienteId) {
        return servicio.abrirEmergencia(pacienteId);
    }

    // Guarda el motivo del acceso. 204 indica exito sin cuerpo de respuesta.
    @PutMapping("/urgencias/emergencias/{id}/justificacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void justificar(@PathVariable UUID id, @Valid @RequestBody Justificacion datos) {
        servicio.justificarEmergencia(id, datos.motivo());
    }

    // ?pendientes=true muestra solo accesos sin revision; la opcion predeterminada es true.
    @GetMapping("/urgencias/emergencias")
    List<HistoriasService.AccesoEmergencia> listarEmergencias(
            @RequestParam(defaultValue = "true") boolean pendientes) {
        return servicio.listarEmergencias(pendientes);
    }

    // Direccion revisa un acceso previamente justificado; el servicio aplica esa regla.
    @PutMapping("/urgencias/emergencias/{id}/revision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revisar(@PathVariable UUID id, @Valid @RequestBody Revision datos) {
        servicio.revisarEmergencia(id, datos.comentario());
    }
}
