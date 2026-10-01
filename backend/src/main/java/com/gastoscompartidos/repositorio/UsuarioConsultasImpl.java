package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Ingreso;
import com.gastoscompartidos.modelo.Usuario;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;

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

    @Override
    public boolean editarIngreso(String usuarioId, String ingresoId, BigDecimal nuevoMonto) {
        // El filtro incluye "ingresos.id" ademas de "_id": si el id no
        // existe, la query entera no matchea ningun documento (en vez de
        // matchear el usuario y no mover nada), asi que modifiedCount queda
        // en 0 en los dos casos que nos interesa distinguir del lado del
        // servicio -- aca alcanza con el resultado unico.
        Query query = Query.query(
                Criteria.where("_id").is(usuarioId).and("ingresos.id").is(ingresoId));
        // "$" posicional a secas: a diferencia del arrayFilters que usa el
        // borrado de cuenta para los aportes, aca alcanza porque el id es
        // unico y el query de arriba ya aseguro cual es el PRIMER (y unico)
        // que matchea.
        Update update = new Update().set("ingresos.$.monto", nuevoMonto);
        return mongoTemplate.updateFirst(query, update, Usuario.class).getModifiedCount() > 0;
    }

    @Override
    public boolean borrarIngreso(String usuarioId, String ingresoId) {
        Query query = Query.query(Criteria.where("_id").is(usuarioId));
        Update update = new Update().pull("ingresos",
                Query.query(Criteria.where("id").is(ingresoId)).getQueryObject());
        return mongoTemplate.updateFirst(query, update, Usuario.class).getModifiedCount() > 0;
    }
}
