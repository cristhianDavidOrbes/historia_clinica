# Modulo de historias clinicas

Esta entrega contiene tu modulo de historias. Su codigo ejecutable se conserva: solo se agregaron comentarios para explicar las peticiones HTTP, la inyeccion por constructor, el SQL, las transacciones, los records y el control de versiones.

Las implementaciones de seguridad y auditoria corresponden a tus companeros. Se retiraron sus servicios, controladores, configuracion, cuentas de practica y SQL de inicializacion. Se conservan SeguridadApi y AuditoriaApi como contratos necesarios para que historias compile y pueda integrarse con ellos.

## Estructura

```text
com.medfan.historias_clinicas
├── HistoriasClinicasApplication.java
├── historias
│   ├── HistoriasApi.java
│   └── internal
│       ├── HistoriasController.java
│       └── HistoriasService.java
├── seguridad
│   └── SeguridadApi.java
└── auditoria
    └── AuditoriaApi.java
```

Spring Modulith sigue reconociendo tres paquetes de modulo. Seguridad y auditoria tienen solamente interfaces pendientes de implementar. Las llamadas entre modulos son directas y pasan por esas interfaces, siguiendo el ejemplo de Carnaval Tickets ST-01.

## Tu codigo

- HistoriasController recibe el JSON, valida sus campos y llama al servicio.
- HistoriasService contiene el SQL con JdbcTemplate y las operaciones clinicas.
- HistoriasApi expone las consultas y el record ResumenClinico.
- schema.sql conserva las tablas pacientes, historias_clinicas y accesos_emergencia.

Historias conserva registro y consulta de pacientes, creacion y modificacion de historias, lectura normal y de urgencias, apertura de emergencia, justificacion y revision posterior. El control de version evita sobrescribir una edicion mas reciente. Una historia inexistente devuelve 404.

Los comentarios dentro de los tres archivos explican cada parte. No se quitaron las llamadas a seguridad y auditoria: forman parte de los puntos de integracion de tu codigo.

## Trabajo de los companeros

El responsable de seguridad debe crear un bean que implemente SeguridadApi y configurar la autenticacion. actual() entrega el usuario, rol y servicio. autorizar(pacienteId, escritura) permite o rechaza la lectura/escritura; autorizarEmergencia(pacienteId) decide el acceso excepcional. Los rechazos se comunican mediante una excepcion.

El responsable de auditoria debe crear un bean que implemente AuditoriaApi. registrar(...) recibe los metadatos de las operaciones permitidas; registrarDenegado(...) recibe rechazos del flujo de revision. El contenido clinico no se envia a esta API. Para confirmar el cambio clinico y su auditoria juntos, registrar debe participar en la transaccion existente; un rechazo que deba sobrevivir a una reversion necesita una transaccion independiente.

La aplicacion completa no arranca hasta contar con ambos beans. No se agregaron implementaciones vacias que autoricen todo o ignoren la auditoria. Los sustitutos usados en las pruebas existen exclusivamente en src/test.

## Pruebas

La verificacion de Spring Modulith no necesita una base de datos:

```powershell
mvn -Dtest=ModulosTest test
```

ModulosTest sigue el ejemplo de Carnaval: imprime los modulos, ejecuta modulos.verify() y genera documentacion en target/spring-modulith-docs. Tambien comprueba los nombres historias, seguridad y auditoria.

Para comprobar historias con PostgreSQL y contratos simulados:

```powershell
mvn clean verify
```

FlujoClinicoIntegrationTest usa @MockitoBean para simular las dos interfaces. Verifica pacientes, validacion, documentos duplicados, versiones, delegacion del permiso, emergencia, justificacion, revision y reversion de cambios cuando falla la llamada de auditoria. Estas pruebas no certifican las implementaciones de tus companeros.

Cada ejecucion usa un esquema temporal clinica_test_ con un identificador aleatorio y lo retira al terminar. No modifica las tablas de public. La conexion predeterminada de pruebas es historias_, usuario postgres y clave 1234. Puede usarse otra base disponible:

```powershell
$env:CLINICA_TEST_JDBC_URL = 'jdbc:postgresql://localhost:5432/postgres'
$env:CLINICA_TEST_DB_USER = 'postgres'
$env:CLINICA_TEST_DB_PASSWORD = '1234'
mvn clean verify
```

La cuenta de pruebas necesita permiso para crear esquemas. Las pruebas usan un puerto HTTP aleatorio.

## Ejecutar despues de integrar los modulos

```powershell
docker compose up -d
mvn spring-boot:run
```

application.yaml conserva la conexion principal a historias_; DB_URL, DB_USER y DB_PASSWORD permiten cambiarla. La aplicacion escucha en 127.0.0.1. La autenticacion y las credenciales las definira el modulo de seguridad.

La coleccion postman/HistoriasClinicas.postman_collection.json contiene solo peticiones de historias. Conserva las rutas /pacientes, /historias y /urgencias. Configura su autorizacion segun la implementacion de tus companeros. Las peticiones requieren que ambos contratos ya esten implementados.

Eliminar codigo de inicializacion no elimina las tablas ni los datos que existan en PostgreSQL. No se ejecutaron DROP sobre tablas de la aplicacion.

## ASR que guian el trabajo del equipo

| Requisito | Aporte de historias y trabajo pendiente |
|---|---|
| Disponibilidad 99,95 % y urgencias 24/7 | Conserva el flujo de urgencias; la copia local, precarga y redundancia siguen pendientes. |
| Consulta p95 ≤ 2 segundos | Consulta por identificador; falta medir el recorrido completo con seguridad/auditoria y carga representativa. |
| 100 % de accesos auditados | Conserva llamadas antes de devolver contenido y transacciones para los cambios; el companero debe implementar el registro durable y su cobertura. |
| Reserva de la historia | Conserva la delegacion del permiso, el flujo de emergencia y revision; el companero define autenticacion, rol y relacion de atencion. |

Los requisitos proceden del Caso D y de los ADR de C:/Users/crist/Desktop/arquitectura de software. Esta entrega prepara la integracion; una prueba de fronteras de Modulith no demuestra el cumplimiento de disponibilidad, rendimiento ni auditabilidad de la aplicacion completa.
