package com.freestyleperu.aplicacion.tienda.domain;

import com.freestyleperu.aplicacion.shared.domain.BaseEntity;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "storefront_banners")
public class StorefrontBanner extends BaseEntity {

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "headline", length = 150)
    private String headline;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "cta_label", length = 80)
    private String ctaLabel;

    @Column(name = "cta_url", length = 255)
    private String ctaUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoGeneral status = EstadoGeneral.ACTIVE;
}
