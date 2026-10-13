package com.gestioncomunidad.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "historial_incidencias",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_historial_id_incidencia",
            columnNames = {"id_historial", "id_incidencia"}
        )
    }
)
public class HistorialIncidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_incidencia", nullable = false)
    private Incidencia incidencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_actor", nullable = false)
    private Usuario usuarioActor;

    @Column(name = "accion", nullable = false, length = 50)
    private String accion;

    @Column(name = "estado_anterior", length = 30)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", length = 30)
    private String estadoNuevo;

    @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tecnico_anterior")
    private Usuario tecnicoAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tecnico_nuevo")
    private Usuario tecnicoNuevo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_verificador_anterior")
    private Usuario verificadorAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_verificador_nuevo")
    private Usuario verificadorNuevo;

    @Column(
        name = "fecha_evento",
        nullable = false,
        insertable = false,
        updatable = false,
        columnDefinition = "DATETIME(6)"
    )
    private LocalDateTime fechaEvento;

    public HistorialIncidencia() {
    }

    public Long getId() {
        return id;
    }

    public Incidencia getIncidencia() {
        return incidencia;
    }

    public void setIncidencia(Incidencia incidencia) {
        this.incidencia = incidencia;
    }

    public Usuario getUsuarioActor() {
        return usuarioActor;
    }

    public void setUsuarioActor(Usuario usuarioActor) {
        this.usuarioActor = usuarioActor;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(String estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public String getEstadoNuevo() {
        return estadoNuevo;
    }

    public void setEstadoNuevo(String estadoNuevo) {
        this.estadoNuevo = estadoNuevo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Usuario getTecnicoAnterior() {
        return tecnicoAnterior;
    }

    public void setTecnicoAnterior(Usuario tecnicoAnterior) {
        this.tecnicoAnterior = tecnicoAnterior;
    }

    public Usuario getTecnicoNuevo() {
        return tecnicoNuevo;
    }

    public void setTecnicoNuevo(Usuario tecnicoNuevo) {
        this.tecnicoNuevo = tecnicoNuevo;
    }

    public Usuario getVerificadorAnterior() {
        return verificadorAnterior;
    }

    public void setVerificadorAnterior(Usuario verificadorAnterior) {
        this.verificadorAnterior = verificadorAnterior;
    }

    public Usuario getVerificadorNuevo() {
        return verificadorNuevo;
    }

    public void setVerificadorNuevo(Usuario verificadorNuevo) {
        this.verificadorNuevo = verificadorNuevo;
    }

    public LocalDateTime getFechaEvento() {
        return fechaEvento;
    }
}
