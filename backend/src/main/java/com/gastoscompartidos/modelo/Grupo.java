package com.gastoscompartidos.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * El grupo al que pertenecen los dos integrantes.
 *
 * Fijate lo que ya NO esta: la lista `usuarios`. En JPA era un @OneToMany
 * mapeado por la otra punta. En Mongo no hay relaciones bidireccionales que el
 * mapeador mantenga por vos: si quiero los integrantes del grupo, se los pido
 * al UsuarioRepositorio filtrando por grupoId.
 *
 * Podrian embeberse los usuarios adentro del grupo, y en muchos disenios de
 * Mongo seria lo idiomatico. Aca NO, por un motivo concreto: el login busca por
 * email, y con los usuarios embebidos habria que buscar dentro de un array de
 * un documento de otra coleccion cada vez que alguien entra a la app. El
 * usuario es una entidad con vida propia, no un detalle del grupo.
 */
@Document(collection = "grupo")
public class Grupo {

    /**
     * String y no Long: el _id de Mongo es un ObjectId de 12 bytes, y Spring
     * Data lo convierte a String. No hay secuencias ni autoincremento — el id
     * lo genera el driver ANTES de escribir, que es una diferencia practica
     * respecto de Postgres, donde habia que insertar para conocerlo.
     */
    @Id
    private String id;

    private String nombre;

    /**
     * El codigo de invitacion vigente, si hay uno. A diferencia del codigo de
     * reseteo de contrasena (Usuario.resetCodigoHash), este NO se guarda
     * hasheado, y es a proposito:
     *
     *  - El de reseteo tiene que resistir fuerza bruta con solo 6 digitos (un
     *    millon de combinaciones), asi que el hash + el tope de intentos son la
     *    defensa real.
     *  - Este codigo tiene mucha mas entropia (8 caracteres de un alfabeto de
     *    32), y ademas hace falta poder buscarlo DIRECTO por su valor -- quien
     *    se suma no sabe de que grupo es el codigo, asi que no hay ningun otro
     *    dato (como el email en el reseteo) para llegar primero al documento y
     *    despues comparar el hash.
     *
     * `unique = true` mas `sparse = true`: unique sin sparse fallaria en cuanto
     * DOS grupos no tuvieran codigo activo, porque Mongo trata el campo
     * ausente como un valor mas (null) a los fines del indice, y dos nulls
     * colisionan. Sparse saca del indice a los documentos que no tienen el
     * campo, que es el caso normal.
     */
    @Indexed(unique = true, sparse = true)
    @Field("invitacion_codigo")
    private String invitacionCodigo;

    @Field("invitacion_vence")
    private Instant invitacionVence;

    protected Grupo() {
    }

    public Grupo(String nombre) {
        this.nombre = nombre;
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getInvitacionCodigo() {
        return invitacionCodigo;
    }

    public Instant getInvitacionVence() {
        return invitacionVence;
    }

    /** Genera una invitacion nueva. Si ya habia una vigente, la reemplaza. */
    public void generarInvitacion(String codigo, Instant vence) {
        this.invitacionCodigo = codigo;
        this.invitacionVence = vence;
    }
}
