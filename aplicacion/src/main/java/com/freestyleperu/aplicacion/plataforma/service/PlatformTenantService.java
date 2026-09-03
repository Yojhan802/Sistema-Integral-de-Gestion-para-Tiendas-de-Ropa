package com.freestyleperu.aplicacion.plataforma.service;

import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.plataforma.domain.ModuloSistema;
import com.freestyleperu.aplicacion.plataforma.dto.request.ActualizarModulosRequest;
import com.freestyleperu.aplicacion.plataforma.dto.request.ActualizarModulosRequest.ModuloSeleccionado;
import com.freestyleperu.aplicacion.plataforma.dto.request.ActualizarTenantRequest;
import com.freestyleperu.aplicacion.plataforma.dto.request.CrearTenantRequest;
import com.freestyleperu.aplicacion.plataforma.dto.response.CrearTenantResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.TenantResponse;
import com.freestyleperu.aplicacion.plataforma.repository.PlatformTenantRepository;
import com.freestyleperu.aplicacion.shared.exception.RecursoDuplicadoException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlatformTenantService {

    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private final PlatformTenantRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformModuleService moduleService;
    private final SubscriptionRenewalService renewalService;
    private final SecureRandom random = new SecureRandom();

    public PlatformTenantService(PlatformTenantRepository repository, PasswordEncoder passwordEncoder,
            PlatformModuleService moduleService, SubscriptionRenewalService renewalService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.moduleService = moduleService;
        this.renewalService = renewalService;
    }

    /** Un plan sin ajustes se vende a precio de lista. */
    private static List<ModuloSeleccionado> preciosDelPlan(Plan plan) {
        return ModuloSistema.delPlan(plan).stream()
                .map(modulo -> new ModuloSeleccionado(modulo, modulo.getPrecioLista()))
                .toList();
    }

    public List<TenantResponse> listar(String search, SubscriptionStatus status) {
        return repository.findAll(search, status);
    }

    @Transactional
    public CrearTenantResponse crear(CrearTenantRequest request, Long actorId) {
        return crear(request, actorId, null);
    }

    @Transactional
    public CrearTenantResponse crear(CrearTenantRequest request, Long actorId, String actorUsername) {
        String slug = request.slug().trim().toLowerCase();
        String ownerUsername = request.ownerUsername().trim();
        if (repository.existsBySlug(slug)) {
            throw new RecursoDuplicadoException("Ya existe una empresa con el subdominio " + slug);
        }

        String temporaryPassword = generarPasswordTemporal();
        LocalDateTime now = LocalDateTime.now();
        Long tenantId;
        try {
            tenantId = repository.insertTenant(request.name().trim(), slug, blankToNull(request.ruc()),
                    blankToNull(request.address()), blankToNull(request.phone()), blankToNull(request.email()),
                    request.businessVertical(), request.plan(), request.nextPaymentDue(), request.esFacturable(), actorId, now);
            repository.seedTenant(tenantId, ownerUsername, blankToNull(request.ownerEmail()),
                    request.ownerFullName().trim(), passwordEncoder.encode(temporaryPassword), now);
        } catch (DataIntegrityViolationException ex) {
            // Solo el alta puede chocar por slug o usuario repetidos. Sembrar el paquete va
            // fuera del catch: un fallo suyo no es un duplicado y no debe disfrazarse de uno.
            throw new RecursoDuplicadoException("La empresa o el usuario administrador ya existe");
        }

        // El paquete se siembra en el alta: sin filas propias el acceso caería al plan, y la
        // gracia de vender por módulos es poder recortarlo desde el primer día.
        var paquete = moduleService.actualizar(tenantId, new ActualizarModulosRequest(
                request.modulos() == null || request.modulos().isEmpty()
                        ? preciosDelPlan(request.plan())
                        : request.modulos()));

        // El costo de implementación cubre el primer mes: se fija el vencimiento y se deja
        // registrado el cobro. Sin esto la empresa nacía sin fecha, y sin fecha nada la
        // suspende nunca aunque deje de pagar.
        LocalDate cubreHasta = request.nextPaymentDue() != null
                ? request.nextPaymentDue()
                : LocalDate.now().plusMonths(1);
        repository.actualizarVencimiento(tenantId, cubreHasta, now);
        renewalService.registrarImplementacion(tenantId,
                request.costoImplementacion() != null ? request.costoImplementacion() : paquete.totalMensual(),
                cubreHasta, actorId, actorUsername);

        TenantResponse tenant = repository.findById(tenantId);
        repository.insertAudit(tenantId, actorId, "TENANT_CREADO", tenantId, now);
        return new CrearTenantResponse(tenant, ownerUsername, temporaryPassword);
    }

    @Transactional
    public TenantResponse actualizar(Long tenantId, ActualizarTenantRequest request, Long actorId) {
        if (!repository.existsTenant(tenantId)) {
            throw RecursoNoEncontradoException.de("Empresa", tenantId);
        }
        boolean facturable = request.facturableODefecto(repository.findById(tenantId).billable());
        repository.updateTenant(tenantId, request.name().trim(), blankToNull(request.ruc()), blankToNull(request.address()),
                blankToNull(request.phone()), blankToNull(request.email()), request.businessVertical(), request.plan(),
                request.subscriptionStatus(), facturable, request.nextPaymentDue(), actorId, LocalDateTime.now());
        TenantResponse tenant = repository.findById(tenantId);
        repository.insertAudit(tenantId, actorId, "TENANT_ACTUALIZADO", tenantId, LocalDateTime.now());
        return tenant;
    }

    private String generarPasswordTemporal() {
        StringBuilder password = new StringBuilder(14);
        password.append((char) ('A' + random.nextInt(26)));
        password.append((char) ('a' + random.nextInt(26)));
        password.append(2 + random.nextInt(8));
        while (password.length() < 14) {
            password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
