package com.gastoscompartidos.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Una categoria de gasto.
 *
 * HASTA LA v1.1 (seccion 2.5) ERAN GLOBALES: un unico documento por nombre,
 * compartido por TODA la base -- las seis que uso la usuaria en la entrevista
 * (cafe, uber, comida, ropa, regalos, otros), sembradas una sola vez al
 * arrancar. Con las categorias 100% personalizables eso dejo de alcanzar: si
 * Franco agrega "Netflix", no tiene sentido que le aparezca a cualquier otra
 * persona que se registre desde la App Store, y viceversa.
 *
 * Ahora CADA GRUPO tiene su propia coleccion de categorias, empezando por una
 * copia de esas mismas seis (`CategoriaServicio.sembrarParaGrupo`, llamado al
 * crear el grupo en el registro). Un grupo puede editar esa copia como quiera
 * -- agregar, borrar, inclusive las seis originales -- sin que le toque nada
 * a ningun otro grupo.
 *
 * Sigue siendo un documento chico y de referencia, igual que antes. Lo que
 * cambio es el ALCANCE, no la forma.
 */
@CompoundIndex(name = "idx_categoria_grupo_nombre", def = "{'grupo_id': 1, 'nombre': 1}", unique = true)
@Document(collection = "categoria")
public class Categoria {

    @Id
    private String id;

    @Field("grupo_id")
    private String grupoId;

    /**
     * Unico DENTRO DEL GRUPO (ver el indice compuesto de la clase), ya no en
     * toda la base: dos grupos distintos pueden tener cada uno su propio
     * "cafe" sin chocar entre si.
     */
    private String nombre;

    /** Nombre del icono de Lucide: "coffee", "car", "utensils". No es un emoji. */
    private String icono;

    protected Categoria() {
    }

    public Categoria(String grupoId, String nombre, String icono) {
        this.grupoId = grupoId;
        this.nombre = nombre;
        this.icono = icono;
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

    public String getIcono() {
        return icono;
    }
}
