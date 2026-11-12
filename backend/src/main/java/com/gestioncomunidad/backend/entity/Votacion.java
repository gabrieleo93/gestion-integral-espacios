package com.gestioncomunidad.backend.entity;

import jakarta.persistence.*;

// Mapea la tabla existente: las decisiones y permisos se aplican en el servicio.
@Entity
@Table(name = "votaciones")
public class Votacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_votacion")
    private Integer id;
    @Column(nullable = false, length = 150)
    private String titulo;
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    @Column(name = "fecha_inicio", nullable = false)
    private java.time.LocalDateTime fechaInicio;
    @Column(name = "fecha_fin", nullable = false)
    private java.time.LocalDateTime fechaFin;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private EstadoVotacion estado;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_presupuesto", nullable = false, unique = true)
    private Presupuesto presupuesto;
    public Integer getId() { return id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public java.time.LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(java.time.LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }
    public java.time.LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(java.time.LocalDateTime fechaFin) { this.fechaFin = fechaFin; }
    public EstadoVotacion getEstado() { return estado; }
    public void setEstado(EstadoVotacion estado) { this.estado = estado; }
    public Presupuesto getPresupuesto() { return presupuesto; }
    public void setPresupuesto(Presupuesto presupuesto) { this.presupuesto = presupuesto; }
}
