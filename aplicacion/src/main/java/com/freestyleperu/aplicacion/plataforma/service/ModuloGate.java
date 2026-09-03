package com.freestyleperu.aplicacion.plataforma.service;

import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.plataforma.domain.ModuloSistema;
import com.freestyleperu.aplicacion.plataforma.domain.TenantModule;
import com.freestyleperu.aplicacion.plataforma.repository.TenantModuleRepository;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultado desde {@code @PreAuthorize("@modulos.activo('TIENDA')")}, igual que se hacía
 * con {@code @planGate.tienePlan(...)}, pero preguntando por un módulo contratado en vez
 * de por un peldaño de la escalera de planes.
 *
 * <p>El acceso se decide siempre aquí, en el servidor. Ocultar entradas del menú es
 * cosmética: un módulo no contratado tiene que fallar aunque alguien escriba la URL.
 */
@Component("modulos")
@Transactional(readOnly = true)
public class ModuloGate {

    private final TenantModuleRepository repository;
    private final CompanySettingsRepository companySettingsRepository;

    public ModuloGate(TenantModuleRepository repository, CompanySettingsRepository companySettingsRepository) {
        this.repository = repository;
        this.companySettingsRepository = companySettingsRepository;
    }

    /**
     * Se cachea porque se consulta en casi cada petición protegida. La invalidación va por
     * tenant, desde el único sitio que puede cambiar el conjunto: el panel del operador.
     */
    @Cacheable(cacheNames = "tenantModules", key = "#tenantId")
    public Set<ModuloSistema> modulosDe(Long tenantId) {
        Set<ModuloSistema> contratados = repository.findAllByTenantId(tenantId).stream()
                .map(TenantModule::getModule)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ModuloSistema.class)));
        if (contratados.isEmpty()) {
            // Sin configuración propia manda el plan: es lo que ese plan siempre otorgó, así
            // que una empresa recién creada —o una que el backfill no alcanzó— sigue operando
            // igual que antes en vez de quedarse sin acceso a nada.
            return companySettingsRepository.findById(tenantId)
                    .map(settings -> ModuloSistema.delPlan(settings.getPlan()))
                    .orElseGet(() -> EnumSet.noneOf(ModuloSistema.class));
        }
        // El cierre se aplica también al leer: si una edición manual dejó el conjunto
        // incompleto, el sistema funciona en vez de romperse a mitad de una venta.
        return ModuloSistema.cerrarDependencias(contratados);
    }

    public boolean activo(String codigo) {
        return modulosDe(TenantContext.getOrDefault()).contains(ModuloSistema.valueOf(codigo));
    }

    public Set<ModuloSistema> modulosDelTenantActual() {
        return modulosDe(TenantContext.getOrDefault());
    }

    @CacheEvict(cacheNames = "tenantModules", key = "#tenantId")
    public void invalidar(Long tenantId) {
        // El cuerpo vacío es intencional: solo existe para desalojar la caché.
    }
}
