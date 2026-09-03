package com.freestyleperu.aplicacion.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.catalogo.repository.CategoryRepository;
import com.freestyleperu.aplicacion.inventario.domain.Branch;
import com.freestyleperu.aplicacion.inventario.domain.MovementType;
import com.freestyleperu.aplicacion.inventario.domain.Warehouse;
import com.freestyleperu.aplicacion.inventario.dto.request.AjusteInventarioRequest;
import com.freestyleperu.aplicacion.inventario.dto.request.EntradaInventarioRequest;
import com.freestyleperu.aplicacion.inventario.dto.request.SalidaInventarioRequest;
import com.freestyleperu.aplicacion.inventario.dto.response.MovimientoResponse;
import com.freestyleperu.aplicacion.inventario.repository.BranchRepository;
import com.freestyleperu.aplicacion.inventario.repository.InventoryMovementRepository;
import com.freestyleperu.aplicacion.inventario.repository.WarehouseRepository;
import com.freestyleperu.aplicacion.inventario.service.InventarioService;
import com.freestyleperu.aplicacion.producto.AtributoTestFixture;
import com.freestyleperu.aplicacion.producto.dto.request.CrearProductoRequest;
import com.freestyleperu.aplicacion.producto.dto.request.CrearVarianteRequest;
import com.freestyleperu.aplicacion.producto.dto.response.ProductoDetalleResponse;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteResponse;
import com.freestyleperu.aplicacion.producto.service.ProductoService;
import com.freestyleperu.aplicacion.producto.service.VarianteService;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.exception.StockInsuficienteException;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.repository.RolRepository;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventarioFlujoIntegrationTest {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AtributoTestFixture atributos;
    @Autowired private BranchRepository branchRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProductoService productoService;
    @Autowired private VarianteService varianteService;
    @Autowired private InventarioService inventarioService;
    @Autowired private InventoryMovementRepository movementRepository;

    @Test
    void registraMovimientosYMantieneElStockConsistenteConSuHistorial() {
        Branch branch = nuevaSucursal();
        nuevoAlmacen(branch);
        Long userId = nuevoUsuario().getId();

        Category categoria = nuevaCategoria("Casacas");
        AttributeValue negro = atributos.color("Negro");
        AttributeValue talleL = atributos.talla("L", (short) 4);

        ProductoDetalleResponse producto = productoService.crear(new CrearProductoRequest(
                null, null, "Casaca Denim", categoria.getId(), null, null, null, null, null,
                new BigDecimal("129.90"), null));
        VarianteResponse variante = varianteService.crear(producto.id(),
                new CrearVarianteRequest(List.of(negro.getId(), talleL.getId()), null, null, 0, 2, false));

        // Entrada: 0 -> 20
        MovimientoResponse entrada = inventarioService.registrarEntrada(
                new EntradaInventarioRequest(variante.id(), null, 20, "Compra inicial"), userId);
        assertThat(entrada.type()).isEqualTo(MovementType.ENTRADA);
        assertThat(entrada.stockBefore()).isZero();
        assertThat(entrada.stockAfter()).isEqualTo(20);

        // Salida: 20 -> 15
        MovimientoResponse salida = inventarioService.registrarSalida(
                new SalidaInventarioRequest(variante.id(), null, 5, "Traslado a exhibición"), userId);
        assertThat(salida.stockAfter()).isEqualTo(15);

        // No se puede vender/salir más de lo disponible.
        assertThatThrownBy(() -> inventarioService.registrarSalida(
                new SalidaInventarioRequest(variante.id(), null, 999, "Salida imposible"), userId))
                .isInstanceOf(StockInsuficienteException.class);

        // Ajuste (recuento físico): 15 -> 18
        MovimientoResponse ajuste = inventarioService.registrarAjuste(
                new AjusteInventarioRequest(variante.id(), null, 18, "Recuento físico"), userId);
        assertThat(ajuste.type()).isEqualTo(MovementType.AJUSTE);
        assertThat(ajuste.quantity()).isEqualTo(3);
        assertThat(ajuste.stockAfter()).isEqualTo(18);

        // Ajustar al mismo valor actual debe rechazarse (no hay nada que ajustar).
        assertThatThrownBy(() -> inventarioService.registrarAjuste(
                new AjusteInventarioRequest(variante.id(), null, 18, "Sin cambios"), userId))
                .isInstanceOf(ReglaDeNegocioException.class);

        // Invariante: la suma de todos los movimientos debe igualar el stock final.
        long sumaMovimientos = movementRepository.sumaMovimientos(variante.id());
        assertThat(sumaMovimientos).isEqualTo(18);

        var historial = inventarioService.listarMovimientos(variante.id(), null, null, null, PageRequest.of(0, 10));
        assertThat(historial.totalElements()).isEqualTo(3);
        assertThat(historial.content().get(0).type()).isEqualTo(MovementType.AJUSTE); // más reciente primero

        var stockBajo = inventarioService.listarStockBajo();
        assertThat(stockBajo).noneMatch(item -> item.variantId().equals(variante.id())); // 18 > minStock(2)
    }

    private Branch nuevaSucursal() {
        Branch branch = new Branch();
        branch.setCode("SUC-TEST");
        branch.setName("Sucursal de prueba");
        return branchRepository.save(branch);
    }

    private Warehouse nuevoAlmacen(Branch branch) {
        Warehouse warehouse = new Warehouse();
        warehouse.setBranch(branch);
        warehouse.setCode("ALM-TEST");
        warehouse.setName("Almacén de prueba");
        return warehouseRepository.save(warehouse);
    }

    private Usuario nuevoUsuario() {
        Rol rol = new Rol();
        rol.setCode("TEST_ROL");
        rol.setName("Rol de prueba");
        rol.setSystem(false);
        rolRepository.save(rol);

        Usuario usuario = new Usuario();
        usuario.setUsername("almacenero.test");
        usuario.setPasswordHash("hash");
        usuario.setFullName("Almacenero de Prueba");
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setRoles(new HashSet<>(java.util.List.of(rol)));
        return usuarioRepository.save(usuario);
    }

    private Category nuevaCategoria(String nombre) {
        Category category = new Category();
        category.setName(nombre);
        category.setSlug(nombre.toLowerCase());
        return categoryRepository.save(category);
    }

}
