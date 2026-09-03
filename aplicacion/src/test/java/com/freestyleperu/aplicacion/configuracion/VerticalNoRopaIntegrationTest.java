package com.freestyleperu.aplicacion.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import com.freestyleperu.aplicacion.catalogo.domain.Attribute;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeInputType;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.repository.AttributeRepository;
import com.freestyleperu.aplicacion.catalogo.repository.AttributeValueRepository;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.configuracion.domain.BusinessVertical;
import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.producto.dto.request.CrearProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.request.GenerarVariantesRequest;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoDetalleResponse;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteResponse;
import com.freestyleperu.aplicacion.producto.service.ProductoService;
import com.freestyleperu.aplicacion.producto.service.VarianteService;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cierre de la Fase 5 del sistema de atributos genéricos (ver plan aprobado): prueba un negocio
 * que NO es de ropa (ferretería — atributos "Voltaje"/"Capacidad", ninguno de tipo SWATCH) de
 * punta a punta, y confirma que sus datos quedan completamente aislados del tenant de ropa
 * (tenant 1, ver V51 backfill).
 *
 * <p>Nota de alcance: la resolución de tenant por subdominio (Fase 2 del plan de multi-tenant)
 * todavía no existe — por eso acá se fija {@link TenantContext} a mano, igual que hará
 * {@code TenantResolutionFilter} más adelante, en vez de levantar un servidor real y pegarle por
 * HTTP a un subdominio que todavía no se puede resolver. {@code ConfiguracionService} (branding,
 * IGV, envío, y por lo tanto {@code obtenerContextoIA()}) sigue hardcodeada a la fila
 * {@code id=1} a propósito — igual que {@code SuscripcionScheduler}/{@code PlanGate} — hacerla
 * tenant-aware es trabajo de esa Fase 2, no de esta.
 *
 * <p>Deliberadamente SIN {@code @Transactional} a nivel de clase: el resolver de tenant se
 * consulta cuando Hibernate ABRE una sesión/transacción, no en cada query (lección real de la
 * Fase 0 del plan de multi-tenant) — envolver el test entero en una transacción abriría la
 * sesión antes de fijar {@link TenantContext}. Cada llamada a un servicio (que gestiona su
 * propia transacción) recién abre su sesión al ejecutarse, en el momento correcto. Como no hay
 * rollback automático, el test limpia sus propios datos al final con SQL nativo dentro de su
 * propia transacción explícita (vía {@link TransactionTemplate}), para no dejar nada en el H2
 * compartido del resto de la suite.
 */
@SpringBootTest
@ActiveProfiles("test")
class VerticalNoRopaIntegrationTest {

    @Autowired private CompanySettingsRepository companySettingsRepository;
    @Autowired private AttributeRepository attributeRepository;
    @Autowired private AttributeValueRepository attributeValueRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductoService productoService;
    @Autowired private VarianteService varianteService;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void ferreteriaFuncionaDePuntaAPuntaSinNingunSupuestoDeRopaYAisladaDelTenantDeRopa() {
        Long tenantFerreteria = crearTenantFerreteria();

        try {
            TenantContext.set(tenantFerreteria);
            Attribute voltaje = crearAtributo("Voltaje", AttributeInputType.LIST);
            Attribute capacidad = crearAtributo("Capacidad", AttributeInputType.LIST);
            AttributeValue v110 = crearValor(voltaje, "110V", (short) 0);
            AttributeValue v220 = crearValor(voltaje, "220V", (short) 1);
            AttributeValue c12 = crearValor(capacidad, "1/2 pulgada", (short) 0);
            AttributeValue c38 = crearValor(capacidad, "3/8 pulgada", (short) 1);

            Category categoria = new Category();
            categoria.setName("Herramientas Eléctricas");
            categoria.setSlug("herramientas-electricas");
            categoria = categoryRepository.save(categoria);

            ProductoDetalleResponse producto = productoService.crear(new CrearProductoRequest(
                    null, null, "Taladro Percutor", categoria.getId(), null, null,
                    "Taladro percutor profesional", null, null, new BigDecimal("349.90"), null));

            // Ni "color" ni "talla" en ningún lado — solo los atributos reales del rubro.
            List<VarianteResponse> generadas = varianteService.generarMatriz(producto.id(),
                    new GenerarVariantesRequest(
                            List.of(List.of(v110.getId(), v220.getId()), List.of(c12.getId(), c38.getId())),
                            2, false));

            assertThat(generadas).hasSize(4);
            assertThat(generadas).extracting(VarianteResponse::variantLabel)
                    .containsExactlyInAnyOrder("110V / 1/2 pulgada", "110V / 3/8 pulgada", "220V / 1/2 pulgada", "220V / 3/8 pulgada");
            assertThat(generadas).allSatisfy(v -> {
                assertThat(v.sku()).startsWith(producto.sku() + "-");
                assertThat(v.attributes()).hasSize(2);
                assertThat(v.attributes()).extracting("attributeName").containsExactly("Voltaje", "Capacidad");
                // Ningún valor lleva hexCode — este rubro no tiene atributos tipo SWATCH.
                assertThat(v.attributes()).allSatisfy(a -> assertThat((Object) a.hexCode()).isNull());
            });

            assertThat(varianteService.listarPorProducto(producto.id())).hasSize(4);

            // Aislamiento: solo la ferretería ve sus propios atributos.
            assertThat(attributeRepository.findAllByOrderByNameAsc())
                    .extracting(Attribute::getName)
                    .containsExactlyInAnyOrder("Voltaje", "Capacidad");
        } finally {
            TenantContext.clear();
            limpiarDatosFerreteria(tenantFerreteria);
        }

        // Y viceversa: parado en el tenant de ropa (default), nada de la ferretería se filtra —
        // ni siquiera antes de la limpieza de arriba hubiera sido visible, pero esto confirma
        // que el aislamiento es real y no una casualidad del orden de las consultas.
        TenantContext.set(1L);
        try {
            assertThat(attributeRepository.findAllByOrderByNameAsc())
                    .extracting(Attribute::getName)
                    .doesNotContain("Voltaje", "Capacidad");
            assertThat(categoryRepository.findAllByOrderByNameAsc())
                    .extracting(Category::getName)
                    .doesNotContain("Herramientas Eléctricas");
        } finally {
            TenantContext.clear();
        }
    }

    private Long crearTenantFerreteria() {
        CompanySettings settings = new CompanySettings();
        settings.setSlug("ferreteria-demo-" + System.nanoTime());
        settings.setName("Ferretería El Tornillo Feliz");
        settings.setBusinessVertical(BusinessVertical.GENERAL);
        settings.setBusinessDescription("una ferretería en Perú");
        settings.setCurrencyCode("PEN");
        settings.setCurrencySymbol("S/");
        settings.setIgvRate(new BigDecimal("0.18"));
        settings.setShippingFlatRate(new BigDecimal("10.00"));
        settings.setReservationDepositAmount(BigDecimal.ZERO);
        settings.setReservationExpirationDays(1);
        settings.setPlan(Plan.IA);
        settings.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
        settings.setUpdatedAt(LocalDateTime.now());
        return companySettingsRepository.save(settings).getId();
    }

    private Attribute crearAtributo(String nombre, AttributeInputType tipo) {
        Attribute attribute = new Attribute();
        attribute.setName(nombre);
        attribute.setInputType(tipo);
        return attributeRepository.save(attribute);
    }

    private AttributeValue crearValor(Attribute attribute, String valor, short orden) {
        AttributeValue value = new AttributeValue();
        value.setAttribute(attribute);
        value.setValue(valor);
        value.setSortOrder(orden);
        return attributeValueRepository.save(value);
    }

    /** Ver Javadoc de la clase: SQL nativo en su propia transacción explícita, en orden seguro de FKs. */
    private void limpiarDatosFerreteria(Long tenantId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM variant_attribute_values WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM product_attributes WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM product_variants WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM products WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM attribute_values WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM attributes WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM categories WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM company_settings WHERE id = :t").setParameter("t", tenantId).executeUpdate();
        });
    }
}
