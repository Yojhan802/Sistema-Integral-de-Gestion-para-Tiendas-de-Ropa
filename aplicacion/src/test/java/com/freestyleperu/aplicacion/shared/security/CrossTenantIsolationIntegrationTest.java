package com.freestyleperu.aplicacion.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.freestyleperu.aplicacion.auth.dto.LoginRequest;
import com.freestyleperu.aplicacion.auth.dto.LoginResponse;
import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.cliente.domain.Customer;
import com.freestyleperu.aplicacion.cliente.domain.TipoDocumento;
import com.freestyleperu.aplicacion.cliente.repository.CustomerRepository;
import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.producto.dto.request.CrearProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoDetalleResponse;
import com.freestyleperu.aplicacion.producto.service.ProductoService;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.usuario.domain.Permiso;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.repository.PermisoRepository;
import com.freestyleperu.aplicacion.usuario.repository.RolRepository;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fase 4 del plan de multi-tenant: batería de aislamiento cruzado a nivel de referencia directa a
 * objeto (IDOR). Las Fases 2-3 ya probaron que el mecanismo de {@code @TenantId} de Hibernate
 * funciona (listados, caché, JWT) — lo que falta probar es el camino de ataque más realista que
 * queda: un staff YA AUTENTICADO y con el permiso correcto en SU propio tenant, adivinando o
 * conociendo el id de un recurso de OTRO tenant y pidiéndolo directo por id. Como
 * {@code @TenantId} inyecta el filtro de tenant automáticamente en cualquier
 * {@code findById(id)}, el resultado esperado en todos los casos es 404 (el recurso "no existe"
 * para ese tenant), nunca 200 con datos ajenos ni 403 — confirmando que ni siquiera conociendo el
 * id exacto se puede leer un cliente, producto o cualquier otro recurso de otro negocio.
 *
 * <p>Cubre clientes (PII) y productos (catálogo) como representantes — el mecanismo subyacente es
 * el mismo para las ~39 entidades tenant-scoped de la app (confirmado por auditoría: todas
 * extienden {@code BaseEntity} salvo {@code Permiso}, que es catálogo global de permisos a
 * propósito), así que no hace falta repetir la misma prueba entidad por entidad.
 *
 * <p>Deliberadamente sin {@code @Transactional} a nivel de clase (misma razón que los demás tests
 * de esta fase: el resolver de tenant de Hibernate se consulta al ABRIR una sesión).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class CrossTenantIsolationIntegrationTest {

    private static final String HEADER_TENANT_SLUG = "X-Tenant-Slug";
    private static final String PERMISO_CLIENTES_CONSULTAR = "CLIENTES_CONSULTAR";
    private static final String PERMISO_PRODUCTOS_CONSULTAR = "PRODUCTOS_CONSULTAR";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private CompanySettingsRepository companySettingsRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductoService productoService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PermisoRepository permisoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void unStaffAutenticadoNoPuedeLeerClientesNiProductosDeOtroTenantAunConociendoElId() {
        Long tenantA = crearTenant("idor-tenant-a-" + System.nanoTime());
        Long tenantB = crearTenant("idor-tenant-b-" + System.nanoTime());

        try {
            Long clienteAId = crearCliente(tenantA, "Cliente Tenant A");
            Long clienteBId = crearCliente(tenantB, "Cliente Tenant B");
            Long productoAId = crearProducto(tenantA, "Producto Tenant A");
            Long productoBId = crearProducto(tenantB, "Producto Tenant B");

            String slugA = slugDe(tenantA);
            crearStaff(tenantA, "idor.staff", Set.of(PERMISO_CLIENTES_CONSULTAR, PERMISO_PRODUCTOS_CONSULTAR));
            String token = login(slugA, "idor.staff", "ClaveValida123");

            // Control positivo: el staff SÍ ve sus propios recursos — confirma que el 404 de
            // abajo es aislamiento real, no un bug distinto que rompiera el endpoint entero.
            assertThat(get("/api/customers/" + clienteAId, token, slugA).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get("/api/products/" + productoAId, token, slugA).getStatusCode()).isEqualTo(HttpStatus.OK);

            // El mismo staff, mismo token, mismo permiso — pero pidiendo el id de OTRO tenant.
            assertThat(get("/api/customers/" + clienteBId, token, slugA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(get("/api/products/" + productoBId, token, slugA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        } finally {
            limpiarTenant(tenantA);
            limpiarTenant(tenantB);
        }
    }

    private final java.util.Map<Long, String> slugsPorTenant = new java.util.HashMap<>();

    private String slugDe(Long tenantId) {
        return slugsPorTenant.get(tenantId);
    }

    private ResponseEntity<String> get(String path, String token, String slug) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_TENANT_SLUG, slug);
        headers.set("Authorization", "Bearer " + token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String login(String slug, String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_TENANT_SLUG, slug);
        ResponseEntity<LoginResponse> respuesta = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(username, password), headers), LoginResponse.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().accessToken();
    }

    private Long crearTenant(String slug) {
        CompanySettings settings = new CompanySettings();
        settings.setSlug(slug);
        settings.setName("Tenant IDOR " + slug);
        settings.setCurrencyCode("PEN");
        settings.setCurrencySymbol("S/");
        settings.setIgvRate(new BigDecimal("0.18"));
        settings.setShippingFlatRate(new BigDecimal("10.00"));
        settings.setReservationDepositAmount(BigDecimal.ZERO);
        settings.setReservationExpirationDays(1);
        settings.setPlan(Plan.ECOMMERCE);
        settings.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
        settings.setUpdatedAt(LocalDateTime.now());
        Long id = companySettingsRepository.save(settings).getId();
        slugsPorTenant.put(id, slug);
        return id;
    }

    private Long crearCliente(Long tenantId, String nombre) {
        TenantContext.set(tenantId);
        try {
            Customer customer = new Customer();
            customer.setFullName(nombre);
            customer.setDocType(TipoDocumento.DNI);
            customer.setDocNumber(String.valueOf(10_000_000 + (int) (System.nanoTime() % 89_000_000)));
            customer.setStatus(EstadoGeneral.ACTIVE);
            return customerRepository.save(customer).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private Long crearProducto(Long tenantId, String nombre) {
        TenantContext.set(tenantId);
        try {
            Category categoria = new Category();
            categoria.setName("Categoría " + nombre);
            categoria.setSlug("categoria-" + nombre.toLowerCase().replace(" ", "-") + "-" + System.nanoTime());
            categoria = categoryRepository.save(categoria);

            ProductoDetalleResponse producto = productoService.crear(new CrearProductoRequest(
                    null, null, nombre, categoria.getId(), null, null, null, null, null, new BigDecimal("99.90"), null));
            return producto.id();
        } finally {
            TenantContext.clear();
        }
    }

    private void crearStaff(Long tenantId, String username, Set<String> codigosPermiso) {
        TenantContext.set(tenantId);
        try {
            Set<Permiso> permisos = new HashSet<>();
            for (String codigo : codigosPermiso) {
                Permiso permiso = permisoRepository.findAll().stream()
                        .filter(p -> p.getCode().equals(codigo))
                        .findFirst()
                        .orElseGet(() -> permisoRepository.save(Permiso.builder().code(codigo).module("TEST").description(codigo).build()));
                permisos.add(permiso);
            }

            Rol rol = new Rol();
            rol.setCode("TEST_ROL_IDOR_" + System.nanoTime());
            rol.setName("Rol de prueba IDOR");
            rol.setSystem(false);
            rol.setPermisos(permisos);
            rolRepository.save(rol);

            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setPasswordHash(passwordEncoder.encode("ClaveValida123"));
            usuario.setFullName("Staff de prueba IDOR");
            usuario.setStatus(UsuarioEstado.ACTIVE);
            usuario.setRoles(new HashSet<>(Set.of(rol)));
            usuarioRepository.save(usuario);
        } finally {
            TenantContext.clear();
        }
    }

    private void limpiarTenant(Long tenantId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM refresh_tokens WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery(
                    "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE tenant_id = :t)")
                    .setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM product_variants WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM products WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM categories WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM customers WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM company_settings WHERE id = :t").setParameter("t", tenantId).executeUpdate();
        });
    }
}
