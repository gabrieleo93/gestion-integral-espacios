package com.gestioncomunidad.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Auditoría separada del historial de incidencias: una solicitud aún no concede acceso a ellas.
@Entity
@Table(name = "historial_pertenencias")
public class HistorialPertenencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_comunidad", nullable = false) private UsuarioComunidad pertenencia;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_actor", nullable = false) private Usuario actor;
    @Column(nullable = false, length = 30) private String accion;
    @Column(name = "estado_anterior", length = 20) private String estadoAnterior;
    @Column(name = "estado_nuevo", nullable = false, length = 20) private String estadoNuevo;
    @Column(nullable = false, length = 1000) private String motivo;
    @Column(name = "fecha_evento", nullable = false, insertable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime fechaEvento;
    public Long getId() { return id; }
    public UsuarioComunidad getPertenencia() { return pertenencia; }
    public void setPertenencia(UsuarioComunidad pertenencia) { this.pertenencia = pertenencia; }
    public Usuario getActor() { return actor; }
    public void setActor(Usuario actor) { this.actor = actor; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public String getEstadoAnterior() { return estadoAnterior; }
    public void setEstadoAnterior(String estadoAnterior) { this.estadoAnterior = estadoAnterior; }
    public String getEstadoNuevo() { return estadoNuevo; }
    public void setEstadoNuevo(String estadoNuevo) { this.estadoNuevo = estadoNuevo; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public LocalDateTime getFechaEvento() { return fechaEvento; }
}
