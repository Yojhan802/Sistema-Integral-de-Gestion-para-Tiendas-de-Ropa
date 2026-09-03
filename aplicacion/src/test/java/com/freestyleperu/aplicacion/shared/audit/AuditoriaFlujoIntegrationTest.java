package com.freestyleperu.aplicacion.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freestyleperu.aplicacion.shared.audit.dto.response.AuditLogResponse;
import com.freestyleperu.aplicacion.shared.audit.dto.response.AuditLogResumenResponse;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditoriaFlujoIntegrationTest {

    @Autowired private AuditoriaService auditoriaService;
    @Autowired private AuditLogRepository auditLogRepository;

    @Test
    void filtraPorUsuarioAccionEntidadResultadoYRangoDeFechas() {
        Long entryId = seed(10L, "carlos", "PRODUCTO_CREADO", "PRODUCTO", 55L, AuditResult.SUCCESS, LocalDateTime.now().minusDays(1));
        seed(10L, "carlos", "PRODUCTO_ELIMINADO", "PRODUCTO", 55L, AuditResult.DENIED, LocalDateTime.now().minusDays(1));
        seed(20L, "ana", "VENTA_ANULADA", "VENTA", 900L, AuditResult.SUCCESS, LocalDateTime.now().minusDays(10));

        // Filtro por usuario.
        PageResponse<AuditLogResumenResponse> porUsuario = auditoriaService.listar(10L, null, null, null, null, null, PageRequest.of(0, 20));
        assertThat(porUsuario.content()).hasSize(2).allMatch(e -> e.userId().equals(10L));

        // Filtro por acción (parcial), combinado con usuario para no depender de que
        // esta sea la única acción "*CREADO*" en una base compartida entre pruebas.
        PageResponse<AuditLogResumenResponse> porAccion = auditoriaService.listar(10L, "CREADO", null, null, null, null, PageRequest.of(0, 20));
        assertThat(porAccion.content()).extracting("action").containsExactly("PRODUCTO_CREADO");

        // Filtro por entidad, combinado con usuario — otras pruebas del mismo suite (ventas,
        // devoluciones, pedidos, reservas) también generan entradas reales entity=VENTA en esta
        // misma base compartida, ahora que AuditService.write() persiste de verdad en REQUIRES_NEW.
        PageResponse<AuditLogResumenResponse> porEntidad = auditoriaService.listar(20L, null, "VENTA", null, null, null, PageRequest.of(0, 20));
        assertThat(porEntidad.content()).hasSize(1);
        assertThat(porEntidad.content().get(0).entity()).isEqualTo("VENTA");

        // Filtro por resultado, combinado con usuario — otras pruebas (403 reales,
        // logins fallidos) también generan entradas DENIED en esta misma base compartida.
        PageResponse<AuditLogResumenResponse> porResultado = auditoriaService.listar(10L, null, null, AuditResult.DENIED, null, null, PageRequest.of(0, 20));
        assertThat(porResultado.content()).hasSize(1);
        assertThat(porResultado.content().get(0).result()).isEqualTo(AuditResult.DENIED);

        // Filtro por rango de fechas (excluye el de hace 10 días). El límite superior
        // se recorta una hora para no incluir entradas "de ahora" de otras pruebas.
        PageResponse<AuditLogResumenResponse> porFecha = auditoriaService.listar(
                10L, null, null, null, LocalDateTime.now().minusDays(3), LocalDateTime.now().minusHours(1), PageRequest.of(0, 20));
        assertThat(porFecha.content()).hasSize(2);

        // Detalle completo por id.
        AuditLogResponse detalle = auditoriaService.obtener(entryId);
        assertThat(detalle.action()).isEqualTo("PRODUCTO_CREADO");
        assertThat(detalle.username()).isEqualTo("carlos");

        assertThatThrownBy(() -> auditoriaService.obtener(999999L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    private Long seed(Long userId, String username, String action, String entity, Long entityId, AuditResult result, LocalDateTime createdAt) {
        AuditLog log = AuditLog.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .entity(entity)
                .entityId(entityId)
                .result(result)
                .createdAt(createdAt)
                .build();
        return auditLogRepository.save(log).getId();
    }
}
