package com.freestyleperu.aplicacion.tienda;

import static org.assertj.core.api.Assertions.assertThat;

import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import com.freestyleperu.aplicacion.tienda.dto.response.PublicCategoriaResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fase 3 del plan de multi-tenant: {@code TiendaCatalogoService} cachea el catálogo público con
 * Caffeine (ver su Javadoc) para aguantar picos de tráfico — pero antes de este fix, sus 6
 * métodos {@code @Cacheable} usaban el {@code SimpleKeyGenerator} por defecto, que arma la clave
 * SOLO con los argumentos del método. Dos tenants pidiendo lo mismo (ej. la lista de categorías,
 * un método sin argumentos) compartían la misma entrada de caché — una fuga real de datos entre
 * negocios, no solo teórica. Este test prueba, contra el servidor HTTP real y la caché Caffeine
 * real (no mockeada, con el mismo TTL que producción), que dos tenants con la misma categoría de
 * negocio siguen viendo cada uno SOLO su propio catálogo.
 *
 * <p>Deliberadamente sin {@code @Transactional} a nivel de clase (misma razón que
 * VerticalNoRopaIntegrationTest/TenantResolutionIntegrationTest: el resolver de tenant de
 * Hibernate se consulta al ABRIR una sesión, no en cada query).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class TiendaCatalogoCacheIntegrationTest {

    private static final String HEADER_TENANT_SLUG = "X-Tenant-Slug";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private CompanySettingsRepository companySettingsRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void elCatalogoCacheadoNoSeFugaEntreTenants() {
        Long tenantA = crearTenantConCategoria("cache-tenant-a-" + System.nanoTime(), "Categoría Exclusiva A");
        Long tenantB = crearTenantConCategoria("cache-tenant-b-" + System.nanoTime(), "Categoría Exclusiva B");

        try {
            List<PublicCategoriaResponse> categoriasA = listarCategorias(tenantSlugDe(tenantA));
            assertThat(categoriasA).extracting(PublicCategoriaResponse::name).containsExactly("Categoría Exclusiva A");

            // Pedido inmediatamente después (mismo TTL de caché) del tenant B — antes del fix,
            // el método listarCategorias() no recibe argumentos, así que ambos tenants pegaban
            // la misma clave de caché y esto habría devuelto la categoría de A.
            List<PublicCategoriaResponse> categoriasB = listarCategorias(tenantSlugDe(tenantB));
            assertThat(categoriasB).extracting(PublicCategoriaResponse::name).containsExactly("Categoría Exclusiva B");

            // Y de vuelta a A, confirma que su propia entrada de caché tampoco fue pisada por B.
            List<PublicCategoriaResponse> categoriasAOtraVez = listarCategorias(tenantSlugDe(tenantA));
            assertThat(categoriasAOtraVez).extracting(PublicCategoriaResponse::name).containsExactly("Categoría Exclusiva A");
        } finally {
            limpiarTenant(tenantA);
            limpiarTenant(tenantB);
        }
    }

    private List<PublicCategoriaResponse> listarCategorias(String slug) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_TENANT_SLUG, slug);
        ResponseEntity<List<PublicCategoriaResponse>> respuesta = restTemplate.exchange(
                "/api/store/catalog/categories", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<PublicCategoriaResponse>>() {});
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody();
    }

    private final java.util.Map<Long, String> slugsPorTenant = new java.util.HashMap<>();

    private String tenantSlugDe(Long tenantId) {
        return slugsPorTenant.get(tenantId);
    }

    private Long crearTenantConCategoria(String slug, String nombreCategoria) {
        CompanySettings settings = new CompanySettings();
        settings.setSlug(slug);
        settings.setName("Tenant de prueba de caché " + slug);
        settings.setCurrencyCode("PEN");
        settings.setCurrencySymbol("S/");
        settings.setIgvRate(new BigDecimal("0.18"));
        settings.setShippingFlatRate(new BigDecimal("10.00"));
        settings.setReservationDepositAmount(BigDecimal.ZERO);
        settings.setReservationExpirationDays(1);
        settings.setPlan(Plan.ECOMMERCE);
        settings.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
        settings.setUpdatedAt(LocalDateTime.now());
        Long tenantId = companySettingsRepository.save(settings).getId();
        slugsPorTenant.put(tenantId, slug);

        TenantContext.set(tenantId);
        try {
            Category categoria = new Category();
            categoria.setName(nombreCategoria);
            categoria.setSlug(nombreCategoria.toLowerCase().replace(" ", "-") + "-" + System.nanoTime());
            categoryRepository.save(categoria);
        } finally {
            TenantContext.clear();
        }
        return tenantId;
    }

    private void limpiarTenant(Long tenantId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM categories WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM company_settings WHERE id = :t").setParameter("t", tenantId).executeUpdate();
        });
    }
}
