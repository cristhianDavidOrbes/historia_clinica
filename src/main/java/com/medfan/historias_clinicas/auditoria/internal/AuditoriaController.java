package com.medfan.historias_clinicas.auditoria.internal;

import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuditoriaController {
    private final AuditoriaService servicio;

    AuditoriaController(AuditoriaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/auditoria/reportes")
    List<AuditoriaService.Reporte> reportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant hasta,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String accion) {
        return servicio.reportar(desde, hasta, usuario, accion);
    }
}
