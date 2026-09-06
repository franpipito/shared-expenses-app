package com.gastoscompartidos.modelo;

import jakarta.persistence.*;

/**
 * Un usuario pertenece a exactamente un grupo.
 * La contrasena se guarda hasheada (BCrypt); nunca en texto plano.
 */
@Entity
@Table(
        name = "usuario",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuario_email", columnNames = "email")
)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * FetchType.LAZY: al traer un Usuario, NO se trae el Grupo hasta que alguien
     * llame a getGrupo(). Por defecto @ManyToOne es EAGER, que dispara un JOIN
     * en cada consulta aunque no se use. LAZY es casi siempre lo que uno quiere.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_usuario_grupo"))
    private Grupo grupo;

    protected Usuario() {
    }

    public Usuario(String nombre, String email, String passwordHash, Grupo grupo) {
        this.nombre = nombre;
        this.email = email;
        this.passwordHash = passwordHash;
        this.grupo = grupo;
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

    public Grupo getGrupo() {
        return grupo;
    }

    public void setGrupo(Grupo grupo) {
        this.grupo = grupo;
    }
}
