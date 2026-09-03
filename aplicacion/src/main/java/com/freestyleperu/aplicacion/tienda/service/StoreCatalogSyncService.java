package com.freestyleperu.aplicacion.tienda.service;

import com.freestyleperu.aplicacion.notificacion.service.NotificacionService;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import java.util.List;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Invalida la lectura pública del catálogo después del commit y avisa a las tiendas abiertas.
 * Las claves de caché incluyen tenant, pero se limpian todas para no depender de la forma de
 * cada clave y para evitar servir una versión anterior en otro nodo/proceso.
 */
@Service
public class StoreCatalogSyncService {

    private static final List<String> CACHE_NAMES = List.of(
            "storeCatalogProducts", "storeCatalogProductDetail", "storeCatalogCategories",
            "storeCatalogBrands", "storeCatalogShipping", "storeCatalogPaymentMethods",
            "storeCatalogBillingOptions");

    private final CacheManager cacheManager;
    private final NotificacionService notificacionService;

    public StoreCatalogSyncService(CacheManager cacheManager, NotificacionService notificacionService) {
        this.cacheManager = cacheManager;
        this.notificacionService = notificacionService;
    }

    public void requestRefresh() {
        Long tenantId = TenantContext.getOrDefault();
        Runnable refresh = () -> {
            CACHE_NAMES.stream().map(cacheManager::getCache).forEach(this::clear);
            notificacionService.notificarCatalogoActualizado(tenantId);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refresh.run();
                }
            });
        } else {
            refresh.run();
        }
    }

    private void clear(Cache cache) {
        if (cache != null) cache.clear();
    }
}
