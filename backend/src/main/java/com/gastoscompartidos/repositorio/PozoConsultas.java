package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Aporte;

import java.math.BigDecimal;

/**
 * Las escrituras del pozo que NO pueden hacerse con save().
 *
 * Es el mismo mecanismo de fragmento que {@link GastoConsultas}: Spring Data
 * busca una clase con este nombre + "Impl".
 *
 * Por que no alcanza save(): agregar un aporte con
 * `pozo.getAportes().add(...)` seguido de `save()` es un read-modify-write. Si
 * Viole y Franco aportan al mismo tiempo, los dos leen la misma lista de dos
 * elementos y los dos escriben una lista de tres: **el segundo pisa al primero
 * y no hay ningun error**. Es el problema clasico de leer y escribir en dos
 * pasos.
 *
 * La solucion es un $push, que es una escritura de UN solo documento y por lo
 * tanto atomica en Mongo -- el mismo argumento por el que no hay @Transactional
 * en ningun servicio de esta app.
 *
 * En Postgres el aporte seria un INSERT en una tabla hija. Aca el array ES la
 * tabla hija, y $push es el insert.
 */
public interface PozoConsultas {

    /**
     * Agrega un aporte al pozo, en una sola operacion atomica que ademas
     * verifica que el pozo sea de ese grupo y este ABIERTO.
     *
     * Es el patron de UPDATE ... WHERE condicional: la autorizacion y el chequeo
     * de estado viajan DENTRO del filtro, asi que no hay ventana entre
     * "verifique que puedo" y "escribi". Si el filtro no matchea no se escribe
     * nada.
     *
     * @return true si se agrego; false si el pozo no existe, no es del grupo o
     *         ya esta cerrado. Quien llama no puede distinguir los tres casos,
     *         que es lo que queremos: un error distinto confirmaria que el pozo
     *         existe.
     */
    boolean agregarAporte(String pozoId, String grupoId, Aporte aporte);

    /**
     * Corrige el monto de un aporte ya cargado, en una operacion atomica que
     * ademas verifica que el aporte sea DE ESA PERSONA.
     *
     * A DIFERENCIA de agregarAporte, no exige que el pozo este ABIERTO: un
     * error encontrado al volver del viaje tiene que poder corregirse igual,
     * mismo criterio que ya vale para los gastos (seccion 6.9 de CLAUDE.md,
     * "una vaquita cerrada ya no congela sus gastos").
     *
     * La verificacion de que el aporte sea de quien pide el cambio viaja dentro
     * del filtro (aportes.usuario.usuarioId), no en un if del servicio -- mismo
     * criterio que la visibilidad de un gasto: las reglas de acceso van lo mas
     * abajo que se pueda. Nadie puede tocar un aporte ajeno, ni para corregirlo
     * ni para borrarlo: un aporte es la afirmacion de una persona sobre SU
     * plata, y alterarla por otra abriria la puerta a vaciar en silencio el
     * aporte de alguien.
     *
     * @return true si se corrigio; false si el pozo no existe, no es del grupo,
     *         el aporte no existe, o no es de esta persona. Los cuatro casos se
     *         ven iguales desde afuera, a proposito: distinguirlos confirmaria
     *         cosas que no son asunto de quien pregunta.
     */
    boolean editarAporte(String pozoId, String grupoId, String usuarioId, String aporteId, BigDecimal nuevoMonto);

    /**
     * Saca un aporte de la lista. Mismas reglas que editarAporte: no exige
     * pozo ABIERTO, y solo el dueño del aporte puede borrarlo.
     */
    boolean borrarAporte(String pozoId, String grupoId, String usuarioId, String aporteId);

    /**
     * Cierra el pozo, tambien condicionalmente.
     *
     * @return true si se cerro; false si no existe, no es del grupo o ya estaba
     *         cerrado. Cerrar dos veces no es un error silencioso.
     */
    boolean cerrar(String pozoId, String grupoId);

    /**
     * Borrado de cuenta: reemplaza el nombre de esa persona en los aportes que
     * hizo, en todos los pozos del grupo. Devuelve cuantos pozos toco.
     */
    long anonimizarAportante(String grupoId, String usuarioId, String nombre);
}
