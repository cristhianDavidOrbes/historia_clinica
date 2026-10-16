package com.medfan.historias_clinicas.auditoria.internal;

import java.util.UUID;

record AuditoriaEvento(UUID eventId, String usuario, String rol, Long pacienteId,
                       String accion, String resultado, String puntoAcceso, String modo) {
}
