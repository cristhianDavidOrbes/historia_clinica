package com.medfan.historias_clinicas.seguridad;

// Contrato de integracion usado por historias. Su implementacion corresponde al companero de seguridad.
public interface SeguridadApi {
    record Actor(String usuario, String rol, String servicio) {}
    Actor actual();
    Actor autorizar(long pacienteId, boolean escritura);
    Actor autorizarEmergencia(long pacienteId);
}
