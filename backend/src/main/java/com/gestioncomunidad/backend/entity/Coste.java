package com.gestioncomunidad.backend.entity;

import jakarta.persistence.*;

// Datos persistentes; las autorizaciones y transiciones se comprueban en el servicio.
@Entity
@Table(name = "costes")
public class Coste {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_coste")
    private Integer id;
    @Column(nullable = false, length = 150)
    private String concepto;
    @Column(nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal importe;
    @Enumerated(EnumType.STRING) @Column(name = "estado_facturacion", nullable = false)
    private EstadoFacturacion estadoFacturacion;
    @Column(name = "fecha_registro", nullable = false, insertable = false, updatable = false)
    private java.time.LocalDateTime fechaRegistro;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_incidencia", nullable = false)
    private Incidencia incidencia;
    public Integer getId() { return id; }
    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }
    public java.math.BigDecimal getImporte() { return importe; }
    public void setImporte(java.math.BigDecimal importe) { this.importe = importe; }
    public EstadoFacturacion getEstadoFacturacion() { return estadoFacturacion; }
    public void setEstadoFacturacion(EstadoFacturacion estadoFacturacion) { this.estadoFacturacion = estadoFacturacion; }
    public java.time.LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public Incidencia getIncidencia() { return incidencia; }
    public void setIncidencia(Incidencia incidencia) { this.incidencia = incidencia; }
}
