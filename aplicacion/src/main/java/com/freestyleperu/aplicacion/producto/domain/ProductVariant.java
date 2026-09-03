package com.freestyleperu.aplicacion.producto.domain;

import com.freestyleperu.aplicacion.shared.domain.BaseEntity;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_variants", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "tenant_id", "sku" }),
        @UniqueConstraint(columnNames = { "tenant_id", "barcode" }),
        @UniqueConstraint(columnNames = { "tenant_id", "product_id", "combination_hash" }) })
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Sin orden garantizado por JPQL (requeriría unir contra product_attributes.position, que
     * está a 2 saltos) — quien necesite el orden real de exhibición usa {@code ProductAttribute}
     * del producto para ordenar esta colección en memoria (ver VarianteMapper).
     */
    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VariantAttributeValue> attributeValues = new ArrayList<>();

    /** Pre-armado ("Rojo / M", "220V") en orden de {@code ProductAttribute.position} — evita que
     * el camino caliente (POS, inventario, tickets) necesite unir contra las tablas de atributos. */
    @Column(name = "variant_label", nullable = false, length = 150)
    private String variantLabel;

    /** SHA-256 sobre la lista ordenada (ascendente) de attribute_value_id — ver VarianteService. */
    @Column(name = "combination_hash", nullable = false, length = 64)
    private String combinationHash;

    @Column(name = "sku", nullable = false, length = 80)
    private String sku;

    @Column(name = "barcode", length = 20)
    private String barcode;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "min_stock", nullable = false)
    private int minStock;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoGeneral status = EstadoGeneral.ACTIVE;
}
