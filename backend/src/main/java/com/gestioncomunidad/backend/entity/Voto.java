package com.gestioncomunidad.backend.entity;

import jakarta.persistence.*;

// Mapea la tabla existente: las decisiones y permisos se aplican en el servicio.
@Entity
@Table(name = "votos", uniqueConstraints = @UniqueConstraint(name = "uk_votos_usuario_votacion", columnNames = {"id_usuario", "id_votacion"}))
public class Voto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_voto")
    private Integer id;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private OpcionVoto opcion;
    @Column(name = "fecha_voto", nullable = false, insertable = false, updatable = false)
    private java.time.LocalDateTime fechaVoto;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_votacion", nullable = false)
    private Votacion votacion;
    public Integer getId() { return id; }
    public OpcionVoto getOpcion() { return opcion; }
    public void setOpcion(OpcionVoto opcion) { this.opcion = opcion; }
    public java.time.LocalDateTime getFechaVoto() { return fechaVoto; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Votacion getVotacion() { return votacion; }
    public void setVotacion(Votacion votacion) { this.votacion = votacion; }
}
