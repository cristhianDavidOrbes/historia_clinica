package com.medfan.historias_clinicas.auditoria.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuditoriaRepository {
    private final JdbcTemplate jdbc;

    AuditoriaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean guardar(AuditoriaEvento evento) {
        return jdbc.update("""
            INSERT INTO auditoria_eventos
                (event_id, usuario, rol, paciente_id, accion, resultado, punto_acceso, modo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """, evento.eventId(), evento.usuario(), evento.rol(), evento.pacienteId(),
            evento.accion(), evento.resultado(), evento.puntoAcceso(), evento.modo()) == 1;
    }

    List<AuditoriaService.Reporte> buscar(Instant desde, Instant hasta,
                                          String usuario, String accion) {
        var sql = new StringBuilder("""
            SELECT event_id, usuario, rol, paciente_id, accion, resultado,
                   punto_acceso, modo, registrado_en
            FROM auditoria_eventos WHERE 1 = 1
            """);
        var parametros = new ArrayList<Object>();
        agregarFiltro(sql, parametros, "registrado_en >= ?", desde);
        agregarFiltro(sql, parametros, "registrado_en < ?", hasta);
        agregarFiltro(sql, parametros, "usuario = ?", usuario);
        agregarFiltro(sql, parametros, "accion = ?", accion);
        sql.append(" ORDER BY registrado_en DESC");
        return jdbc.query(sql.toString(), (rs, fila) -> mapear(rs, fila), parametros.toArray());
    }

    private void agregarFiltro(StringBuilder sql, List<Object> parametros,
                               String condicion, Object valor) {
        if (valor != null) {
            sql.append(" AND ").append(condicion);
            parametros.add(valor);
        }
    }

    private AuditoriaService.Reporte mapear(ResultSet rs, int fila) throws SQLException {
        return new AuditoriaService.Reporte(rs.getObject("event_id", UUID.class),
            rs.getString("usuario"), rs.getString("rol"),
            rs.getObject("paciente_id", Long.class), rs.getString("accion"),
            rs.getString("resultado"), rs.getString("punto_acceso"),
            rs.getString("modo"), rs.getTimestamp("registrado_en").toInstant());
    }
}
