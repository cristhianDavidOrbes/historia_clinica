package com.medfan.historias_clinicas.historias.internal;

import com.medfan.historias_clinicas.auditoria.AuditoriaApi;
import com.medfan.historias_clinicas.historias.HistoriasApi;
import com.medfan.historias_clinicas.seguridad.SeguridadApi;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Bean del modulo historias: contiene las operaciones clinicas y su SQL.
// Solo depende de los contratos de seguridad/auditoria; sus implementaciones son de los companeros.
@Service
class HistoriasService implements HistoriasApi {
    // JdbcTemplate ejecuta SQL y convierte sus resultados usando las funciones mapear...
    private final JdbcTemplate jdbc;
    // Contrato externo: identifica al usuario y autoriza la operacion.
    private final SeguridadApi seguridad;
    // Contrato externo: recibe metadatos del acceso, sin el texto de los datos clinicos.
    private final AuditoriaApi auditoria;

    // Spring inyecta estas dependencias por constructor. Debe existir un bean de cada API.
    HistoriasService(JdbcTemplate jdbc, SeguridadApi seguridad, AuditoriaApi auditoria) {
        this.jdbc = jdbc;
        this.seguridad = seguridad;
        this.auditoria = auditoria;
    }

    // INSERT crea el paciente; RETURNING obtiene su id generado y sus datos.
    // Los signos ? son parametros SQL y strip() elimina espacios al principio y al final.
    public Paciente crearPaciente(String nombre, String documento) {
        try {
            return jdbc.queryForObject("""
                INSERT INTO pacientes (nombre, documento) VALUES (?, ?)
                RETURNING id, nombre, documento
                """, (rs, n) -> mapearPaciente(rs), nombre.strip(), documento.strip());
        // PostgreSQL exige un documento unico; el duplicado se comunica como HTTP 409.
        } catch (DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El documento ya esta registrado.");
        }
    }

    // query devuelve una lista. mapearPaciente convierte cada fila SQL en un record.
    public List<Paciente> listarPacientes() {
        return jdbc.query("SELECT id, nombre, documento FROM pacientes ORDER BY id",
            (rs, n) -> mapearPaciente(rs));
    }

    // Busca el id real en PostgreSQL. Una lista vacia se convierte en un error HTTP 404.
    public Paciente buscarPaciente(long id) {
        return jdbc.query("SELECT id, nombre, documento FROM pacientes WHERE id = ?",
            (rs, n) -> mapearPaciente(rs), id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El paciente no existe."));
    }

    // @Override implementa HistoriasApi. @Transactional confirma o revierte el trabajo como una unidad.
    // false significa lectura; el registro debe completarse antes de devolver la historia.
    @Override
    @Transactional
    public ResumenClinico consultar(long pacienteId) {
        var actor = seguridad.autorizar(pacienteId, false);
        var historia = obtenerHistoria(pacienteId);
        registrar(actor, pacienteId, "CONSULTAR_HISTORIA", "NORMAL");
        return historia;
    }

    // SeguridadApi decide la autorizacion excepcional; aqui solo se consulta y registra el modo.
    @Override
    @Transactional
    public ResumenClinico consultarEnEmergencia(long pacienteId) {
        var actor = seguridad.autorizarEmergencia(pacienteId);
        var historia = obtenerHistoria(pacienteId);
        registrar(actor, pacienteId, "CONSULTAR_HISTORIA", "EMERGENCIA");
        return historia;
    }

    // true pide permiso de escritura. El cambio y la llamada de auditoria comparten esta transaccion.
    @Transactional
    public ResumenClinico guardar(long pacienteId, String antecedentes, String alergias,
                                  String medicamentos, Long versionEsperada) {
        var actor = seguridad.autorizar(pacienteId, true);
        buscarPaciente(pacienteId);
        List<ResumenClinico> guardadas;
        // Sin version: creacion. ON CONFLICT evita sobrescribir una historia que ya existe.
        if (versionEsperada == null) {
            guardadas = jdbc.query("""
                INSERT INTO historias_clinicas (paciente_id, antecedentes, alergias, medicamentos)
                VALUES (?, ?, ?, ?) ON CONFLICT (paciente_id) DO NOTHING RETURNING *
                """, (rs, n) -> mapearHistoria(rs), pacienteId,
                antecedentes.strip(), alergias.strip(), medicamentos.strip());
        } else {
            // Actualiza solo si la version coincide; asi una edicion antigua no pisa otra reciente.
            guardadas = jdbc.query("""
                UPDATE historias_clinicas
                SET antecedentes = ?, alergias = ?, medicamentos = ?,
                    version = version + 1, actualizada_en = CURRENT_TIMESTAMP
                WHERE paciente_id = ? AND version = ? RETURNING *
                """, (rs, n) -> mapearHistoria(rs), antecedentes.strip(), alergias.strip(),
                medicamentos.strip(), pacienteId, versionEsperada);
        }
        // Si INSERT/UPDATE no devolvio filas, hubo conflicto de existencia o version: HTTP 409.
        var historia = guardadas.stream().findFirst().orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT, "La historia ya existe o su version cambio. Consulta la version actual."));
        registrar(actor, pacienteId, versionEsperada == null ? "CREAR_HISTORIA" : "MODIFICAR_HISTORIA", "NORMAL");
        return historia;
    }

    // Envuelve la lectura normal con su fuente. false indica que aun no hay copia offline.
    // La transaccion de este metodo cubre la llamada interna a consultar().
    @Transactional
    public Consulta consultarUrgencias(long pacienteId) {
        return new Consulta(consultar(pacienteId), "BASE_PRINCIPAL", "NORMAL", false);
    }

    // La lectura excepcional y la apertura se realizan en la transaccion de este metodo.
    // El UUID identifica el acceso clinico que posteriormente se justifica y revisa.
    @Transactional
    public Apertura abrirEmergencia(long pacienteId) {
        var historia = consultarEnEmergencia(pacienteId);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accesos_emergencia (id, paciente_id, profesional) VALUES (?, ?, ?)",
            id, pacienteId, seguridad.actual().usuario());
        return new Apertura(id, new Consulta(historia, "BASE_PRINCIPAL", "EMERGENCIA", false), true);
    }

    // Solo el profesional que abrio el acceso puede justificarlo, y solo una vez.
    @Transactional
    public void justificarEmergencia(UUID id, String motivo) {
        var actor = seguridad.actual();
        long pacienteId = jdbc.query("""
            UPDATE accesos_emergencia SET justificacion = ?, justificada_en = CURRENT_TIMESTAMP
            WHERE id = ? AND profesional = ? AND justificacion IS NULL RETURNING paciente_id
            """, (rs, n) -> rs.getLong(1), motivo.strip(), id, actor.usuario()).stream()
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No existe un acceso tuyo pendiente de justificar con ese identificador."));
        registrar(actor, pacienteId, "JUSTIFICAR_EMERGENCIA", "EMERGENCIA");
    }

    // Direccion puede consultar todos los accesos o solamente los pendientes de revision.
    public List<AccesoEmergencia> listarEmergencias(boolean pendientes) {
        exigirDirector();
        return jdbc.query("""
            SELECT * FROM accesos_emergencia WHERE NOT ? OR revisada_en IS NULL ORDER BY abierta_en
            """, (rs, n) -> mapearEmergencia(rs), pendientes);
    }

    // Exige rol DIRECTOR, justificacion previa y ausencia de una revision anterior.
    @Transactional
    public void revisarEmergencia(UUID id, String comentario) {
        var actor = exigirDirector();
        long pacienteId = jdbc.query("""
            UPDATE accesos_emergencia SET revisada_por = ?, revisada_en = CURRENT_TIMESTAMP, revision = ?
            WHERE id = ? AND justificacion IS NOT NULL AND revisada_en IS NULL RETURNING paciente_id
            """, (rs, n) -> rs.getLong(1), actor.usuario(), comentario.strip(), id).stream()
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "El acceso no existe, no esta justificado o ya fue revisado."));
        registrar(actor, pacienteId, "REVISAR_EMERGENCIA", "EMERGENCIA");
    }

    // Una historia ausente devuelve 404: no se inventan datos ni se interpreta como ausencia de alergias.
    private ResumenClinico obtenerHistoria(long pacienteId) {
        return jdbc.query("SELECT * FROM historias_clinicas WHERE paciente_id = ?",
            (rs, n) -> mapearHistoria(rs), pacienteId).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No hay una historia clinica registrada para este paciente."));
    }

    // Regla del flujo de revision. La identidad procede de SeguridadApi.
    // Si el rol no corresponde, se pide registrar el rechazo antes de responder 403.
    private SeguridadApi.Actor exigirDirector() {
        var actor = seguridad.actual();
        if (!"DIRECTOR".equals(actor.rol())) {
            auditoria.registrarDenegado(actor.usuario(), actor.rol(), null,
                "REVISAR_EMERGENCIA", "DENEGADO", actor.servicio(), "NORMAL");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta operacion corresponde a Direccion medica.");
        }
        return actor;
    }

    // Llamada directa al contrato de auditoria; su implementacion guarda el registro.
    private void registrar(SeguridadApi.Actor actor, long pacienteId, String accion, String modo) {
        auditoria.registrar(actor.usuario(), actor.rol(), pacienteId, accion,
            "PERMITIDO", actor.servicio(), modo);
    }

    // ResultSet representa una fila; getLong/getString leen columnas por su nombre.
    private Paciente mapearPaciente(ResultSet rs) throws SQLException {
        return new Paciente(rs.getLong("id"), rs.getString("nombre"), rs.getString("documento"));
    }

    // Convierte las columnas clinicas, version y fecha a los datos de salida de HistoriasApi.
    private ResumenClinico mapearHistoria(ResultSet rs) throws SQLException {
        return new ResumenClinico(rs.getLong("paciente_id"), rs.getString("antecedentes"),
            rs.getString("alergias"), rs.getString("medicamentos"), rs.getLong("version"),
            rs.getTimestamp("actualizada_en").toInstant());
    }

    // Las fechas de justificacion/revision pueden ser null mientras el acceso este pendiente.
    private AccesoEmergencia mapearEmergencia(ResultSet rs) throws SQLException {
        var justificada = rs.getTimestamp("justificada_en");
        var revisada = rs.getTimestamp("revisada_en");
        return new AccesoEmergencia(rs.getObject("id", UUID.class), rs.getLong("paciente_id"),
            rs.getString("profesional"), rs.getTimestamp("abierta_en").toInstant(),
            rs.getString("justificacion"), justificada == null ? null : justificada.toInstant(),
            rs.getString("revisada_por"), revisada == null ? null : revisada.toInstant(), rs.getString("revision"));
    }

    // Records de salida: Spring los convierte a JSON sin escribir getters/setters manualmente.
    record Paciente(long id, String nombre, String documento) {}
    record Consulta(ResumenClinico historia, String fuente, String modo, boolean contingencia) {}
    record Apertura(UUID accesoId, Consulta consulta, boolean justificacionPendiente) {}
    record AccesoEmergencia(UUID id, long pacienteId, String profesional, Instant abiertaEn,
        String justificacion, Instant justificadaEn, String revisadaPor, Instant revisadaEn, String revision) {}
}
