package com.freestyleperu.aplicacion.plataforma.service;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.plataforma.domain.ModuloSistema;
import com.freestyleperu.aplicacion.plataforma.domain.TenantModule;
import com.freestyleperu.aplicacion.plataforma.domain.TenantModuleChange;
import com.freestyleperu.aplicacion.plataforma.dto.request.ActualizarModulosRequest;
import com.freestyleperu.aplicacion.plataforma.dto.response.CambioPaqueteResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.CatalogoModulosResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.ModuloResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.ModulosTenantResponse;
import com.freestyleperu.aplicacion.plataforma.repository.TenantModuleChangeRepository;
import com.freestyleperu.aplicacion.plataforma.repository.TenantModuleRepository;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestiona qué módulos tiene contratados cada empresa y a qué precio. */
@Service
@Transactional(readOnly = true)
public class PlatformModuleService {

    private static final int HISTORIAL_VISIBLE = 20;

    private final TenantModuleRepository repository;
    private final TenantModuleChangeRepository changeRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final ModuloGate moduloGate;

    public PlatformModuleService(TenantModuleRepository repository, TenantModuleChangeRepository changeRepository,
            CompanySettingsRepository companySettingsRepository, ModuloGate moduloGate) {
        this.repository = repository;
        this.changeRepository = changeRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.moduloGate = moduloGate;
    }

    /** Últimos cambios de paquete, del más reciente al más antiguo. */
    public List<CambioPaqueteResponse> historial(Long tenantId) {
        return changeRepository
                .findAllByTenantIdOrderByChangedAtDescIdDesc(tenantId, PageRequest.of(0, HISTORIAL_VISIBLE)).stream()
                .map(cambio -> new CambioPaqueteResponse(cambio.getChangedAt(), cambio.getChangedByUsername(),
                        cambio.getPreviousTotal(), cambio.getNewTotal(),
                        nombresDe(cambio.getAdded()), nombresDe(cambio.getRemoved())))
                .toList();
    }

    /** Los códigos se guardan separados por coma; al leer se traducen a nombres visibles. */
    private static List<String> nombresDe(String codigos) {
        if (codigos == null || codigos.isBlank()) {
            return List.of();
        }
        return Arrays.stream(codigos.split(","))
                .map(String::trim)
                .filter(codigo -> !codigo.isBlank())
                .map(codigo -> {
                    try {
                        return ModuloSistema.valueOf(codigo).getNombre();
                    } catch (IllegalArgumentException moduloRetirado) {
                        // Un módulo que ya no existe en el catálogo no debe romper el historial.
                        return codigo;
                    }
                })
                .toList();
    }

    /** Catálogo completo, sin nada contratado: para elegir el paquete al dar de alta. */
    public CatalogoModulosResponse catalogo() {
        ModulosTenantResponse vacio = armarRespuesta(null, EnumSet.noneOf(ModuloSistema.class),
                new EnumMap<>(ModuloSistema.class));
        return new CatalogoModulosResponse(vacio.modulos(), vacio.presets());
    }

    public ModulosTenantResponse obtener(Long tenantId) {
        CompanySettings empresa = companySettingsRepository.findById(tenantId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Empresa", tenantId));
        List<TenantModule> filas = repository.findAllByTenantId(tenantId);
        Map<ModuloSistema, BigDecimal> precios = filas.stream()
                .collect(Collectors.toMap(TenantModule::getModule, TenantModule::getMonthlyPrice,
                        (a, b) -> a, () -> new EnumMap<>(ModuloSistema.class)));
        Set<ModuloSistema> contratados = precios.isEmpty()
                ? EnumSet.noneOf(ModuloSistema.class)
                : EnumSet.copyOf(precios.keySet());
        return armarRespuesta(empresa, contratados, precios);
    }

    /**
     * Guarda el conjunto pedido tras cerrarlo: se añaden las dependencias que falten y el
     * Libro de Reclamaciones si la empresa vende por internet. Lo que no venga en la
     * petición se da de baja, así que esta llamada define el estado completo.
     */
    @Transactional
    public ModulosTenantResponse actualizar(Long tenantId, ActualizarModulosRequest request) {
        return actualizar(tenantId, request, null, null);
    }

    @Transactional
    public ModulosTenantResponse actualizar(Long tenantId, ActualizarModulosRequest request,
            Long actorId, String actorUsername) {
        CompanySettings empresa = companySettingsRepository.findById(tenantId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Empresa", tenantId));

        Map<ModuloSistema, BigDecimal> preciosPedidos = new EnumMap<>(ModuloSistema.class);
        request.modulos().forEach(seleccion -> preciosPedidos.put(seleccion.code(), seleccion.precioMensual()));
        Set<ModuloSistema> finales = ModuloSistema.cerrarDependencias(
                preciosPedidos.isEmpty() ? EnumSet.noneOf(ModuloSistema.class) : EnumSet.copyOf(preciosPedidos.keySet()));

        Map<ModuloSistema, BigDecimal> preciosPrevios = repository.findAllByTenantId(tenantId).stream()
                .collect(Collectors.toMap(TenantModule::getModule, TenantModule::getMonthlyPrice, (a, b) -> a,
                        () -> new EnumMap<>(ModuloSistema.class)));
        Map<ModuloSistema, TenantModule> existentes = repository.findAllByTenantId(tenantId).stream()
                .collect(Collectors.toMap(TenantModule::getModule, fila -> fila, (a, b) -> a,
                        () -> new EnumMap<>(ModuloSistema.class)));
        LocalDateTime ahora = LocalDateTime.now();

        existentes.entrySet().stream()
                .filter(entrada -> !finales.contains(entrada.getKey()))
                .forEach(entrada -> repository.delete(entrada.getValue()));

        Map<ModuloSistema, BigDecimal> preciosFinales = new EnumMap<>(ModuloSistema.class);
        for (ModuloSistema modulo : finales) {
            // Lo que no se pidió explícitamente entró por cierre de dependencias, así que
            // va a cero: el cliente paga el módulo que contrató, no la infraestructura que
            // ese módulo necesita. Conservar el precio anterior inflaba el paquete al
            // recortarlo, que es justo lo contrario de lo que busca el operador.
            BigDecimal precio = preciosPedidos.getOrDefault(modulo, BigDecimal.ZERO);
            TenantModule fila = existentes.get(modulo);
            if (fila == null) {
                fila = new TenantModule(tenantId, modulo, precio);
            } else {
                fila.setMonthlyPrice(precio);
                fila.setUpdatedAt(ahora);
            }
            repository.save(fila);
            preciosFinales.put(modulo, precio);
        }

        registrarCambio(tenantId, preciosPrevios, preciosFinales, actorId, actorUsername, ahora);
        moduloGate.invalidar(tenantId);
        return armarRespuesta(empresa, finales, preciosFinales);
    }

    /**
     * Deja constancia solo si el paquete cambió de verdad: guardar una fila por cada vez
     * que alguien abre y guarda sin tocar nada llenaría el historial de ruido.
     */
    private void registrarCambio(Long tenantId, Map<ModuloSistema, BigDecimal> antes,
            Map<ModuloSistema, BigDecimal> despues, Long actorId, String actorUsername, LocalDateTime cuando) {
        if (mismoPaquete(antes, despues)) {
            return;
        }
        TenantModuleChange cambio = new TenantModuleChange();
        cambio.setTenantId(tenantId);
        cambio.setChangedAt(cuando);
        cambio.setChangedBy(actorId);
        cambio.setChangedByUsername(actorUsername);
        cambio.setPreviousTotal(suma(antes));
        cambio.setNewTotal(suma(despues));
        cambio.setAdded(codigos(despues.keySet(), antes.keySet()));
        cambio.setRemoved(codigos(antes.keySet(), despues.keySet()));
        cambio.setModules(codigos(despues.keySet(), Set.of()));
        changeRepository.save(cambio);
    }

    /**
     * Compara los importes con {@code compareTo} y no con {@code equals}: para BigDecimal
     * 25.0 y 25.00 no son iguales, y el precio vuelve de la base con la escala de la
     * columna. Con {@code equals} cualquier guardado sin cambios dejaba una entrada nueva.
     */
    private static boolean mismoPaquete(Map<ModuloSistema, BigDecimal> antes, Map<ModuloSistema, BigDecimal> despues) {
        if (!antes.keySet().equals(despues.keySet())) {
            return false;
        }
        return antes.entrySet().stream()
                .allMatch(entrada -> entrada.getValue().compareTo(despues.get(entrada.getKey())) == 0);
    }

    private static BigDecimal suma(Map<ModuloSistema, BigDecimal> precios) {
        return precios.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String codigos(Set<ModuloSistema> conjunto, Set<ModuloSistema> excluir) {
        String valor = conjunto.stream().filter(modulo -> !excluir.contains(modulo))
                .map(Enum::name).collect(Collectors.joining(","));
        return valor.isBlank() ? null : valor;
    }

    private ModulosTenantResponse armarRespuesta(CompanySettings empresa, Set<ModuloSistema> contratados,
            Map<ModuloSistema, BigDecimal> precios) {
        Set<ModuloSistema> efectivos = contratados.isEmpty()
                ? EnumSet.noneOf(ModuloSistema.class)
                : ModuloSistema.cerrarDependencias(contratados);
        Set<ModuloSistema> forzadosPorLey = ModuloSistema.forzadosPor(efectivos);

        List<ModuloResponse> modulos = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ModuloSistema modulo : ModuloSistema.values()) {
            boolean activo = efectivos.contains(modulo);
            BigDecimal precio = precios.getOrDefault(modulo, BigDecimal.ZERO);
            if (activo) {
                total = total.add(precio);
            }
            Set<ModuloSistema> dependientes = activo
                    ? ModuloSistema.dependientesActivos(modulo, efectivos)
                    : EnumSet.noneOf(ModuloSistema.class);
            String motivo = motivoBloqueo(modulo, activo, dependientes, forzadosPorLey);
            // Está aquí porque otro módulo contratado lo necesita, no porque se haya
            // vendido: el panel lo muestra como incluido y no lo cobra aparte.
            boolean porDependencia = activo && !dependientes.isEmpty();
            modulos.add(new ModuloResponse(modulo, modulo.getNombre(), modulo.getDescripcion(), modulo.getTipo(),
                    activo, porDependencia, motivo != null, motivo,
                    precio, modulo.getPrecioLista(), modulo.getRequiere()));
        }

        Map<Plan, List<ModuloSistema>> presets = new HashMap<>();
        Arrays.stream(Plan.values()).forEach(plan -> presets.put(plan, List.copyOf(ModuloSistema.delPlan(plan))));

        return new ModulosTenantResponse(empresa == null ? null : empresa.getId(),
                empresa == null ? null : empresa.getName(), empresa == null ? null : empresa.getPlan(),
                modulos, total, presets);
    }

    private String motivoBloqueo(ModuloSistema modulo, boolean activo, Set<ModuloSistema> dependientes,
            Set<ModuloSistema> forzadosPorLey) {
        if (modulo.getTipo() == ModuloSistema.Tipo.NUCLEO) {
            return "El sistema no funciona sin este módulo";
        }
        if (!activo) {
            return null;
        }
        if (forzadosPorLey.contains(modulo)) {
            return "Obligatorio por ley mientras la empresa venda por internet";
        }
        if (!dependientes.isEmpty()) {
            return "Lo necesitan: " + dependientes.stream().map(ModuloSistema::getNombre)
                    .collect(Collectors.joining(", "));
        }
        return null;
    }
}
