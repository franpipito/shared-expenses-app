package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Ingreso;
import com.gastoscompartidos.modelo.Usuario;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Implementacion del fragmento {@link UsuarioConsultas}.
 *
 * El nombre no es libre: Spring Data busca la interfaz + "Impl". Si se
 * renombra una sin la otra, la app no arranca.
 */
public class UsuarioConsultasImpl implements UsuarioConsultas {

    private final MongoTemplate mongoTemplate;

    public UsuarioConsultasImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void agregarIngreso(String usuarioId, Ingreso ingreso) {
        // A diferencia de Pozo, Usuario no tiene @Version: no hace falta el
        // .inc manual que agregarAporte necesita en PozoConsultasImpl. Y no
        // hay un "el pozo podria estar cerrado" que verificar -- el usuario
        // siempre existe, porque usuarioId sale de su propio token.
        Query query = Query.query(Criteria.where("_id").is(usuarioId));
        Update update = new Update().push("ingresos", ingreso);
        mongoTemplate.updateFirst(query, update, Usuario.class);
    }
}
