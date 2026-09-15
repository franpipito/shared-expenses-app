package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Aporte;

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
     * Cierra el pozo, tambien condicionalmente.
     *
     * @return true si se cerro; false si no existe, no es del grupo o ya estaba
     *         cerrado. Cerrar dos veces no es un error silencioso.
     */
    boolean cerrar(String pozoId, String grupoId);
}
