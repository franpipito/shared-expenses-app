package com.gastoscompartidos.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Un usuario pertenece a exactamente un grupo.
 * La contrasena se guarda hasheada (BCrypt); nunca en texto plano.
 */
@Document(collection = "usuario")
public class Usuario {

    @Id
    private String id;

    private String nombre;

    /**
     * unique = true crea el indice unico sobre email, que es lo que impide dos
     * cuentas con el mismo mail.
     *
     * DIFERENCIA REAL CON POSTGRES, y conviene tenerla clara: alla la restriccion
     * la creaba una migracion y estaba garantizada antes de que la app corriera.
     * Aca el indice lo crea la app al arrancar. Si por algun motivo no llega a
     * crearse, **Mongo acepta el duplicado sin quejarse** — no hay un esquema
     * que lo impida por su cuenta. Es la clase de garantia que en Mongo hay que
     * verificar, no asumir.
     */
    @Indexed(unique = true)
    private String email;

    /**
     * @Field renombra el campo en el documento. Se podria dejar `passwordHash`
     * en camelCase, pero se mantiene `password_hash` para que quien mire la
     * coleccion desde Atlas vea los mismos nombres que veia en Postgres.
     */
    @Field("password_hash")
    private String passwordHash;

    /**
     * Numero de generacion de los tokens de este usuario.
     *
     * Cada JWT emitido lleva adentro el valor que tenia este campo en ese
     * momento, y cada request lo compara contra el actual. Subirle uno invalida
     * al instante todos los tokens emitidos antes: es la forma de recuperar la
     * capacidad de revocar sin dejar de ser stateless.
     */
    @Field("token_version")
    private long tokenVersion = 0L;

    /**
     * El grupo, como referencia por id y no como objeto embebido.
     *
     * En JPA esto era un @ManyToOne LAZY. En Mongo hay tres opciones y vale
     * saber por que esta:
     *
     *  - Embeber el Grupo entero: se duplicaria su nombre en cada usuario y
     *    habria que actualizar los dos documentos al renombrarlo.
     *  - @DBRef: Spring Data resuelve la referencia sola, pero dispara una
     *    consulta extra invisible por cada acceso. Es el N+1 de JPA, con otro
     *    nombre y sin fetch join para arreglarlo.
     *  - El id pelado, que es esto: explicito, una sola consulta, y quien
     *    necesita el grupo lo pide.
     *
     * En la practica casi nunca hace falta el Grupo entero: lo que se usa es el
     * id, para filtrar los gastos.
     */
    @Field("grupo_id")
    private String grupoId;

    protected Usuario() {
    }

    public Usuario(String nombre, String email, String passwordHash, String grupoId) {
        this.nombre = nombre;
        this.email = email;
        this.passwordHash = passwordHash;
        this.grupoId = grupoId;
    }

    /**
     * El snapshot de este usuario que se embebe en cada gasto que paga.
     *
     * Fijate que solo lleva id y nombre: el email y el hash de la contrasena se
     * quedan afuera por construccion. Con Postgres, `join fetch g.pagadoPor`
     * traia la entidad entera y el hash viajaba de la base a la app en cada
     * listado de gastos — era un pendiente conocido en CLAUDE.md. El modelo de
     * documentos lo resuelve solo.
     */
    public ReferenciaUsuario comoReferencia() {
        return new ReferenciaUsuario(id, nombre);
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    /** Deja fuera a todos los tokens ya emitidos para este usuario. */
    public void invalidarSesiones() {
        this.tokenVersion++;
    }

    public String getGrupoId() {
        return grupoId;
    }

    public void setGrupoId(String grupoId) {
        this.grupoId = grupoId;
    }
}
