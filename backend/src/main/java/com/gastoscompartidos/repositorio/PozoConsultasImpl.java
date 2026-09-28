package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Aporte;
import com.gastoscompartidos.modelo.EstadoPozo;
import com.gastoscompartidos.modelo.Pozo;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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
