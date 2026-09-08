package com.gastoscompartidos.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Una categoria de gasto. Son datos de referencia: cafe, uber, comida, ropa,
 * regalos, otros. Salieron de las palabras que uso la usuaria en la entrevista.
 *
 * Con Postgres se sembraban con la migracion V2 de Flyway, que garantizaba que
 * viajaran con el esquema. Sin Flyway, las siembra `SembradorDeCategorias` al
 * arrancar. La diferencia no es cosmetica: Flyway registraba que la migracion
 * ya se habia aplicado, y el sembrador tiene que verificarlo el mismo cada vez.
 */
@Document(collection = "categoria")
public class Categoria {

    @Id
    private String id;

    /**
     * unique = true crea un indice unico en Mongo, que es lo que impide dos
     * categorias con el mismo nombre.
     *
     * OJO CON UNA DIFERENCIA IMPORTANTE respecto de Postgres: aca el indice lo
     * crea la app al arrancar (auto-index-creation), no una migracion. Si la
     * coleccion YA tuviera duplicados, la creacion del indice falla y se entera
     * el log, no el deploy. Con Flyway, una migracion que no podia aplicarse
     * frenaba el arranque.
     */
    @Indexed(unique = true)
    private String nombre;

    /** Nombre del icono de Lucide: "coffee", "car", "utensils". No es un emoji. */
    private String icono;

    protected Categoria() {
    }

    public Categoria(String nombre, String icono) {
        this.nombre = nombre;
        this.icono = icono;
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getIcono() {
        return icono;
    }
}
