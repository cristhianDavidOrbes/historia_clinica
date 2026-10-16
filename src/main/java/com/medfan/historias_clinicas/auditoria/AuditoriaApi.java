package com.medfan.historias_clinicas.auditoria;

// Contrato de integracion usado por los demas modulos.
public interface AuditoriaApi {
    void registrar(String usuario, String rol, Long pacienteId, String accion,
                   String resultado, String puntoAcceso, String modo);
    void registrarDenegado(String usuario, String rol, Long pacienteId, String accion,
                          String resultado, String puntoAcceso, String modo);
}
