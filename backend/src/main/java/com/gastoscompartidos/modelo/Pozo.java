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
import java.util.ArrayList;
import java.util.List;

/**
 * La vaquita: una bolsa de plata a la que los dos aportan y de la que salen los
 * gastos de un viaje.
 *
 * EL INVARIANTE, que es todo el modelo:
 *
 *     restante          = suma(aportes) - suma(gastos con pozoId)
 *     deuda entre ellos = (aporto Franco - aporto Viole) / 2
 *
 * La segunda linea es la que importa: **sacar del pozo NO genera deuda**,
 * porque la plata ya se repartio al entrar. Si los dos ponen $400.000, el saldo
 * entre ellos es cero desde el minuto uno y sigue en cero todo el viaje, gasten
 * lo que gasten. Si uno pone mas que el otro, esa diferencia se registra UNA
 * sola vez, al aportar.
 *
 * Es la misma filosofia que `montoPagador` en Gasto: resolver al escribir para
 * no dividir al leer.
 *
 * LO QUE ESTE DOCUMENTO NO ES: una cuenta bancaria. La app no custodia plata --
 * eso seria PCI, prevencion de fraude y ser sujeto obligado ante la UIF. La
 * plata vive donde ellos decidan; esto es **el libro contable de esa cuenta**.
 *
 * Ver docs/vaquita.md para el razonamiento completo y las alternativas que se
 * descartaron.
 */
@Document(collection = "pozo")
// UN SOLO POZO ABIERTO POR GRUPO, garantizado por la base y no solo por el
// servicio.
//
// `partialFilter` es un indice PARCIAL: solo indexa los documentos que cumplen
// la condicion. Combinado con unique=true dice "no puede haber dos pozos del
// mismo grupo en estado ABIERTO", pero deja crear todos los CERRADOS que hagan
// falta -- que es exactamente la regla que queremos.
//
// Sin esto, el chequeo del servicio ("ya hay uno abierto?") tiene una ventana de
// carrera: dos POST /pozos simultaneos leen "no hay ninguno" y los dos crean.
// Con dos personas es improbable, pero la restriccion correcta vive en la base,
// no en un if.
@CompoundIndex(name = "idx_pozo_grupo_abierto", def = "{'grupo_id': 1}",
        unique = true, partialFilter = "{'estado': 'ABIERTO'}")
public class Pozo {

    @Id
    private String id;

    @Field("grupo_id")
    private String grupoId;

    /** Como lo llaman ellos: "Bariloche". Es lo que titula la pantalla. */
    private String nombre;

    /**
     * Cuanto se propusieron juntar. Opcional y **no es un tope**: nada se
     * rechaza por pasarlo. Sirve para dibujar cuanto falta para completarlo.
     *
     * Ojo con no confundirlo con un presupuesto, que estan descartados (la
     * usuaria es freelance con ingresos irregulares). Un presupuesto es un tope
     * sobre plata que todavia no tenes; esto es plata que ya pusieron.
     */
    private BigDecimal objetivo;

    private EstadoPozo estado;

    /**
     * Las fechas del viaje. Opcionales, y su unico uso es de UX: si hoy cae
     * adentro del rango, el formulario de alta abre con "Vaquita" preseleccionado.
     *
     * Se ata al rango y no a que el pozo exista para que el cafe que se compra
     * Viole sola despues del viaje no se cargue sin querer al pozo.
     */
    private LocalDate desde;
    private LocalDate hasta;

    /** Ver {@link Aporte}. Se agregan con $push atomico, nunca con save(). */
    private List<Aporte> aportes = new ArrayList<>();

    @CreatedDate
    @Field("creado_en")
    private Instant creadoEn;

    @LastModifiedDate
    @Field("actualizado_en")
    private Instant actualizadoEn;

    /**
     * Bloqueo optimista, igual que en Gasto.
     *
     * OJO CON UNA SUTILEZA QUE VALE LA PENA ENTENDER: los aportes y el cierre NO
     * pasan por save(), van por updateFirst() con un $push o un $set. Un
     * updateFirst a mano **no toca el @Version**, asi que lo incrementamos
     * nosotros con .inc("version", 1).
     *
     * Si no lo hicieramos, quedaria un agujero silencioso: alguien que leyo el
     * pozo ANTES del aporte podria despues llamar a save(), su version seguiria
     * coincidiendo, y pisaria la lista de aportes con la version vieja -- o sea,
     * borraria el aporte recien hecho sin ningun error.
     *
     * De ahi sale la regla de esta clase: **despues de crearlo, un Pozo no se
     * guarda nunca mas con save().** Todo cambio es una actualizacion
     * condicional.
     */
    @Version
    private Long version;

    protected Pozo() {
    }

    public Pozo(String grupoId, String nombre, BigDecimal objetivo,
                LocalDate desde, LocalDate hasta) {
        this.grupoId = grupoId;
        this.nombre = nombre;
        this.objetivo = objetivo;
        this.desde = desde;
        this.hasta = hasta;
        this.estado = EstadoPozo.ABIERTO;
    }

    /** Cuanto se puso en total. Son dos personas y unos pocos aportes. */
    public BigDecimal totalAportado() {
        return aportes.stream()
                .map(Aporte::monto)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Cuanto puso una persona. Para mostrar "Franco $400.000 / Viole $400.000". */
    public BigDecimal aportadoPor(String usuarioId) {
        return aportes.stream()
                .filter(a -> a.usuario().usuarioId().equals(usuarioId))
                .map(Aporte::monto)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /**
     * EL INVARIANTE DE LA VAQUITA, en el unico lugar donde vive.
     *
     * Vive en la entidad y no en el servicio por el mismo motivo que
     * `Gasto.deudaGenerada()`: es aritmetica de dominio, y aca se puede testear
     * sin levantar Spring ni una base. `gastado` entra por parametro porque sale
     * de un $sum sobre la coleccion de gastos, que el pozo no conoce ni tiene
     * por que conocer.
     *
     * **Puede dar negativo, y no es un error.** Si se les acabo la vaquita en
     * medio de una cena, el gasto se cargo igual y el pozo quedo en rojo.
     * Bloquear una carga parada en el mostrador es el pecado capital de esta
     * app: validar no es lo mismo que bloquear.
     */
    public BigDecimal restante(BigDecimal gastado) {
        return totalAportado().subtract(gastado);
    }

    public boolean estaAbierto() {
        return estado == EstadoPozo.ABIERTO;
    }

    /** Si hoy cae dentro del viaje. Sin fechas, un pozo abierto siempre lo esta. */
    public boolean vigenteEl(LocalDate dia) {
        if (!estaAbierto()) return false;
        if (desde != null && dia.isBefore(desde)) return false;
        return hasta == null || !dia.isAfter(hasta);
    }

    public String getId() {
        return id;
    }

    public String getGrupoId() {
        return grupoId;
    }

    public String getNombre() {
        return nombre;
    }

    public BigDecimal getObjetivo() {
        return objetivo;
    }

    public EstadoPozo getEstado() {
        return estado;
    }

    public LocalDate getDesde() {
        return desde;
    }

    public LocalDate getHasta() {
        return hasta;
    }

    public List<Aporte> getAportes() {
        return List.copyOf(aportes);
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
