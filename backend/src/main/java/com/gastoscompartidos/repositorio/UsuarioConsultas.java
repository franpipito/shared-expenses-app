package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Ingreso;

/**
 * Las consultas de usuario que no se pueden expresar con el nombre de un
 * metodo. Mismo patron de fragmento que {@link GastoConsultas}/{@link
 * GrupoConsultas}: Spring Data suma este contrato gratis a {@code
 * UsuarioRepositorio} buscando una clase {@code UsuarioConsultasImpl}.
 */
public interface UsuarioConsultas {

    /**
     * Agrega un ingreso a "Mi Plata" (sección 2.3b) con {@code $push}
     * atómico -- nunca {@code save()}, que pisaría cualquier ingreso que se
     * haya agregado entre que se leyó el usuario y que se escribe este.
     */
    void agregarIngreso(String usuarioId, Ingreso ingreso);
}
