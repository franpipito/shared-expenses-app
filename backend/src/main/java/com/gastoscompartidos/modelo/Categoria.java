package com.gastoscompartidos.modelo;

import jakarta.persistence.*;

/**
 * Categorias predefinidas y compartidas por todos los grupos.
 * Es tabla y no enum de Java a proposito: asi se pueden agregar categorias
 * sin recompilar ni redeployar el backend.
 */
@Entity
@Table(
        name = "categoria",
        uniqueConstraints = @UniqueConstraint(name = "uk_categoria_nombre", columnNames = "nombre")
)
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nombre;

    /** Nombre del icono que consume el cliente (mobile/web decide como dibujarlo). */
    @Column(nullable = false, length = 50)
    private String icono;

    protected Categoria() {
    }

    public Categoria(String nombre, String icono) {
        this.nombre = nombre;
        this.icono = icono;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getIcono() {
        return icono;
    }

    public void setIcono(String icono) {
        this.icono = icono;
    }
}
