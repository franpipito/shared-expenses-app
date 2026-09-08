package com.gastoscompartidos.modelo;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Un gasto cargado por un integrante del grupo.
 *
 * DECISION CENTRAL DEL MODELO, y no cambio al pasar a Mongo: el reparto se
 * guarda resuelto. En lugar de guardar un porcentaje y dividir en cada lectura,
 * guardamos `montoPagador` = cuanto de este gasto le corresponde a quien lo pago.
 *
 * De ahi sale, sin ninguna division:
 *     deuda generada por este gasto = monto - montoPagador
 *     (la debe el OTRO integrante, a favor de pagadoPor)
 *
 * Un gasto PERSONAL tiene montoPagador == monto, asi que aporta 0 a la deuda.
 * El centavo impar de un monto como 10.01 se decide una sola vez, al escribir.
 *
 * Esa decision se volvio MAS valiosa en Mongo: como no hay aritmetica por
 * documento, el resumen y el saldo son un `$group` con `$sum` y nada mas. Con un
 * porcentaje guardado, cada agregacion tendria que multiplicar y redondear
 * adentro del pipeline, que es donde el redondeo se vuelve dificil de auditar.
 *
 * QUE CAMBIO RESPECTO DE POSTGRES:
 *
 *  - `pagadoPor` y `categoria` pasaron de foreign key a documento embebido.
 *    Ver {@link ReferenciaUsuario}.
 *  - Los @Check de la base ya no existen. Postgres verificaba `monto > 0` y
 *    `monto_pagador BETWEEN 0 AND monto` como ultima linea de defensa contra un
 *    bug de la capa de servicio. **Mongo no tiene un equivalente que se declare
 *    desde la entidad**: existen los JSON Schema validators, pero se configuran
 *    sobre la coleccion, no desde Spring Data. Hoy esas invariantes viven solo
 *    en GastoServicio, o sea que hay una red menos.
 */
@Document(collection = "gasto")
// El indice que sostiene tanto el listado del mes como el calculo del saldo.
// Es el mismo idx_gasto_grupo_fecha que teniamos en Postgres, y por el mismo
// motivo: toda consulta de la app filtra por grupo y por rango de fecha.
@CompoundIndex(name = "idx_gasto_grupo_fecha", def = "{'grupo_id': 1, 'fecha': -1}")
@CompoundIndex(name = "idx_gasto_pagado_por", def = "{'pagadoPor.usuarioId': 1}")
public class Gasto {

    @Id
    private String id;

    @Field("grupo_id")
    private String grupoId;

    /** Snapshot embebido: {usuarioId, nombre}. Sin email ni hash de contrasena. */
    private ReferenciaUsuario pagadoPor;

    /** Snapshot embebido: {categoriaId, nombre, icono}. */
    private ReferenciaCategoria categoria;

    /**
     * Monto total del gasto.
     *
     * Se guarda como Decimal128 por spring.data.mongodb.representation.big-decimal.
     * Es el analogo exacto de NUMERIC(12,2): decimal de verdad, no punto
     * flotante. Sin esa conversion, `$sum` sumaria doubles y el error se
     * acumularia gasto por gasto.
     */
    private BigDecimal monto;

    /** Parte del monto que le corresponde a quien pago. Ver javadoc de la clase. */
    @Field("monto_pagador")
    private BigDecimal montoPagador;

    /**
     * En Mongo el enum se guarda como su nombre ("PERSONAL"/"COMPARTIDO") por
     * defecto, que es lo que queriamos.
     *
     * Es un default mejor que el de JPA: alla el default era ORDINAL (guardaba
     * 0/1) y habia que pedir @Enumerated(EnumType.STRING) explicitamente para
     * que reordenar las constantes del enum no corrompiera los datos viejos en
     * silencio.
     */
    private TipoGasto tipo;

    /**
     * Se guarda como texto ISO ("2026-09-07"), no como fecha de Mongo.
     * El por que esta en ConfiguracionMongo: Mongo no tiene "fecha sin hora", y
     * su tipo Date reintroduce el problema de zona horaria que motivo el `Clock`.
     */
    private LocalDate fecha;

    /**
     * Obligatoria por pedido de la usuaria: es "algo que me recuerde el momento".
     * No es decorativa. Es lo que le permite distinguir despues un gasto evitable
     * de uno que no lo era: "uber cumple guada" contra "uber a las 15hs".
     */
    private String descripcion;

    /**
     * Marca de "gasto hormiga": el gasto que, mirado en frio, podria no haberse
     * hecho. Es un juicio que solo puede emitir quien lo carga, y en el momento
     * de cargarlo.
     *
     * No es derivable de nada mas: el mismo Uber por el mismo monto es necesario
     * si fue por seguridad y hormiga si fue por comodidad.
     *
     * El total mensual de estos gastos es el numero principal de la app.
     */
    @Field("es_hormiga")
    private boolean esHormiga;

    /**
     * @CreatedDate y @LastModifiedDate reemplazan a @PrePersist y @PreUpdate de
     * JPA. Necesitan que @EnableMongoAuditing este activo (esta en
     * BackendApplication); sin eso quedan en null sin avisar.
     */
    @CreatedDate
    @Field("creado_en")
    private Instant creadoEn;

    @LastModifiedDate
    @Field("actualizado_en")
    private Instant actualizadoEn;

    /**
     * Bloqueo optimista, y sigue funcionando igual que con Hibernate: Spring
     * Data MongoDB agrega la version al filtro del update e incrementa el
     * numero. Si las dos personas editan el mismo gasto a la vez, la segunda
     * escritura no matchea ningun documento y se lanza
     * OptimisticLockingFailureException en vez de pisar el cambio de la otra.
     *
     * Es el mismo patron de UPDATE ... WHERE condicional, y en Mongo es la
     * unica forma razonable de conseguirlo: las transacciones multi-documento
     * existen, pero requieren replica set y son mucho mas caras que esto.
     *
     * Ojo con el tipo: @Version de Spring Data (org.springframework.data), no
     * el de JPA (jakarta.persistence).
     */
    @Version
    private Long version;

    protected Gasto() {
    }

    public Gasto(String grupoId, ReferenciaUsuario pagadoPor, ReferenciaCategoria categoria,
                 BigDecimal monto, BigDecimal montoPagador,
                 TipoGasto tipo, LocalDate fecha, String descripcion,
                 boolean esHormiga) {
        this.grupoId = grupoId;
        this.pagadoPor = pagadoPor;
        this.categoria = categoria;
        this.monto = monto;
        this.montoPagador = montoPagador;
        this.tipo = tipo;
        this.fecha = fecha;
        this.descripcion = descripcion;
        this.esHormiga = esHormiga;
    }

    /** Lo que el otro integrante le debe a `pagadoPor` por este gasto. */
    public BigDecimal deudaGenerada() {
        return monto.subtract(montoPagador);
    }

    /**
     * Cuanto de este gasto le corresponde a un integrante: lo que consumio, no
     * lo que puso de su bolsillo.
     *
     * Es la base del resumen personal. Si pagaste vos, tu parte es montoPagador;
     * si pago el otro, tu parte es lo que le debes.
     *
     * Propiedad util que cae sola: en un gasto PERSONAL del OTRO, tu parte da
     * cero (monto - monto = 0). O sea que los gastos privados ajenos no ensucian
     * tus totales aunque la consulta los trajera.
     */
    public BigDecimal parteDe(String usuarioId) {
        return pagadoPor.usuarioId().equals(usuarioId) ? montoPagador : deudaGenerada();
    }

    public String getId() {
        return id;
    }

    public String getGrupoId() {
        return grupoId;
    }

    public void setGrupoId(String grupoId) {
        this.grupoId = grupoId;
    }

    public ReferenciaUsuario getPagadoPor() {
        return pagadoPor;
    }

    public void setPagadoPor(ReferenciaUsuario pagadoPor) {
        this.pagadoPor = pagadoPor;
    }

    public ReferenciaCategoria getCategoria() {
        return categoria;
    }

    public void setCategoria(ReferenciaCategoria categoria) {
        this.categoria = categoria;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public BigDecimal getMontoPagador() {
        return montoPagador;
    }

    public void setMontoPagador(BigDecimal montoPagador) {
        this.montoPagador = montoPagador;
    }

    public TipoGasto getTipo() {
        return tipo;
    }

    public void setTipo(TipoGasto tipo) {
        this.tipo = tipo;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    /**
     * La convencion de Java seria `isEsHormiga()`, que en castellano queda
     * ilegible. `esHormiga()` se lee bien, y es seguro porque Spring Data mapea
     * por campo y no por getter — igual que hacia Hibernate.
     */
    public boolean esHormiga() {
        return esHormiga;
    }

    public void setEsHormiga(boolean esHormiga) {
        this.esHormiga = esHormiga;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public Long getVersion() {
        return version;
    }
}
