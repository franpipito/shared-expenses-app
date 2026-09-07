package com.gastoscompartidos.seguridad;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Configuracion de Spring Security.
 *
 * Spring Security es, en el fondo, una cadena de filtros que corre antes de los
 * controladores. Aca definimos como se arma esa cadena.
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    /**
     * OJO CON ESTO: FiltroJwt se crea aca a mano y NO lleva @Component.
     *
     * Si fuera un @Component, Spring Boot lo registraria automaticamente en la
     * cadena de filtros del servlet ADEMAS de la cadena de seguridad, y correria
     * dos veces por request. Es un error clasico y silencioso: todo "funciona",
     * pero cada request hace el doble de trabajo.
     */
    @Bean
    SecurityFilterChain cadenaDeSeguridad(
            HttpSecurity http,
            ServicioDeTokens tokens,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver manejadorDeExcepciones)
            throws Exception {
        http
                // CSRF protege contra que otro sitio haga que TU NAVEGADOR mande
                // una request con tus cookies. Aca no hay cookies: el token va en
                // un header que un sitio ajeno no puede setear. Sin cookies no
                // hay ataque CSRF, asi que la proteccion no aporta nada.
                .csrf(csrf -> csrf.disable())

                // STATELESS: no se crea HttpSession ni se guarda nada del lado
                // del servidor entre requests. Cada request se autentica sola con
                // su token. Es lo que permite que el backend escale horizontal
                // sin sesiones compartidas.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/auth/**").permitAll()
                        // Railway consulta este endpoint para saber si el
                        // contenedor esta listo antes de mandarle trafico, y lo
                        // hace sin token. Devuelve solo {"status":"UP"}:
                        // show-details=never en application.properties evita que
                        // publique el estado de la base y las demas piezas.
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())

                // Spring Security corta ANTES de que exista un controlador, asi
                // que nuestro @RestControllerAdvice no se entera y una request
                // sin token recibiria una respuesta vacia, con otro formato que
                // el resto de los errores de la API.
                //
                // En vez de escribir el JSON a mano aca (y tener el formato de
                // error definido en dos lugares), le pasamos la excepcion al
                // mismo resolver que usa Spring MVC. Termina en
                // ManejadorDeErrores.noAutenticado() como cualquier otro 401.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> manejadorDeExcepciones.resolveException(
                                req, res, null,
                                new NoAutenticadoException("Falta el token, o no es valido"))))

                .addFilterBefore(new FiltroJwt(tokens), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt: el estandar para guardar contrasenas.
     *
     * Tres propiedades que importan y que un hash comun (SHA-256, por ejemplo)
     * no tiene:
     *
     *  - Genera un SALT aleatorio por contrasena y lo guarda dentro del propio
     *    hash. Por eso dos personas con la misma contrasena tienen hashes
     *    distintos, y las rainbow tables no sirven.
     *  - Es DELIBERADAMENTE LENTO (~100ms). Eso no molesta para un login, pero
     *    hace inviable probar millones de combinaciones por segundo.
     *  - El costo es ajustable: cuando el hardware mejore, se sube el factor.
     */
    @Bean
    PasswordEncoder codificadorDeContrasenas() {
        return new BCryptPasswordEncoder();
    }
}
