package com.gestioncomunidad.backend.entity;

import jakarta.persistence.*;

// Datos persistentes; las autorizaciones y transiciones se comprueban en el servicio.
@Entity
@Table(name = "reuniones")
public class Reunion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_reunion")
    private Integer id;
    @Column(nullable = false, length = 150)
    private String titulo;
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    @Column(name = "fecha_reunion", nullable = false)
    private java.time.LocalDateTime fechaReunion;
    @Column(nullable = false, length = 255)
    private String lugar;
    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private java.time.LocalDateTime fechaCreacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_comunidad", nullable = false)
    private Comunidad comunidad;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_usuario_creador", nullable = false)
    private Usuario creador;
    public Integer getId() { return id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public java.time.LocalDateTime getFechaReunion() { return fechaReunion; }
    public void setFechaReunion(java.time.LocalDateTime fechaReunion) { this.fechaReunion = fechaReunion; }
    public String getLugar() { return lugar; }
    public void setLugar(String lugar) { this.lugar = lugar; }
    public java.time.LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public Comunidad getComunidad() { return comunidad; }
    public void setComunidad(Comunidad comunidad) { this.comunidad = comunidad; }
    public Usuario getCreador() { return creador; }
    public void setCreador(Usuario creador) { this.creador = creador; }
}
