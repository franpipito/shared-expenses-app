package com.gastoscompartidos.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * "Ya te pagué $X": un asiento que reduce la deuda acumulada entre los dos.
 *
 * Es la entidad que el javadoc de {@link com.gastoscompartidos.dto.SaldoRespuesta}
 * viene anotando como pendiente desde la sesion 3, y que {@link Aporte} ya
 * describia como "la version barata, acotada a un viaje". Esta es la version
 * general: no vive embebida en nada, porque a diferencia de los aportes de un
 * pozo (acotados a un viaje) las liquidaciones se acumulan mientras dure la
 * pareja, sin un limite natural -- el mismo motivo por el que Gasto es su
 * propia coleccion y no vive embebido en Grupo.
 *
 * `de` y `para` son snapshots ({@link ReferenciaUsuario}), igual que
 * `Gasto.pagadoPor`: si mas adelante alguien sale del grupo (seccion 2.1) o
 * borra la cuenta, esta liquidacion sigue leyendose con el nombre de quien
 * pago y quien recibio en ese momento.
 *
 * Inmutable por construccion, como {@link Aporte}: no se edita. Si se
 * registro mal, se corrige con otra liquidacion en el sentido contrario -- a
 * diferencia del aporte, donde la correccion es un monto negativo del MISMO
 * lado, aca cambia quien es `de` y quien es `para`, porque lo que importa
 * leer despues es quien le pago a quien, no un numero con signo.
 */
@Document(collection = "liquidacion")
public class Liquidacion {

    @Id
    private String id;

    @Field("grupo_id")
    private String grupoId;

    /** Quien hizo el pago. */
    private ReferenciaUsuario de;

    /** Quien lo recibio. */
    private ReferenciaUsuario para;

    /** Siempre positivo: la direccion ya la dicen `de` y `para`. */
    private BigDecimal monto;

    private LocalDate fecha;

    protected Liquidacion() {
    }

    public Liquidacion(String grupoId, ReferenciaUsuario de, ReferenciaUsuario para,
                       BigDecimal monto, LocalDate fecha) {
        this.grupoId = grupoId;
        this.de = de;
        this.para = para;
        this.monto = monto;
        this.fecha = fecha;
    }

    public String getId() {
        return id;
    }

    public String getGrupoId() {
        return grupoId;
    }

    public ReferenciaUsuario getDe() {
        return de;
    }

    public ReferenciaUsuario getPara() {
        return para;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public LocalDate getFecha() {
        return fecha;
    }
}
