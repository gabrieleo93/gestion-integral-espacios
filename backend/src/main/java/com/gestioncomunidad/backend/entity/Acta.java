package com.gestioncomunidad.backend.entity;

import jakarta.persistence.*;

// Datos persistentes; las autorizaciones y transiciones se comprueban en el servicio.
@Entity
@Table(name = "actas")
public class Acta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_acta")
    private Integer id;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;
    @Column(name = "fecha_publicacion", nullable = false, insertable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private java.time.LocalDateTime fechaPublicacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_reunion", nullable = false, unique = true)
    private Reunion reunion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_usuario_publicador", nullable = false)
    private Usuario publicador;
    public Integer getId() { return id; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public java.time.LocalDateTime getFechaPublicacion() { return fechaPublicacion; }
    public Reunion getReunion() { return reunion; }
    public void setReunion(Reunion reunion) { this.reunion = reunion; }
    public Usuario getPublicador() { return publicador; }
    public void setPublicador(Usuario publicador) { this.publicador = publicador; }
}
