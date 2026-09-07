package com.gastoscompartidos.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * QUE PASA EN CADA REQUEST AUTENTICADO, paso a paso.
 *
 * 1. Llega la request. Este filtro corre ANTES que cualquier controlador.
 * 2. Busca el header `Authorization: Bearer <token>`.
 * 3. Si no hay, no hace nada y sigue. Ojo: no rechaza. Rechazar es tarea de
 *    Spring Security mas adelante en la cadena, que sabe que rutas son publicas
 *    (/auth/**) y cuales no.
 * 4. Si hay, valida la firma y la expiracion, y saca el id del usuario.
 * 5. Si el token es valido, deja el id en el SecurityContext, que es un
 *    ThreadLocal: vive solo durante esta request y en este hilo.
 * 6. Sigue la cadena. De ahi en mas, cualquier parte del codigo puede preguntar
 *    "quien es" sin volver a mirar el header.
 *
 * OncePerRequestFilter garantiza que corra una sola vez por request aunque haya
 * forwards internos.
 *
 * DECISION IMPORTANTE: aca NO se carga el Usuario de la base, solo su id.
 *
 * Si lo cargaramos, la entidad quedaria DETACHED para cuando el servicio la use,
 * porque el filtro corre fuera de cualquier transaccion. Y entonces el primer
 * `actual.getGrupo()` explotaria con LazyInitializationException. Dejando el id,
 * el Usuario se carga dentro del @Transactional del servicio y queda managed,
 * que es de lo que depende todo el codigo que ya escribimos.
 */
public class FiltroJwt extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final ServicioDeTokens tokens;

    public FiltroJwt(ServicioDeTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(PREFIJO)) {
            String token = header.substring(PREFIJO.length()).trim();

            tokens.identidadDe(token).ifPresent(identidad -> {
                // El "principal" es quien decis ser mas la generacion del token.
                // Sin roles: en esta app todos los usuarios autenticados pueden
                // lo mismo, y los permisos reales (que gasto podes ver) los
                // resuelve el WHERE del repositorio.
                var autenticacion = new UsernamePasswordAuthenticationToken(
                        identidad, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            });
        }

        chain.doFilter(request, response);
    }
}
