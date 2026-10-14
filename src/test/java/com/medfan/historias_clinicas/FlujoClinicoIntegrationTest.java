package com.medfan.historias_clinicas;

import com.medfan.historias_clinicas.auditoria.AuditoriaApi;
import com.medfan.historias_clinicas.seguridad.SeguridadApi;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlujoClinicoIntegrationTest {
    private static final String ESQUEMA = "clinica_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv().getOrDefault("CLINICA_TEST_JDBC_URL", "jdbc:postgresql://localhost:5432/historias_");
    private static final String USUARIO = System.getenv().getOrDefault("CLINICA_TEST_DB_USER", "postgres");
    private static final String PASSWORD = System.getenv().getOrDefault("CLINICA_TEST_DB_PASSWORD", "1234");
    private static final SeguridadApi.Actor MEDICO = new SeguridadApi.Actor("medico-prueba", "MEDICO", "CONSULTORIO");
    private static final SeguridadApi.Actor URGENCIAS = new SeguridadApi.Actor("urgencias-prueba", "MEDICO", "URGENCIAS");
    private static final SeguridadApi.Actor DIRECTOR = new SeguridadApi.Actor("director-prueba", "DIRECTOR", "ADMINISTRACION");

    @LocalServerPort int puerto;
    @Autowired JdbcTemplate jdbc;
    // Solo dobles de prueba: no reemplazan las implementaciones que haran los companeros.
    @MockitoBean SeguridadApi seguridad;
    @MockitoBean AuditoriaApi auditoria;
    private final HttpClient cliente = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @DynamicPropertySource
    static void baseAislada(DynamicPropertyRegistry propiedades) throws Exception {
        try (var conexion = DriverManager.getConnection(URL, USUARIO, PASSWORD);
             var sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE SCHEMA " + ESQUEMA);
        }
        propiedades.add("spring.datasource.url", () -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + ESQUEMA);
        propiedades.add("spring.datasource.username", () -> USUARIO);
        propiedades.add("spring.datasource.password", () -> PASSWORD);
    }

    @AfterAll
    static void retirarSoloElEsquemaDePrueba() throws Exception {
        if (!ESQUEMA.matches("clinica_test_[a-f0-9]{32}")) {
            throw new IllegalStateException("El nombre no corresponde a un esquema temporal.");
        }
        try (var conexion = DriverManager.getConnection(URL, USUARIO, PASSWORD);
             var sentencia = conexion.createStatement()) {
            sentencia.execute("DROP SCHEMA " + ESQUEMA + " CASCADE");
        }
    }

    @BeforeEach
    void prepararContratosSimulados() {
        when(seguridad.actual()).thenReturn(MEDICO);
        when(seguridad.autorizar(anyLong(), anyBoolean())).thenReturn(MEDICO);
        when(seguridad.autorizarEmergencia(anyLong())).thenReturn(URGENCIAS);
    }

    @Test
    void pacientesHistoriasYVersionesConservanSuFuncionamiento() throws Exception {
        long id = crearPaciente("TEST-HISTORIAS");
        assertThat(enviar("GET", "/pacientes", null).body()).contains("TEST-HISTORIAS");
        assertThat(enviar("GET", "/pacientes/" + id, null).statusCode()).isEqualTo(200);
        assertThat(enviar("GET", "/pacientes/999999", null).statusCode()).isEqualTo(404);
        assertThat(enviar("GET", "/historias/" + id, null).statusCode()).isEqualTo(404);

        // Comprobamos que historias respeta el rechazo que devuelve el contrato de seguridad.
        when(seguridad.autorizar(id, false)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThat(enviar("GET", "/historias/" + id, null).statusCode()).isEqualTo(403);
        doReturn(MEDICO).when(seguridad).autorizar(id, false);

        String historia = datosHistoria();
        var creada = enviar("PUT", "/historias/" + id, historia);
        assertThat(creada.statusCode()).isEqualTo(201);
        assertThat(json.readTree(creada.body()).path("version").asLong()).isEqualTo(1);
        assertThat(enviar("PUT", "/historias/" + id, historia).statusCode()).isEqualTo(409);
        assertThat(enviar("GET", "/historias/" + id, null).body()).contains("CONTENIDO_FICTICIO");

        String cambio = historia.substring(0, historia.length() - 1) + ",\"versionEsperada\":1}";
        var modificada = enviar("PUT", "/historias/" + id, cambio);
        assertThat(modificada.statusCode()).isEqualTo(200);
        assertThat(json.readTree(modificada.body()).path("version").asLong()).isEqualTo(2);
        assertThat(enviar("PUT", "/historias/" + id, cambio).statusCode()).isEqualTo(409);
        assertThat(enviar("GET", "/urgencias/pacientes/" + id, null).statusCode()).isEqualTo(200);
        verify(auditoria).registrar(MEDICO.usuario(), MEDICO.rol(), id, "CREAR_HISTORIA",
            "PERMITIDO", MEDICO.servicio(), "NORMAL");
        verify(auditoria).registrar(MEDICO.usuario(), MEDICO.rol(), id, "MODIFICAR_HISTORIA",
            "PERMITIDO", MEDICO.servicio(), "NORMAL");
    }

    @Test
    void emergenciaJustificacionYRevisionConservanSusReglas() throws Exception {
        long id = crearPaciente("TEST-EMERGENCIA");
        assertThat(enviar("PUT", "/historias/" + id, datosHistoria()).statusCode()).isEqualTo(201);
        when(seguridad.actual()).thenReturn(URGENCIAS);
        var abierta = enviar("POST", "/urgencias/pacientes/" + id + "/emergencias", null);
        assertThat(abierta.statusCode()).isEqualTo(201);
        var apertura = json.readTree(abierta.body());
        String accesoId = apertura.path("accesoId").asString();
        assertThat(apertura.path("consulta").path("modo").asString()).isEqualTo("EMERGENCIA");
        assertThat(apertura.path("consulta").path("contingencia").asBoolean()).isFalse();

        assertThat(enviar("GET", "/urgencias/emergencias", null).statusCode()).isEqualTo(403);
        when(seguridad.actual()).thenReturn(DIRECTOR);
        assertThat(enviar("PUT", "/urgencias/emergencias/" + accesoId + "/revision",
            "{\"comentario\":\"Revisado\"}").statusCode()).isEqualTo(409);

        // Otra identidad no puede justificar el acceso que abrio el profesional de urgencias.
        when(seguridad.actual()).thenReturn(MEDICO);
        assertThat(enviar("PUT", "/urgencias/emergencias/" + accesoId + "/justificacion",
            "{\"motivo\":\"Practica ficticia\"}").statusCode()).isEqualTo(404);
        when(seguridad.actual()).thenReturn(URGENCIAS);
        assertThat(enviar("PUT", "/urgencias/emergencias/" + accesoId + "/justificacion",
            "{\"motivo\":\"Practica ficticia\"}").statusCode()).isEqualTo(204);
        when(seguridad.actual()).thenReturn(DIRECTOR);
        assertThat(enviar("GET", "/urgencias/emergencias", null).body()).contains(accesoId);
        assertThat(enviar("PUT", "/urgencias/emergencias/" + accesoId + "/revision",
            "{\"comentario\":\"Ejercicio revisado\"}").statusCode()).isEqualTo(204);
        assertThat(enviar("GET", "/urgencias/emergencias", null).body()).doesNotContain(accesoId);
        assertThat(enviar("GET", "/urgencias/emergencias?pendientes=false", null).body()).contains(accesoId);
        verify(auditoria).registrar(URGENCIAS.usuario(), URGENCIAS.rol(), id, "CONSULTAR_HISTORIA",
            "PERMITIDO", URGENCIAS.servicio(), "EMERGENCIA");
    }

    @Test
    void validaElJsonYEvitaDocumentosDuplicados() throws Exception {
        assertThat(enviar("POST", "/pacientes", "{\"nombre\":\" \",\"documento\":\"X\"}").statusCode()).isEqualTo(400);
        assertThat(enviar("POST", "/pacientes", "{").statusCode()).isEqualTo(400);
        String datos = "{\"nombre\":\"Paciente ficticio\",\"documento\":\"TEST-UNICO\"}";
        assertThat(enviar("POST", "/pacientes", datos).statusCode()).isEqualTo(201);
        assertThat(enviar("POST", "/pacientes", datos).statusCode()).isEqualTo(409);
    }

    @Test
    void unaFallaDelContratoDeAuditoriaNoEntregaDatosNiConfirmaCambios() throws Exception {
        long id = crearPaciente("TEST-ASR");
        String historia = datosHistoria();
        assertThat(enviar("PUT", "/historias/" + id, historia).statusCode()).isEqualTo(201);
        // Se simula una falla de la API, sin implementar el modulo de auditoria.
        doThrow(new IllegalStateException("Falla de auditoria simulada")).when(auditoria)
            .registrar(anyString(), anyString(), eq(id), anyString(), anyString(), anyString(), anyString());

        var consulta = enviar("GET", "/historias/" + id, null);
        assertThat(consulta.statusCode()).isEqualTo(500);
        assertThat(consulta.body()).doesNotContain("CONTENIDO_FICTICIO");
        String cambio = historia.substring(0, historia.length() - 1) + ",\"versionEsperada\":1}";
        assertThat(enviar("PUT", "/historias/" + id, cambio).statusCode()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT version FROM historias_clinicas WHERE paciente_id = ?",
            Long.class, id)).isEqualTo(1L);
        var emergencia = enviar("POST", "/urgencias/pacientes/" + id + "/emergencias", null);
        assertThat(emergencia.statusCode()).isEqualTo(500);
        assertThat(emergencia.body()).doesNotContain("CONTENIDO_FICTICIO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accesos_emergencia WHERE paciente_id = ?",
            Integer.class, id)).isZero();
    }

    private long crearPaciente(String documento) throws Exception {
        var respuesta = enviar("POST", "/pacientes",
            "{\"nombre\":\"Paciente ficticio\",\"documento\":\"" + documento + "\"}");
        assertThat(respuesta.statusCode()).isEqualTo(201);
        return json.readTree(respuesta.body()).path("id").asLong();
    }

    private String datosHistoria() {
        return "{\"antecedentes\":\"CONTENIDO_FICTICIO\",\"alergias\":\"Dato ficticio B\",\"medicamentos\":\"Dato ficticio C\"}";
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
            .header("Content-Type", "application/json")
            .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
