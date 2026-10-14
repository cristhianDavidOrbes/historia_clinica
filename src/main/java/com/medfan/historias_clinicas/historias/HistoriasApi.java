package com.medfan.historias_clinicas.historias;

import java.time.Instant;

// Contrato publico: otros modulos llaman estos metodos sin acceder a historias.internal.
// La implementacion y el SQL estan en HistoriasService.
public interface HistoriasApi {
    // Datos que se devuelven como JSON. version y actualizadaEn permiten reconocer datos antiguos.
    // Un record genera constructor y accesores; no genera setters.
    record ResumenClinico(long pacienteId, String antecedentes, String alergias, String medicamentos,
                          long version, Instant actualizadaEn) {}

    // Lectura normal: el servicio pide autorizacion y registro de acceso a los otros modulos.
    ResumenClinico consultar(long pacienteId);
    // Lectura excepcional: SeguridadApi decide si el usuario puede usar el acceso de emergencia.
    ResumenClinico consultarEnEmergencia(long pacienteId);
}
