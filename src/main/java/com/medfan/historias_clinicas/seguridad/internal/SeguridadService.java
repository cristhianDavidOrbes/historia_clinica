package com.medfan.historias_clinicas.seguridad.internal;

import com.medfan.historias_clinicas.auditoria.AuditoriaApi;
import com.medfan.historias_clinicas.seguridad.SeguridadApi;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class SeguridadService implements SeguridadApi {
    private final JdbcTemplate jdbc;
    private final AuditoriaApi auditoria;

    // Spring entrega las dependencias por constructor.
    SeguridadService(JdbcTemplate jdbc, AuditoriaApi auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @Override
    public Actor actual() {
        // La identidad viene de Spring Security, no del JSON del cliente.
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesion.");
        }

        String rol = "SIN_ROL";
        String servicio = "ADMINISTRACION";
        for (var autoridad : autenticacion.getAuthorities()) {
            String valor = autoridad.getAuthority();
            if (valor.startsWith("ROLE_")) {
                rol = valor.substring(5);
            }
            if (valor.startsWith("SERVICIO_")) {
                servicio = valor.substring(9);
            }
        }
        return new Actor(autenticacion.getName(), rol, servicio);
    }

    @Override
    public Actor autorizar(long pacienteId, boolean escritura) {
        var actor = actual();
        // Medico: lectura/escritura. Enfermeria: lectura. Ambos necesitan atencion activa.
        boolean rolPermitido = "MEDICO".equals(actor.rol())
            || (!escritura && "ENFERMERIA".equals(actor.rol()));
        if (!rolPermitido || !tieneAtencion(pacienteId, actor.usuario())) {
            denegar(actor, pacienteId, escritura ? "MODIFICAR_HISTORIA" : "CONSULTAR_HISTORIA");
        }
        return actor;
    }

    @Override
    public Actor autorizarEmergencia(long pacienteId) {
        var actor = actual();
        // Solo el medico de urgencias utiliza el acceso excepcional sin asignacion.
        if (!"MEDICO".equals(actor.rol()) || !"URGENCIAS".equals(actor.servicio())) {
            denegar(actor, pacienteId, "ABRIR_EMERGENCIA");
        }
        return actor;
    }

    public Atencion asignarAtencion(long pacienteId, String profesional, String servicio) {
        exigirAdmision(pacienteId);
        try {
            return jdbc.queryForObject("""
                INSERT INTO atenciones_activas (paciente_id, profesional, servicio)
                VALUES (?, ?, ?) RETURNING *
                """, (rs, n) -> mapearAtencion(rs), pacienteId, profesional.strip(), servicio);
        } catch (DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La atencion ya esta activa.");
        } catch (DataIntegrityViolationException error) {
            // PostgreSQL 23503: el paciente referenciado no existe.
            if (error.getMostSpecificCause() instanceof SQLException sql
                    && "23503".equals(sql.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El paciente no existe.");
            }
            throw error;
        }
    }

    public List<Atencion> listarAtenciones() {
        exigirAdmision(null);
        return jdbc.query("SELECT * FROM atenciones_activas ORDER BY id", (rs, n) -> mapearAtencion(rs));
    }

    public void cerrarAtencion(long id) {
        exigirAdmision(null);
        int filas = jdbc.update("""
            UPDATE atenciones_activas SET cerrada_en = CURRENT_TIMESTAMP
            WHERE id = ? AND cerrada_en IS NULL
            """, id);
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La atencion no existe o ya esta cerrada.");
        }
    }

    private boolean tieneAtencion(long pacienteId, String profesional) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM atenciones_activas
                WHERE paciente_id = ? AND profesional = ? AND cerrada_en IS NULL
            )
            """, Boolean.class, pacienteId, profesional));
    }

    private void exigirAdmision(Long pacienteId) {
        var actor = actual();
        if (!"ADMISION".equals(actor.rol())) {
            denegar(actor, pacienteId, "GESTIONAR_ATENCION");
        }
    }

    private void denegar(Actor actor, Long pacienteId, String accion) {
        // Llamada directa al modulo que implementara el companero de auditoria.
        auditoria.registrarDenegado(actor.usuario(), actor.rol(), pacienteId, accion,
            "DENEGADO", actor.servicio(), "NORMAL");
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu rol o atencion no permite esta operacion.");
    }

    private Atencion mapearAtencion(ResultSet rs) throws SQLException {
        var cierre = rs.getTimestamp("cerrada_en");
        return new Atencion(rs.getLong("id"), rs.getLong("paciente_id"), rs.getString("profesional"),
            rs.getString("servicio"), rs.getTimestamp("abierta_en").toInstant(),
            cierre == null ? null : cierre.toInstant());
    }

    record Atencion(long id, long pacienteId, String profesional, String servicio,
                    Instant abiertaEn, Instant cerradaEn) {}
}
