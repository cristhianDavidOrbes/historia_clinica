package com.medfan.historias_clinicas.auditoria.internal;

import org.springframework.stereotype.Component;

@Component
class AuditoriaEventReceiver {
    private final AuditoriaService servicio;

    AuditoriaEventReceiver(AuditoriaService servicio) {
        this.servicio = servicio;
    }

    void recibir(AuditoriaEvento evento) {
        servicio.procesar(evento);
    }
}
