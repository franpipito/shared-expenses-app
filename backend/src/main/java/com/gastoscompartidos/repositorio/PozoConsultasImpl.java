package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Aporte;
import com.gastoscompartidos.modelo.EstadoPozo;
import com.gastoscompartidos.modelo.Pozo;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;

/**
 * Implementacion del fragmento {@link PozoConsultas}.
 *
 * El nombre no es libre: Spring Data busca la interfaz + "Impl". Si se renombra
 * una sin la otra, la app no arranca.
 */
public class PozoConsultasImpl implements PozoConsultas {

    private final MongoTemplate mongoTemplate;

    public PozoConsultasImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * "Este pozo, de este grupo, y abierto". Las tres condiciones en el filtro.
     *
     * Que el grupo viaje en el filtro y no en un if del servicio es la misma
     * decision que `visiblesPara()` en GastoConsultasImpl: las reglas de acceso
     * van lo mas abajo que se pueda, para que una consulta nueva que se olvide
     * de repetirlas no exista.
     */
    private static Query abiertoDelGrupo(String pozoId, String grupoId) {
        return Query.query(Criteria.where("_id").is(pozoId)
                .and("grupo_id").is(grupoId)
                .and("estado").is(EstadoPozo.ABIERTO));
    }

    @Override
    public boolean agregarAporte(String pozoId, String grupoId, Aporte aporte) {
        Update update = new Update()
                .push("aportes", aporte)
                // El .inc del @Version NO es opcional, y es la parte facil de
                // olvidar. updateFirst no toca la version: sin esto, alguien que
                // leyo el pozo antes de este aporte podria llamar a save() con
                // la version vieja, matchear igual, y borrar el aporte que
                // acabamos de escribir sin ningun error. Ver el javadoc de Pozo.
                .inc("version", 1);

        UpdateResult resultado = mongoTemplate.updateFirst(
                abiertoDelGrupo(pozoId, grupoId), update, Pozo.class);

        // getMatchedCount y no getModifiedCount: un $push siempre modifica, pero
        // lo que queremos saber es si el FILTRO encontro el pozo.
        return resultado.getMatchedCount() > 0;
    }

    @Override
    public boolean editarAporte(String pozoId, String grupoId, String usuarioId, String aporteId, BigDecimal nuevoMonto) {
        // SIN exigir estado ABIERTO, a diferencia de agregarAporte: editar un
        // aporte tiene que seguir andando despues de cerrar la vaquita, mismo
        // criterio que ya vale para corregir un gasto del viaje (seccion 6.9).
        //
        // OJO CON ESTO, que es sutil y vale la pena recordar: "aportes.id" y
        // "aportes.usuario.usuarioId" como DOS condiciones sueltas (unidas
        // con .and() pero sin $elemMatch) NO exigen que las cumpla el MISMO
        // elemento del array -- Mongo las evalua cada una por separado contra
        // CUALQUIER elemento. Si Viole tiene su propio aporte en este mismo
        // pozo, pedir editar el aporte de Franco pasando el id de Franco pero
        // SU PROPIO usuarioId podria matchear el documento igual (el id lo
        // satisface el aporte de Franco, el usuarioId lo satisface el de
        // Viole) y el "$" terminar apuntando a un elemento ambiguo. Con
        // $elemMatch las dos condiciones se exigen del MISMO elemento, que es
        // lo que hace falta para que el "$" de mas abajo sea inequivoco.
        Query query = Query.query(Criteria.where("_id").is(pozoId)
                .and("grupo_id").is(grupoId)
                .and("aportes").elemMatch(Criteria.where("id").is(aporteId).and("usuario.usuarioId").is(usuarioId)));
        // "$" posicional a secas: alcanza porque el id ya es unico, mismo
        // razonamiento que editarIngreso (a diferencia del arrayFilters que
        // hace falta cuando una misma persona puede matchear mas de un
        // elemento, como en anonimizarAportante de aca abajo).
        Update update = new Update()
                .set("aportes.$.monto", nuevoMonto)
                .inc("version", 1);
        return mongoTemplate.updateFirst(query, update, Pozo.class).getModifiedCount() > 0;
    }

    @Override
    public boolean borrarAporte(String pozoId, String grupoId, String usuarioId, String aporteId) {
        // OJO ACA, que costo encontrarlo probando contra Mongo real y no con
        // mocks: el $inc de version NO es condicional al $pull. Si el chequeo
        // de dueño viviera solo adentro del $pull (su propio filtro sobre el
        // array), un intento de borrar el aporte AJENO matchearia igual el
        // DOCUMENTO por _id+grupo_id, el $pull no sacaria nada, pero el $inc
        // se aplicaria lo mismo -- modifiedCount > 0 por el solo hecho de
        // incrementar version, y el servicio leeria eso como "se borro" y
        // devolveria 200 sin haber borrado nada. Confirmado con dos usuarios
        // reales: B borrando el aporte de A daba 200 en vez de 404.
        //
        // La solucion es que el filtro de ARRIBA tambien exija que exista un
        // elemento que matchee -- $elemMatch -- para que ni el $pull ni el
        // $inc se ejecuten si el aporte no es de esta persona. Mismo efecto
        // que editarAporte ya tiene gratis porque su filtro de arriba usa
        // "aportes.id"/"aportes.usuario.usuarioId" directo.
        Query query = Query.query(Criteria.where("_id").is(pozoId)
                .and("grupo_id").is(grupoId)
                .and("aportes").elemMatch(Criteria.where("id").is(aporteId).and("usuario.usuarioId").is(usuarioId)));
        Update update = new Update()
                .pull("aportes", Query.query(
                        Criteria.where("id").is(aporteId).and("usuario.usuarioId").is(usuarioId)).getQueryObject())
                .inc("version", 1);
        return mongoTemplate.updateFirst(query, update, Pozo.class).getModifiedCount() > 0;
    }

    @Override
    public boolean cerrar(String pozoId, String grupoId) {
        Update update = new Update()
                .set("estado", EstadoPozo.CERRADO)
                .inc("version", 1);

        UpdateResult resultado = mongoTemplate.updateFirst(
                abiertoDelGrupo(pozoId, grupoId), update, Pozo.class);

        return resultado.getMatchedCount() > 0;
    }

    /**
     * Cambia el nombre en los aportes de una persona, adentro del array.
     *
     * Es la primera vez que la app edita elementos de un array en el lugar, y
     * la herramienta es `arrayFilters`: `aportes.$[a]` significa "cada
     * elemento del array que cumpla el filtro llamado a", y el filtro se
     * declara aparte con `filterArray`. El `$` a secas solo tocaria el PRIMER
     * aporte que matchea, y una persona suele aportar mas de una vez.
     *
     * Todo en una sola escritura por pozo, atomica como el `$push` del aporte.
     */
    @Override
    public long anonimizarAportante(String grupoId, String usuarioId, String nombre) {
        Query query = Query.query(Criteria.where("grupo_id").is(grupoId)
                .and("aportes.usuario.usuarioId").is(usuarioId));
        Update update = new Update()
                .set("aportes.$[a].usuario.nombre", nombre)
                .filterArray(Criteria.where("a.usuario.usuarioId").is(usuarioId))
                .inc("version", 1);
        return mongoTemplate.updateMulti(query, update, Pozo.class).getModifiedCount();
    }

}
