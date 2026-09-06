package com.gastoscompartidos.seguridad;

import com.gastoscompartidos.modelo.Usuario;

/**
 * Quien esta haciendo esta request.
 *
 * ESTA INTERFAZ ES LA COSTURA. Los servicios dependen de ella y no de como se
 * averigua la identidad. Hoy la implementa {@link UsuarioActualPorHeader}, que
 * lee un header; en la sesion 4 la va a implementar algo que lee un JWT, y
 * ningun servicio se entera del cambio.
 *
 * Es inversion de dependencias: la capa de negocio define lo que necesita
 * ("dame el usuario") y la capa de infraestructura decide como se cumple.
 */
public interface UsuarioActual {

    /**
     * @return el usuario de esta request.
     * @throws NoAutenticadoException si no se puede determinar.
     */
    Usuario requerido();
}
