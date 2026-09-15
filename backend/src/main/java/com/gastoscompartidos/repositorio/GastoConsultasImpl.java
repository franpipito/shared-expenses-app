package com.gastoscompartidos.repositorio;

import com.gastoscompartidos.modelo.Gasto;
import com.gastoscompartidos.modelo.TipoGasto;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * La implementacion del fragmento {@link GastoConsultas}.
 *
 * El nombre no es libre: Spring Data busca una clase llamada igual que la
 * interfaz mas "Impl". Si se renombra una sin la otra, la app falla al arrancar
 * diciendo que no puede crear el bean del repositorio.
 *
 * Se usa `MongoTemplate` y no `@Query` con JSON porque estas consultas se arman
 * en tiempo de ejecucion: los filtros de categoria y pagador son opcionales, y
 * un string de JSON no tiene forma de decir "si este parametro viene null, no
 * filtres".
 */
public class GastoConsultasImpl implements GastoConsultas {

    private final MongoTemplate mongoTemplate;

    /**
     * Inyeccion por constructor, sin @Autowired: con un solo constructor Spring
     * lo usa igual. Un campo final es una dependencia que no puede quedar sin
     * setear ni cambiar despues.
     */
    public GastoConsultasImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * LA REGLA DE VISIBILIDAD, en un solo lugar.
     *
     * "Los gastos de este grupo que este usuario puede ver": los COMPARTIDO los
     * ve cualquiera del grupo, y los PERSONAL solo quien los pago.
     *
     * Devolver un Criteria en vez de un string es lo que permite componerla con
     * el resto de los filtros de cada consulta sin repetirla.
     */
    private static Criteria visiblesPara(String grupoId, String usuarioId) {
        return Criteria.where("grupo_id").is(grupoId)
                .orOperator(
                        Criteria.where("tipo").is(TipoGasto.COMPARTIDO),
                        Criteria.where("pagadoPor.usuarioId").is(usuarioId)
                );
    }

    /**
     * El rango de fechas, semiabierto: [desde, hasta).
     *
     * Es semiabierto a proposito, igual que en Postgres: evita tener que saber
     * si el mes termina el 28, 30 o 31. Y funciona sobre un campo de texto
     * porque las fechas ISO ordenan como texto igual que como fecha —- ver
     * ConfiguracionMongo.
     */
    private static Criteria enElPeriodo(LocalDate desde, LocalDate hasta) {
        return Criteria.where("fecha").gte(desde).lt(hasta);
    }

    /**
     * "Los gastos de la vida normal", o sea los que no salieron de un pozo.
     *
     * Es el filtro de los TRES agregados mensuales. Un viaje se paga con plata
     * que se ahorro a proposito, asi que no es deuda entre ellos ni es gasto
     * hormiga, y comparar un mes con viaje contra uno sin viaje no es una
     * tendencia sino ruido. Ver docs/vaquita.md.
     *
     * OJO CON ESTE `is(null)`, que en SQL seria un bug: en Mongo `{campo: null}`
     * matchea tanto los documentos con el campo en null como **los que no tienen
     * el campo**. Es justo lo que hace falta, porque Spring Data no escribe los
     * null y los gastos anteriores a la vaquita directamente no tienen la clave.
     * En SQL, `= NULL` no matchea nunca y habria que escribir `IS NULL`.
     */
    private static Criteria sinPozo() {
        return Criteria.where("pozo_id").is(null);
    }

    @Override
    public List<Gasto> buscarVisibles(String grupoId, String usuarioId,
                                      LocalDate desde, LocalDate hasta,
                                      String categoriaId, String pagadoPorId) {
        Query query = new Query(new Criteria().andOperator(
                visiblesPara(grupoId, usuarioId),
                enElPeriodo(desde, hasta),
                sinPozo()
        ));

        // Los filtros opcionales se agregan solo si vinieron. En JPQL esto era
        // el patron ":param is null or campo = :param", que obligaba a que la
        // consulta contemplara los dos casos siempre. Aca la consulta que llega
        // a Mongo tiene exactamente las condiciones que hacen falta.
        if (categoriaId != null) {
            query.addCriteria(Criteria.where("categoria.categoriaId").is(categoriaId));
        }
        if (pagadoPorId != null) {
            query.addCriteria(Criteria.where("pagadoPor.usuarioId").is(pagadoPorId));
        }

        // Por fecha descendente, y desempatando por _id descendente. Como los
        // primeros bytes del ObjectId son el timestamp de creacion, eso deja
        // arriba el ultimo gasto cargado del dia, que es lo que uno espera al
        // volver a la lista despues de cargar algo.
        query.with(Sort.by(Sort.Direction.DESC, "fecha", "_id"));

        // NO hace falta ningun $lookup ni nada parecido al `join fetch` de JPQL:
        // la categoria y el pagador ya viajan embebidos en cada documento. Esta
        // consulta es una sola lectura y no tiene problema N+1 posible.
        return mongoTemplate.find(query, Gasto.class);
    }

    @Override
    public Optional<Gasto> buscarVisiblePorId(String id, String grupoId, String usuarioId) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(id),
                visiblesPara(grupoId, usuarioId)
        ));
        return Optional.ofNullable(mongoTemplate.findOne(query, Gasto.class));
    }

    @Override
    public BigDecimal sumarHormigaDe(String grupoId, String usuarioId,
                                     LocalDate desde, LocalDate hasta) {
        Criteria filtro = new Criteria().andOperator(
                visiblesPara(grupoId, usuarioId),
                enElPeriodo(desde, hasta),
                sinPozo(),
                Criteria.where("es_hormiga").is(true)
        );

        // "Su parte": si pago el, montoPagador; si pago el otro, lo que le debe.
        // Es la misma cuenta que Gasto.parteDe(), pero expresada como pipeline
        // para no traer los documentos solo para sumarlos.
        return sumar(filtro, condicional(usuarioId, "$monto_pagador",
                new Document("$subtract", List.of("$monto", "$monto_pagador"))));
    }

    @Override
    public long contarEn(String grupoId, String usuarioId, LocalDate desde, LocalDate hasta) {
        // sinPozo() tambien aca, y no es un detalle: este conteo decide si la
        // nutria tiene datos del mes anterior para comparar. Si contara los
        // gastos del viaje, un mes que solo tuvo viaje daria "hay datos" con
        // cero hormiga, y la nutria saldria CONTENTA por un mes que en realidad
        // no midio nada.
        Query query = new Query(new Criteria().andOperator(
                visiblesPara(grupoId, usuarioId),
                enElPeriodo(desde, hasta),
                sinPozo()
        ));
        return mongoTemplate.count(query, Gasto.class);
    }

    @Override
    public BigDecimal saldoDe(String grupoId, String usuarioId,
                              LocalDate desde, LocalDate hasta) {
        // Solo los COMPARTIDO: un gasto personal tiene montoPagador == monto,
        // asi que aportaria cero igual, pero filtrarlo deja la intencion
        // explicita. Y ojo: aca NO va la regla de visibilidad, porque los
        // COMPARTIDO los ven los dos por definicion.
        Criteria filtro = new Criteria().andOperator(
                Criteria.where("grupo_id").is(grupoId),
                Criteria.where("tipo").is(TipoGasto.COMPARTIDO),
                enElPeriodo(desde, hasta),
                sinPozo()
        );

        // Positivo = a este usuario le deben. Negativo = debe el.
        return sumar(filtro, condicional(usuarioId,
                new Document("$subtract", List.of("$monto", "$monto_pagador")),
                new Document("$subtract", List.of("$monto_pagador", "$monto"))));
    }

    @Override
    public List<Gasto> buscarDelPozo(String pozoId, String grupoId) {
        // Sin rango de fechas, a proposito: el pozo ES el recorte. Es el primer
        // listado de la app que no se corta por mes, y esta bien -- un viaje
        // trae su propio corte natural, y el de Bariloche cruza de septiembre a
        // octubre. El grupoId va igual, para que el id de un pozo ajeno no
        // devuelva nada.
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("pozo_id").is(pozoId),
                Criteria.where("grupo_id").is(grupoId)
        ));
        query.with(Sort.by(Sort.Direction.DESC, "fecha", "_id"));
        return mongoTemplate.find(query, Gasto.class);
    }

    @Override
    public BigDecimal sumarDelPozo(String pozoId) {
        // Suma el monto ENTERO y no la parte de nadie: el gasto salio del pozo,
        // no del bolsillo de uno de los dos. Por eso no hay $cond aca, a
        // diferencia de saldoDe y sumarHormigaDe. Es el lado de los debitos de
        // `restante = aportes - gastos`.
        return sumar(Criteria.where("pozo_id").is(pozoId), "$monto");
    }

    /**
     * Arma un `$cond`: "si el pagador de este gasto es este usuario, entonces
     * `siPago`, si no `siPagoElOtro`".
     *
     * Se construye con objetos `Document` y no concatenando texto JSON. No es
     * estilo: el usuarioId entra como un VALOR de BSON, asi que no hay forma de
     * que un id raro cambie la estructura de la consulta. Concatenando strings,
     * si.
     */
    private static Document condicional(String usuarioId, Object siPago, Object siPagoElOtro) {
        return new Document("$cond", List.of(
                new Document("$eq", List.of("$pagadoPor.usuarioId", usuarioId)),
                siPago,
                siPagoElOtro
        ));
    }

    /**
     * El pipeline que reemplaza al `SUM(CASE WHEN ...)` de SQL: `$match` para
     * filtrar y `$group` para sumar.
     *
     * Es la traduccion mas directa que hay entre los dos mundos: `$match` es el
     * WHERE, `$group` es el GROUP BY. La diferencia conceptual es que en SQL se
     * declara el resultado y el motor arma el plan, mientras que un pipeline de
     * Mongo es una lista ordenada de etapas donde cada una recibe lo que dejo la
     * anterior. Por eso el orden importa: `$match` va SIEMPRE primero, para que
     * `$group` trabaje sobre menos documentos y para que el filtro pueda usar el
     * indice.
     *
     * `_id: null` significa "una sola fila con el total de todo", que es el
     * equivalente de un SUM sin GROUP BY.
     */
    /**
     * @param expresion la expresion que se suma. Puede ser un Document (un
     *                  `$cond`, por ejemplo) o simplemente la ruta de un campo
     *                  como "$monto": las dos son expresiones validas de
     *                  agregacion, y por eso el parametro es Object y no Document.
     */
    private BigDecimal sumar(Criteria filtro, Object expresion) {
        // OJO CON ESTAS DOS LINEAS: aca estuvo el bug que hizo fallar el saldo.
        //
        // La primera version armaba el $match a mano con
        // `filtro.getCriteriaObject()`. Eso devuelve el Criteria CRUDO, sin
        // pasar por el conversor de Mongo -- y el conversor es justamente el que
        // traduce los VALORES. Resultado: el LocalDate viajaba como fecha de
        // BSON (el codec por defecto del driver) y se comparaba contra un campo
        // que se guarda como texto ISO. No matcheaba nunca, el pipeline no
        // devolvia filas, y el saldo daba cero.
        //
        // Y no daba ningun error, que es lo peor: la consulta era valida, solo
        // que preguntaba por algo que no existe.
        //
        // Aggregation.match() dentro de una agregacion TIPADA (el Gasto.class de
        // abajo) si pasa por el mapeo, igual que hace mongoTemplate.find(). Por
        // eso buscarVisibles funcionaba y esto no.
        //
        // La leccion general: en Spring Data MongoDB, armar una etapa a mano te
        // saca del mapeo. Si la etapa toca valores, va con la API tipada.
        AggregationOperation match = Aggregation.match(filtro);
        AggregationOperation group = ctx -> new Document("$group",
                new Document("_id", null).append("total", new Document("$sum", expresion)));

        AggregationResults<Document> resultado = mongoTemplate.aggregate(
                Aggregation.newAggregation(Gasto.class, match, group), Gasto.class, Document.class);

        Document fila = resultado.getUniqueMappedResult();

        // Sin documentos que matcheen, el pipeline no devuelve ninguna fila -- ni
        // siquiera una con cero. Es el equivalente del `coalesce(sum(...), 0)`
        // que haciamos en SQL, y hay que acordarse igual.
        if (fila == null) return BigDecimal.ZERO;

        Object total = fila.get("total");
        if (total == null) return BigDecimal.ZERO;

        // Decimal128 porque los montos se guardan asi. Si algun documento viejo
        // quedo con otro tipo numerico, esto lo delata en vez de redondear en
        // silencio.
        if (total instanceof Decimal128 decimal) return decimal.bigDecimalValue();
        if (total instanceof Number numero) return new BigDecimal(numero.toString());
        throw new IllegalStateException("El total no es un numero: " + total.getClass());
    }
}
