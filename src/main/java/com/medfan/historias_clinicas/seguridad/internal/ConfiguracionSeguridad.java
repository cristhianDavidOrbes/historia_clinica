package com.medfan.historias_clinicas.seguridad.internal;

import com.medfan.historias_clinicas.auditoria.AuditoriaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class ConfiguracionSeguridad {
    @Bean
    SecurityFilterChain filtro(HttpSecurity http, AuditoriaApi auditoria) throws Exception {
        // API local de aprendizaje: Basic Auth en cada peticion, sin sesion de servidor.
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reglas -> reglas
                .requestMatchers("/error", "/pacientes/estado").permitAll()
                .requestMatchers("/auditoria/**").hasAnyRole("AUDITOR", "DIRECTOR")
                .requestMatchers("/atenciones", "/atenciones/**").hasRole("ADMISION")
                .requestMatchers(HttpMethod.POST, "/pacientes").hasRole("ADMISION")
                .requestMatchers("/pacientes", "/pacientes/**").hasAnyRole("ADMISION", "MEDICO", "ENFERMERIA")
                .anyRequest().authenticated())
            .exceptionHandling(errores -> errores
                .authenticationEntryPoint((solicitud, respuesta, error) -> {
                    auditoria.registrarDenegado("ANONIMO", "SIN_ROL", null,
                        "AUTENTICACION_HTTP", "DENEGADO", "API", "NORMAL");
                    respuesta.setHeader("WWW-Authenticate", "Basic realm=\"clinica-local\"");
                    respuesta.sendError(401, "Credenciales requeridas.");
                })
                .accessDeniedHandler((solicitud, respuesta, error) -> {
                    auditoria.registrarDenegado(solicitud.getRemoteUser(), "HTTP", null,
                        "AUTORIZACION_HTTP", "DENEGADO", "API", "NORMAL");
                    respuesta.sendError(403, "Permisos insuficientes.");
                }))
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    UserDetailsService usuariosLocales(@Value("${CLINICA_DEMO_PASSWORD:Aprender123!}") String clave) {
        // Cuentas ficticias para aprender. Cada cuenta conserva la clave codificada con BCrypt.
        String hash = "{bcrypt}" + new BCryptPasswordEncoder().encode(clave);
        return new InMemoryUserDetailsManager(
            User.withUsername("admision").password(hash).authorities("ROLE_ADMISION").build(),
            User.withUsername("medico").password(hash).authorities("ROLE_MEDICO", "SERVICIO_CONSULTORIO").build(),
            User.withUsername("medico2").password(hash).authorities("ROLE_MEDICO", "SERVICIO_CONSULTORIO").build(),
            User.withUsername("enfermeria").password(hash).authorities("ROLE_ENFERMERIA", "SERVICIO_URGENCIAS").build(),
            User.withUsername("urgencias").password(hash).authorities("ROLE_MEDICO", "SERVICIO_URGENCIAS").build(),
            User.withUsername("auditor").password(hash).authorities("ROLE_AUDITOR").build(),
            User.withUsername("director").password(hash).authorities("ROLE_DIRECTOR").build()
        );
    }
}
