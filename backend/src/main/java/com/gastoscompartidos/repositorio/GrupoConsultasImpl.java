package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Grupo;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Optional;

/**
 * Implementacion del fragmento {@link GrupoConsultas}.
 *
 * El nombre no es libre: Spring Data busca la interfaz + "Impl". Si se
 * renombra una sin la otra, la app no arranca.
 */
public class GrupoConsultasImpl implements GrupoConsultas {

    private final MongoTemplate mongoTemplate;

    public GrupoConsultasImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Grupo> consumirInvitacion(String codigo, Instant ahora) {
        Query query = Query.query(Criteria.where("invitacion_codigo").is(codigo)
                .and("invitacion_vence").gt(ahora));
        Update update = new Update().unset("invitacion_codigo").unset("invitacion_vence");

        // findAndModify, y no updateFirst como en PozoConsultasImpl: aca SI
        // hace falta el documento (el id del grupo al que sumar a quien se
        // une), no solo saber si matcheo. Por default devuelve el documento
        // COMO ESTABA ANTES del update, que es lo que queremos: si
        // devolviera el de despues, ya vendria sin el codigo.
        Grupo grupo = mongoTemplate.findAndModify(query, update, Grupo.class);
        return Optional.ofNullable(grupo);
    }
}
