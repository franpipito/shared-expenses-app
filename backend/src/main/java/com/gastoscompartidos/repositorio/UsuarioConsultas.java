package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Ingreso;

import java.math.BigDecimal;

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

    /**
     * Corrige el monto de un ingreso ya cargado (sección 2.3c), ubicándolo
     * por {@code id} con el operador posicional {@code $}. A diferencia de
     * {@code agregarIngreso}, acá SÍ hace falta saber si matcheó: si el id
     * no existe (ya se borró, o es de otro usuario) hay que devolver 404 en
     * vez de un "éxito" que no cambió nada.
     *
     * @return true si el ingreso existía y se actualizó.
     */
    boolean editarIngreso(String usuarioId, String ingresoId, BigDecimal nuevoMonto);

    /**
     * Saca un ingreso del historial con {@code $pull}, ubicándolo por
     * {@code id}. Mismo motivo que {@code editarIngreso} para devolver si
     * existía.
     *
     * @return true si el ingreso existía y se borró.
     */
    boolean borrarIngreso(String usuarioId, String ingresoId);
}
