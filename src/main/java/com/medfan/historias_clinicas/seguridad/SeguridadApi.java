package com.medfan.historias_clinicas.seguridad;

// Contrato utilizado por historias.
public interface SeguridadApi {
    record Actor(String usuario, String rol, String servicio) {}

    Actor actual();
    Actor autorizar(long pacienteId, boolean escritura);
    Actor autorizarEmergencia(long pacienteId);
}
