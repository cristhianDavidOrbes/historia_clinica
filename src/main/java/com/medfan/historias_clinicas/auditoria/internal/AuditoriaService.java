package com.medfan.historias_clinicas.auditoria.internal;

import com.medfan.historias_clinicas.auditoria.AuditoriaApi;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditoriaService implements AuditoriaApi {
    private final AuditoriaRepository repositorio;

    AuditoriaService(AuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional
    public void registrar(String usuario, String rol, Long pacienteId, String accion,
                          String resultado, String puntoAcceso, String modo) {
        procesar(new AuditoriaEvento(UUID.randomUUID(), usuario, rol, pacienteId, accion,
            resultado, puntoAcceso, modo));
    }

    @Override
    @Transactional
    public void registrarDenegado(String usuario, String rol, Long pacienteId, String accion,
                                  String resultado, String puntoAcceso, String modo) {
        procesar(new AuditoriaEvento(UUID.randomUUID(), usuario, rol, pacienteId, accion,
            resultado, puntoAcceso, modo));
    }

    @Transactional
    void procesar(AuditoriaEvento evento) {
        repositorio.guardar(evento);
    }

    List<Reporte> reportar(Instant desde, Instant hasta, String usuario, String accion) {
        return repositorio.buscar(desde, hasta, usuario, accion);
    }

    record Reporte(UUID eventId, String usuario, String rol, Long pacienteId,
                   String accion, String resultado, String puntoAcceso, String modo,
                   Instant registradoEn) {
    }
}
