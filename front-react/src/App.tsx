import { useEffect, useState } from 'react';
import { TemplateProvider, resolveStorePage } from './components/TemplateProvider';
import { StoreHomePage } from './pages/StoreHomePage';
import { ProductPage } from './pages/ProductPage';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { CustomerAuthPage } from './pages/CustomerAuthPage';
import { OrdersPage } from './pages/OrdersPage';
import { ComplaintBookPage } from './pages/ComplaintBookPage';
import { LegalPage } from './pages/LegalPage';
import { AdminDashboardPage, AdminLoginPage, AdminModulePage } from './pages/AdminPagesV2';
import { AdminChangePasswordPage } from './pages/AdminChangePasswordPage';
import { AdminConfigPage } from './pages/AdminConfigPage';
import { AdminProductsPage } from './pages/AdminProductsPage';
import { AdminProductDetailPage } from './pages/AdminProductDetailPage';
import { AdminInventoryPage } from './pages/AdminInventoryPage';
import { AdminCashPage } from './pages/AdminCashPage';
import { AdminPosPageV2 } from './pages/AdminPosPageV2';
import { AdminElectronicDocumentsPage } from './pages/AdminElectronicDocumentsPage';
import { AdminComplaintsPage } from './pages/AdminComplaintsPage';
import { AdminCustomersPage } from './pages/AdminCustomersPage';
import { AdminOrdersPage } from './pages/AdminOrdersPage';
import { AdminReservationsPage } from './pages/AdminReservationsPage';
import { AdminCombosPage } from './pages/AdminCombosPage';
import { AdminPromotionsPage } from './pages/AdminPromotionsPage';
import { AdminReportsPage } from './pages/AdminReportsPage';
import { AdminAuditPage } from './pages/AdminAuditPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { AdminRolesPage } from './pages/AdminRolesPage';
import { AdminCatalogPage } from './pages/AdminCatalogPage';
import { AdminCompaniesPage } from './pages/AdminCompaniesPage';
import { StoreStatusPage } from './pages/StoreStatusPage';
import { RouteTransition, TemplateMotion } from './templates/TemplateMotion';
import { ToastHost } from './components/ToastHost';

const LEGAL_PATHS = ['/terminos-condiciones', '/politica-privacidad', '/cambios-devoluciones', '/politica-cookies'];

/**
 * Título del documento por ruta. El legado lo tenía por HTML estático y el rewrite lo
 * había perdido: sin esto toda la tienda comparte un único título en la pestaña, en el
 * historial y en el resultado de búsqueda.
 */
const PAGE_TITLES: Array<[string, string]> = [
  ['/producto', 'Producto'],
  ['/carrito', 'Carrito'],
  ['/checkout', 'Finalizar compra'],
  ['/cuenta/login', 'Ingresar'],
  ['/cuenta/registro', 'Crear cuenta'],
  ['/cuenta/pedidos', 'Mis pedidos'],
  ['/libro-reclamaciones', 'Libro de Reclamaciones'],
  ['/reclamos', 'Libro de Reclamaciones'],
  ['/terminos-condiciones', 'Términos y Condiciones'],
  ['/politica-privacidad', 'Política de Privacidad'],
  ['/cambios-devoluciones', 'Cambios y Devoluciones'],
  ['/politica-cookies', 'Política de Cookies'],
  ['/plataforma/empresas', 'Empresas'],
];

function applyDocumentTitle(pathname: string) {
  const path = pathname.replace(/\/$/, '') || '/';
  if (path.startsWith('/admin') || path.endsWith('.html')) return;
  const match = PAGE_TITLES.find(([prefix]) => path === prefix);
  const brand = document.body.dataset.storeBrand || 'Qynex';
  document.title = match ? `${match[1]} · ${brand}` : brand;
}

function route(pathname: string) {
  const path = pathname.replace(/\/$/, '') || '/';
  if (path === '/producto' || path === '/tienda/producto.html') return <ProductPage />;
  if (path === '/carrito' || path === '/tienda/carrito.html') return <CartPage />;
  if (path === '/checkout' || path === '/tienda/checkout.html') return <CheckoutPage />;
  if (path === '/cuenta/login' || path === '/tienda/cuenta/login.html') return <CustomerAuthPage />;
  if (path === '/cuenta/registro' || path === '/tienda/cuenta/registro.html') return <CustomerAuthPage register />;
  if (path === '/cuenta/pedidos' || path === '/tienda/cuenta/pedidos.html') return <OrdersPage />;
  if (path === '/libro-reclamaciones' || path === '/reclamos') return <ComplaintBookPage />;
  if (LEGAL_PATHS.includes(path)) return <LegalPage path={path} />;
  if (path === '/suspendido.html') return <StoreStatusPage suspended />;
  if (path === '/no-disponible.html' || path === '/tienda/no-disponible.html') return <StoreStatusPage />;
  if (path === '/admin/login') return <AdminLoginPage />;
  if (path === '/login.html') return <AdminLoginPage />;
  if (path === '/admin/cambiar-contrasena') return <AdminChangePasswordPage />;
  if (path === '/cambiar-contrasena.html') return <AdminChangePasswordPage />;
  if (path === '/admin/dashboard') return <AdminDashboardPage />;
  if (path === '/admin/configuracion') return <AdminConfigPage />;
  if (path === '/admin/productos' || path === '/productos.html') return <AdminProductsPage />;
  if (path === '/admin/producto-detalle' || path === '/producto-detalle.html') return <AdminProductDetailPage />;
  if (path === '/admin/inventario' || path === '/inventario.html') return <AdminInventoryPage />;
  if (path === '/admin/caja' || path === '/caja.html') return <AdminCashPage />;
  if (path === '/admin/pos' || path === '/pos.html') return <AdminPosPageV2 />;
  if (path === '/admin/comprobantes' || path === '/comprobantes.html') return <AdminElectronicDocumentsPage />;
  if (path === '/admin/reclamos') return <AdminComplaintsPage />;
  if (path === '/admin/clientes' || path === '/clientes.html') return <AdminCustomersPage />;
  if (path === '/admin/pedidos' || path === '/pedidos.html') return <AdminOrdersPage />;
  if (path === '/admin/separaciones' || path === '/separaciones.html') return <AdminReservationsPage />;
  if (path === '/admin/combos' || path === '/combos.html') return <AdminCombosPage />;
  if (path === '/admin/promociones' || path === '/promociones.html') return <AdminPromotionsPage />;
  if (path === '/admin/reportes' || path === '/reportes.html') return <AdminReportsPage />;
  if (path === '/admin/auditoria' || path === '/auditoria.html') return <AdminAuditPage />;
  if (path === '/admin/usuarios' || path === '/usuarios.html') return <AdminUsersPage />;
  if (path === '/admin/roles' || path === '/roles.html') return <AdminRolesPage />;
  if (path === '/admin/catalogo' || path === '/catalogo.html') return <AdminCatalogPage />;
  if (path === '/plataforma' || path === '/plataforma/empresas') return <AdminCompaniesPage />;
  if (path === '/admin/empresas' || path === '/empresas.html') return <AdminCompaniesPage />;
  if (path === '/dashboard.html') return <AdminDashboardPage />;
  if (path.startsWith('/plataforma')) return <AdminCompaniesPage />;
  if (path.startsWith('/admin/')) return <AdminModulePage title={path.split('/').pop()?.replaceAll('-', ' ') || 'Módulo'} />;
  return <StoreHomePage />;
}

export function App() { const [location, setLocation] = useState(window.location.href); useEffect(() => { const update = () => { setLocation(window.location.href); document.body.dataset.storePage = resolveStorePage(window.location.pathname); applyDocumentTitle(window.location.pathname); window.scrollTo({ top: 0, behavior: 'auto' }); }; update(); window.addEventListener('popstate', update); window.addEventListener('qynex-brand-ready', update); return () => { window.removeEventListener('popstate', update); window.removeEventListener('qynex-brand-ready', update); }; }, []); return <TemplateProvider><TemplateMotion><RouteTransition routeKey={location}>{route(window.location.pathname)}</RouteTransition></TemplateMotion><ToastHost /></TemplateProvider>; }
