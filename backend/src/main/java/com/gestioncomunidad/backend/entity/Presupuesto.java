package com.gestioncomunidad.backend.entity;

import jakarta.persistence.*;

// Mapea la tabla existente: las decisiones y permisos se aplican en el servicio.
@Entity
@Table(name = "presupuestos")
public class Presupuesto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_presupuesto")
    private Integer id;
    @Column(nullable = false, length = 150)
    private String titulo;
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    @Column(nullable = false, length = 150)
    private String proveedor;
    @Column(nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal importe;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private EstadoPresupuesto estado;
    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private java.time.LocalDateTime fechaCreacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_incidencia", nullable = false)
    private Incidencia incidencia;
    public Integer getId() { return id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }
    public java.math.BigDecimal getImporte() { return importe; }
    public void setImporte(java.math.BigDecimal importe) { this.importe = importe; }
    public EstadoPresupuesto getEstado() { return estado; }
    public void setEstado(EstadoPresupuesto estado) { this.estado = estado; }
    public java.time.LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public Incidencia getIncidencia() { return incidencia; }
    public void setIncidencia(Incidencia incidencia) { this.incidencia = incidencia; }
}
