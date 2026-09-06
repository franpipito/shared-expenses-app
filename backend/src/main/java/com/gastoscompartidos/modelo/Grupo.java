package com.gastoscompartidos.modelo;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Un grupo es la unidad de convivencia: en el MVP, la pareja.
 * Todo gasto pertenece a un grupo, y el saldo se calcula siempre dentro de un grupo.
 */
@Entity
@Table(name = "grupo")
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    /**
     * mappedBy = "grupo" significa: el dueño de la relacion es el campo `grupo`
     * de Usuario. La foreign key vive en la tabla usuario, no aca.
     * Esta lista es solo el lado de lectura.
     */
    @OneToMany(mappedBy = "grupo")
    private List<Usuario> usuarios = new ArrayList<>();

    /** JPA necesita un constructor sin argumentos para instanciar al leer de la base. */
    protected Grupo() {
    }

    public Grupo(String nombre) {
        this.nombre = nombre;
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

    public List<Usuario> getUsuarios() {
        return usuarios;
    }
}
