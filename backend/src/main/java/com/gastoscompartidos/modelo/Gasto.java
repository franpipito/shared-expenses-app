package com.gastoscompartidos.modelo;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Un gasto cargado por un integrante del grupo.
 *
 * DECISION CENTRAL DEL MODELO: el reparto se guarda resuelto.
 * En lugar de guardar un porcentaje y dividir en cada lectura, guardamos
 * `montoPagador` = cuanto de este gasto le corresponde a quien lo pago.
 *
 * De ahi sale, sin ninguna division:
 *     deuda generada por este gasto = monto - montoPagador
 *     (la debe el OTRO integrante, a favor de pagadoPor)
 *
 * Un gasto PERSONAL tiene montoPagador == monto, asi que aporta 0 a la deuda.
 * El porcentaje 50/50 es un input de UI que se traduce a montoPagador al crear;
 * el centavo impar de un monto como 10.01 se decide una sola vez, al escribir,
 * y queda congelado en la fila para siempre.
 */
@Entity
@Table(
        name = "gasto",
        indexes = {
                // El indice que sostiene tanto el listado del mes como el calculo del saldo.
                @Index(name = "idx_gasto_grupo_fecha", columnList = "grupo_id, fecha"),
                @Index(name = "idx_gasto_pagado_por", columnList = "pagado_por_id")
        }
)
// Invariantes a nivel base de datos: ultima linea de defensa si un bug de
// la capa de servicio deja pasar un valor invalido.
@Check(name = "ck_gasto_monto_positivo", constraints = "monto > 0")
@Check(name = "ck_gasto_reparto_valido", constraints = "monto_pagador >= 0 AND monto_pagador <= monto")
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_gasto_grupo"))
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagado_por_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_gasto_pagado_por"))
    private Usuario pagadoPor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_gasto_categoria"))
    private Categoria categoria;

    /** Monto total del gasto. precision=12, scale=2 -> NUMERIC(12,2) en Postgres. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** Parte del monto que le corresponde a quien pago. Ver javadoc de la clase. */
    @Column(name = "monto_pagador", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoPagador;

    /**
     * EnumType.STRING guarda el texto "PERSONAL"/"COMPARTIDO".
     * El default de JPA es ORDINAL (guarda 0/1), que se rompe silenciosamente
     * si alguien reordena las constantes del enum. STRING siempre.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoGasto tipo;

    @Column(nullable = false)
    private LocalDate fecha;

    /**
     * Obligatoria por pedido de la usuaria: es "algo que me recuerde el momento".
     * No es decorativa. Es lo que le permite distinguir despues un gasto evitable
     * de uno que no lo era: "uber cumple guada" contra "uber a las 15hs".
     */
    @Column(nullable = false, length = 255)
    private String descripcion;

    /**
     * Marca de "gasto hormiga": el gasto que, mirado en frio, podria no haberse
     * hecho. Es un juicio que solo puede emitir quien lo carga, y en el momento
     * de cargarlo.
     *
     * No es derivable de nada mas: el mismo Uber por el mismo monto es necesario
     * si fue por seguridad y hormiga si fue por comodidad. Por eso no puede vivir
     * en la categoria ni deducirse del monto.
     *
     * El total mensual de estos gastos es el numero principal de la app.
     */
    @Column(name = "es_hormiga", nullable = false)
    private boolean esHormiga;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /**
     * Bloqueo optimista. Hibernate agrega "AND version = ?" a cada UPDATE e
     * incrementa el numero. Si las dos personas editan el mismo gasto a la vez,
     * la segunda escritura afecta 0 filas y Hibernate lanza excepcion en vez de
     * pisar el cambio de la otra en silencio.
     * Es el mismo patron de UPDATE ... WHERE condicional, pero automatico.
     */
    @Version
    private Long version;

    protected Gasto() {
    }

    public Gasto(Grupo grupo, Usuario pagadoPor, Categoria categoria,
                 BigDecimal monto, BigDecimal montoPagador,
                 TipoGasto tipo, LocalDate fecha, String descripcion,
                 boolean esHormiga) {
        this.grupo = grupo;
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

    /** Se ejecuta justo antes del INSERT. */
    @PrePersist
    void alCrear() {
        this.creadoEn = Instant.now();
        this.actualizadoEn = this.creadoEn;
    }

    /** Se ejecuta justo antes de cada UPDATE. */
    @PreUpdate
    void alActualizar() {
        this.actualizadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Grupo getGrupo() {
        return grupo;
    }

    public void setGrupo(Grupo grupo) {
        this.grupo = grupo;
    }

    public Usuario getPagadoPor() {
        return pagadoPor;
    }

    public void setPagadoPor(Usuario pagadoPor) {
        this.pagadoPor = pagadoPor;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
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
     * Nota: la convencion de Java para booleanos seria `isEsHormiga()`, que en
     * castellano queda ilegible. Usamos `esHormiga()`, que se lee bien.
     * Es seguro porque Hibernate accede por campo (el @Id esta sobre el campo),
     * no por getter. Ojo en la sesion 2: Jackson SI usa la convencion de bean,
     * asi que no detecta este getter solo -- pero como vamos a serializar DTOs
     * y no la entidad, no nos afecta.
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
