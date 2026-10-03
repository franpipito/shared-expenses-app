package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.BorrarCuentaRequest;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.repositorio.GrupoRepositorio;
import com.gastoscompartidos.repositorio.PozoRepositorio;
import com.gastoscompartidos.repositorio.UsuarioRepositorio;
import com.gastoscompartidos.seguridad.LimitadorDeIntentos;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Borrar la cuenta de quien pregunta. Lo exige la App Store (guideline
 * 5.1.1(v)): una app que deja crear cuenta tiene que dejar borrarla desde
 * adentro, y borrarla de verdad, no desactivarla.
 *
 * QUE PASA CON CADA COSA, que es lo que promete la politica de privacidad:
 *
 *  - Sus gastos PERSONAL se borran.
 *  - Los COMPARTIDO que pago se quedan, porque son el historial de la otra
 *    persona (sus totales y saldos pasados), pero con el nombre reemplazado por
 *    "Cuenta eliminada". La descripcion queda: es parte del registro comun.
 *  - Sus aportes a una vaquita, igual: quedan, con el nombre reemplazado. Si se
 *    borraran, el pozo dejaria de cerrar.
 *  - Si era el ULTIMO integrante del grupo, no queda nadie cuyo historial
 *    cuidar: se va todo (gastos, vaquitas, categorias y el grupo).
 *
 * EL ORDEN IMPORTA, porque son varias escrituras sin transaccion: primero los
 * datos, el usuario al final. Si algo falla en el medio, la cuenta sigue
 * existiendo y la persona puede reintentar, y todos los pasos son idempotentes
 * (borrar lo ya borrado o renombrar lo ya renombrado no hace nada). Al reves
 * quedarian datos sin duenio y nadie con un token para terminar el trabajo.
 *
 * No hace falta invalidar tokens: el filtro busca al usuario en cada request,
 * y un token de un usuario que ya no existe se rechaza solo, en todos sus
 * telefonos.
 */
@Service
public class CuentaServicio {

    /** Lo que ve la otra persona en lugar del nombre. Mismo texto que la politica. */
    public static final String NOMBRE_ANONIMO = "Cuenta eliminada";

    private final UsuarioRepositorio usuarios;
    private final GrupoRepositorio grupos;
    private final GastoRepositorio gastos;
    private final PozoRepositorio pozos;
    private final CategoriaRepositorio categorias;
    private final PasswordEncoder codificador;
    private final LimitadorDeIntentos limitador;
    private final UsuarioActual usuarioActual;

    public CuentaServicio(UsuarioRepositorio usuarios,
                          GrupoRepositorio grupos,
                          GastoRepositorio gastos,
                          PozoRepositorio pozos,
                          CategoriaRepositorio categorias,
                          PasswordEncoder codificador,
                          LimitadorDeIntentos limitador,
                          UsuarioActual usuarioActual) {
        this.usuarios = usuarios;
        this.grupos = grupos;
        this.gastos = gastos;
        this.pozos = pozos;
        this.categorias = categorias;
        this.codificador = codificador;
        this.limitador = limitador;
        this.usuarioActual = usuarioActual;
    }

    public void borrar(BorrarCuentaRequest req) {
        Usuario actual = usuarioActual.requerido();

        // Con un token robado, esto seria un verificador de contrasenas sin
        // limite. Mismo tope que el login por cuenta.
        String clave = "borrar-cuenta:" + actual.getId();
        limitador.verificar(clave, LimitadorDeIntentos.MAX_POR_CUENTA);

        if (!codificador.matches(req.password(), actual.getPasswordHash())) {
            limitador.registrarFallo(clave);
            // 400 y NO 401, a proposito: la app cierra la sesion sola ante un
            // 401 con token (es como detecta un token vencido). Equivocarse la
            // contrasena al confirmar no puede sacarte de la app.
            throw new ReglaDeNegocioException("La contraseña no es correcta");
        }

        String grupoId = actual.getGrupoId();
        String yo = actual.getId();

        if (usuarios.countByGrupoId(grupoId) <= 1) {
            gastos.deleteByGrupoId(grupoId);
            pozos.deleteByGrupoId(grupoId);
            categorias.deleteByGrupoId(grupoId);
            grupos.deleteById(grupoId);
        } else {
            gastos.borrarPersonalesDe(grupoId, yo);
            gastos.anonimizarPagador(grupoId, yo, NOMBRE_ANONIMO);
            pozos.anonimizarAportante(grupoId, yo, NOMBRE_ANONIMO);
        }

        // Al final, siempre. Ver el javadoc de la clase.
        usuarios.delete(actual);
        limitador.limpiar(clave);
    }
}
