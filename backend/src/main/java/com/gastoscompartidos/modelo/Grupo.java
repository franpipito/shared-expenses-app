package com.gastoscompartidos.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
}
