import { requireSession } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { renderBarList, renderStackedBar, renderLineChart } from '../components/charts.js';
import { showToast } from '../components/toast.js';
import { formatCurrency, formatInteger, escapeHtml } from '../core/format.js';
import { colorForPaymentMethod } from '../core/payment-colors.js';

function formatWeekday(isoDate) {
  const label = new Intl.DateTimeFormat('es-PE', { weekday: 'short' }).format(new Date(isoDate + 'T00:00:00'));
  return label.charAt(0).toUpperCase() + label.slice(1, 3);
}

const session = requireSession();
if (session) {
  renderShell('dashboard');
  cargarDashboard();
}

async function cargarDashboard() {
  try {
    const [dashboard, porCategoria, porVendedor, stockBajo] = await Promise.all([
      api.get('/reports/dashboard'),
      api.get('/reports/sales/by-category'),
      api.get('/reports/sales/by-seller'),
      api.get('/inventory/low-stock'),
    ]);

    document.querySelector('#stat-sales-today').textContent = formatCurrency(dashboard.salesToday.total);
    document.querySelector('#stat-sales-today-count').textContent = `${dashboard.salesToday.count} ventas hoy`;

    document.querySelector('#stat-sales-month').textContent = formatCurrency(dashboard.salesMonth.total);
    document.querySelector('#stat-sales-month-count').textContent = `${dashboard.salesMonth.count} ventas este mes`;

    document.querySelector('#stat-products-sold').textContent = formatInteger(dashboard.productsSoldToday);
    document.querySelector('#stat-low-stock').textContent = formatInteger(dashboard.lowStockCount);
    document.querySelector('#stat-out-of-stock').textContent = formatInteger(dashboard.outOfStockCount);

    renderLineChart(
      document.querySelector('#chart-sales-by-day'),
      dashboard.salesByDay.map((d) => ({ day: formatWeekday(d.date), total: d.total })),
      { xKey: 'day', yKey: 'total' }
    );

    const pagosConColor = dashboard.paymentBreakdown.map((p, i) => ({ ...p, color: colorForPaymentMethod(p.label, i) }));
    if (pagosConColor.length > 0) {
      renderStackedBar(document.querySelector('#chart-payment-methods'), pagosConColor, {
        valueKey: 'total',
        labelKey: 'label',
        colorKey: 'color',
      });
    } else {
      document.querySelector('#chart-payment-methods').innerHTML =
        '<div class="empty-state"><span>Sin ventas hoy todavía</span></div>';
    }

    renderChartOrEmpty('#chart-sales-by-category', porCategoria, 'label', 'total');
    renderChartOrEmpty('#chart-sales-by-seller', porVendedor, 'label', 'total');

    const topProducts = dashboard.topProducts.map((p) => ({ name: p.productName, total: p.total }));
    renderChartOrEmpty('#chart-top-products', topProducts, 'name', 'total');

    const lowStockBody = document.querySelector('#low-stock-body');
    if (stockBajo.length === 0) {
      lowStockBody.innerHTML = '<tr><td colspan="4" class="empty-state">Sin variantes en stock bajo</td></tr>';
    } else {
      lowStockBody.innerHTML = stockBajo
        .slice(0, 8)
        .map(
          (item) => `
          <tr>
            <td data-label="Producto">${escapeHtml(item.productName)}</td>
            <td data-label="Variante">${escapeHtml(item.variantLabel)}</td>
            <td data-label="Stock"><strong style="color: var(--color-warning-text);">${item.stock}</strong></td>
            <td data-label="Mínimo">${item.minStock}</td>
          </tr>
        `
        )
        .join('');
    }

    if (sessionStorage.getItem('fsp.welcome-shown') !== '1') {
      showToast({ type: 'success', title: 'Sesión iniciada', message: `Bienvenido, ${session.user.fullName.split(' ')[0]}.` });
      sessionStorage.setItem('fsp.welcome-shown', '1');
    }
  } catch (error) {
    const message = error instanceof ApiError ? error.message : 'No se pudo cargar el dashboard';
    showToast({ type: 'danger', title: 'Error al cargar datos', message });
  }
}

function renderChartOrEmpty(selector, items, labelKey, valueKey) {
  const container = document.querySelector(selector);
  if (!items || items.length === 0) {
    container.innerHTML = '<div class="empty-state"><span>Sin datos en este período</span></div>';
    return;
  }
  renderBarList(container, items, { valueKey, labelKey });
}
