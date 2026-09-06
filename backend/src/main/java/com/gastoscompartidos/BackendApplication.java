package com.gastoscompartidos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Se excluye UserDetailsServiceAutoConfiguration a proposito.
 *
 * Spring Boot, al ver spring-boot-starter-security sin ningun UserDetailsService
 * definido, crea un usuario "user" en memoria con una contrasena aleatoria y la
 * imprime en el log en CADA arranque.
 *
 * En esta app no se usa para nada: la autenticacion es enteramente el FiltroJwt,
 * y no hay formLogin ni httpBasic que puedan consumir ese usuario. Dejarlo solo
 * ensucia los logs con una contrasena que no sirve y hace pensar a quien lea el
 * arranque que forma parte del diseno.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	/**
	 * El reloj de la aplicacion, como bean inyectable.
	 *
	 * Dos motivos para no usar LocalDate.now() suelto por ahi:
	 *
	 * 1. TESTEABILIDAD. Con un Clock inyectado, un test puede fijar la fecha
	 *    ("hoy es 6 de septiembre") en vez de depender de cuando se ejecute.
	 *
	 * 2. ZONA HORARIA. El corte de mes depende de en que dia estas, y eso
	 *    depende de la zona. Un gasto cargado a las 21:00 del 30 de septiembre
	 *    en Buenos Aires ya es 1 de octubre en UTC. Railway y Render corren en
	 *    UTC por defecto, asi que sin fijar la zona los resumenes de fin de mes
	 *    saldrian mal una vez deployados, y seria dificil de rastrear.
	 */
	@Bean
	Clock reloj(@Value("${app.zona-horaria:America/Argentina/Buenos_Aires}") String zona) {
		return Clock.system(ZoneId.of(zona));
	}
}
