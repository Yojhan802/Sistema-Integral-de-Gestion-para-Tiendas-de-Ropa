package com.freestyleperu.aplicacion.shared.cache;

import com.freestyleperu.aplicacion.shared.security.TenantContext;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

/**
 * Generador de clave de caché consciente del tenant — sin esto, dos negocios pidiendo, por
 * ejemplo, la página 0 del catálogo (mismos argumentos de método) compartirían la misma entrada
 * de Caffeine y un tenant vería el catálogo de otro (fuga real, ver Fase 3 del plan de
 * multi-tenant). Se referencia explícitamente vía {@code @Cacheable(keyGenerator =
 * "tenantAwareKeyGenerator")} en cada método que lo necesita — no se registra como el
 * {@code KeyGenerator} por defecto de toda la app para no afectar en silencio cachés ya
 * tenant-seguras por diseño (ej. {@code TenantSlugResolver}, cuya clave ya es el slug, único
 * globalmente, y se resuelve ANTES de que exista un tenant fijado).
 */
@Component("tenantAwareKeyGenerator")
public class TenantAwareKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        // Arrays.asList (no List.of): varios métodos cacheados aquí reciben filtros opcionales
        // que llegan como null (ej. categoryId/brandId sin elegir), y List.of no admite nulls.
        List<Object> key = new ArrayList<>(params.length + 1);
        key.add(TenantContext.getOrDefault());
        key.addAll(Arrays.asList(params));
        return key;
    }
}
