import { storeApi, ApiError, API_ORIGIN } from './core/store-api.js';
import { requireCustomerSession } from './core/customer-auth.js';
import { renderStoreShell } from './components/store-shell.js';
import { getCart, cartTotal, clearCart } from './core/cart.js';
import { getDepartamentos, getProvincias, getDistritos } from './core/ubigeo.js';
import { renderStoreSteps } from './components/store-steps.js';
import { formatCurrency, escapeHtml } from '../../../js/core/format.js';
import { showToast } from '../../../js/components/toast.js';

const CODIGO_CONTRAENTREGA = 'CONTRAENTREGA';

/** Debe coincidir con TERMS_VERSION de la tienda React: identifica qué texto se aceptó. */
const TERMS_VERSION = '2026-09';

let metodosPagoTodos = [];
let proveedoresPago = [];
let metodoSeleccionado = null;
let proveedorSeleccionado = null;
let shippingInfo = { flatRate: 0, freeShippingDistrict: null };
let distritoSeleccionadoNombre = null;
let culqiSdkPromise = null;
let niubizSdkPromises = new Map();
let izipaySdkPromises = new Map();
let sessionActual = null;

function metodosPagoVisibles() {
  const esHuacho = distritoSeleccionadoNombre?.toLowerCase() === shippingInfo.freeShippingDistrict?.toLowerCase();
  return metodosPagoTodos.filter((m) => m.code !== CODIGO_CONTRAENTREGA || esHuacho);
}

function calcularEnvio() {
  if (metodoSeleccionado?.code === CODIGO_CONTRAENTREGA) return 0;
  return shippingInfo.flatRate;
}

function render(session) {
  const contenedor = document.querySelector('#checkout-content');

  contenedor.innerHTML = `
    <form id="checkout-form" class="store-checkout-form">
      <p class="field-hint store-checkout-intro">Datos de quien recibe el envío — la courier los exige para registrar el paquete.</p>
      <div class="form-grid store-checkout-recipient-grid">
        <div class="field">
          <label class="field-label" for="recipientDni">DNI</label>
          <input class="input" id="recipientDni" name="recipientDni" required maxlength="15" inputmode="numeric" />
        </div>
        <div class="field">
          <label class="field-label" for="recipientFirstName">Nombres</label>
          <input class="input" id="recipientFirstName" name="recipientFirstName" required maxlength="100" value="${session.customer.fullName?.split(' ')[0] ?? ''}" />
        </div>
        <div class="field">
          <label class="field-label" for="recipientLastNamePaterno">Apellido paterno</label>
          <input class="input" id="recipientLastNamePaterno" name="recipientLastNamePaterno" required maxlength="60" />
        </div>
        <div class="field">
          <label class="field-label" for="recipientLastNameMaterno">Apellido materno</label>
          <input class="input" id="recipientLastNameMaterno" name="recipientLastNameMaterno" required maxlength="60" />
        </div>
      </div>
      <div class="field store-checkout-field">
        <label class="field-label" for="phone">Teléfono</label>
        <input class="input" id="phone" name="phone" required value="${session.customer.phone ?? ''}" />
      </div>
      <div class="field store-checkout-field">
        <label class="field-label" for="address">Dirección</label>
        <input class="input" id="address" name="address" required placeholder="Av./Jr./Calle, número, referencia" />
      </div>

      <div class="form-grid store-checkout-location-grid">
        <div class="field">
          <label class="field-label" for="departamento">Departamento</label>
          <select class="select" id="departamento" required>
            <option value="">Selecciona…</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" for="provincia">Provincia</label>
          <select class="select" id="provincia" required disabled>
            <option value="">Elige un departamento primero</option>
          </select>
        </div>
        <div class="field field-span-2">
          <label class="field-label" for="distrito">Distrito</label>
          <select class="select" id="distrito" required disabled>
            <option value="">Elige una provincia primero</option>
          </select>
        </div>
      </div>

      <div class="field store-checkout-field store-checkout-notes">
        <label class="field-label" for="notes">Notas (opcional)</label>
        <textarea class="input" id="notes" name="notes" rows="2"></textarea>
      </div>

      <div class="field store-checkout-payment-section">
        <span class="field-label">Método de pago</span>
        <div id="payment-methods" class="store-payment-methods"></div>
        <div id="payment-detail"></div>
      </div>

      <div class="field store-checkout-field" id="reference-field" hidden>
        <label class="field-label" for="paymentReference">Número de operación</label>
        <input class="input" id="paymentReference" name="paymentReference" />
      </div>

      <div class="field store-checkout-field" id="proof-field" hidden>
        <label class="field-label" for="proofInput">Comprobante de pago</label>
        <input type="file" class="input" id="proofInput" accept="image/png,image/jpeg,image/webp" />
        <span class="field-hint">Opcional — si ya pagaste, súbelo para agilizar la confirmación. También puedes hacerlo después desde "Mis pedidos".</span>
      </div>

      <!-- Aceptación expresa de las condiciones: sin ella el backend rechaza el pedido,
           porque la contratación a distancia exige consentimiento informado. Los textos
           legales viven en la tienda React; aquí solo se recoge la aceptación. -->
      <div class="field store-checkout-field store-terms-field">
        <label class="store-terms-accept" for="acceptedTerms">
          <input type="checkbox" id="acceptedTerms" name="acceptedTerms" required />
          <span>He le&iacute;do y acepto los T&eacute;rminos y Condiciones, la Pol&iacute;tica de Privacidad y la Pol&iacute;tica de Cambios y Devoluciones.</span>
        </label>
      </div>

      <button class="btn btn-primary btn-lg btn-block" type="submit">Confirmar pedido</button>
    </form>

    <div class="store-summary store-checkout-summary" id="order-summary"></div>
  `;

  poblarUbigeo();
  renderMetodosPago();
  actualizarResumen();
  document.querySelector('#checkout-form').addEventListener('submit', enviarPedido);
}

async function poblarUbigeo() {
  const depSelect = document.querySelector('#departamento');
  const provSelect = document.querySelector('#provincia');
  const distSelect = document.querySelector('#distrito');

  const departamentos = await getDepartamentos();
  departamentos.forEach((d) => depSelect.insertAdjacentHTML('beforeend', `<option value="${d.id}">${d.nombre}</option>`));

  depSelect.addEventListener('change', async () => {
    provSelect.innerHTML = '<option value="">Selecciona…</option>';
    distSelect.innerHTML = '<option value="">Elige una provincia primero</option>';
    distSelect.disabled = true;
    distritoSeleccionadoNombre = null;
    if (!depSelect.value) {
      provSelect.disabled = true;
      provSelect.innerHTML = '<option value="">Elige un departamento primero</option>';
      renderMetodosPago();
      return;
    }
    const provincias = await getProvincias(depSelect.value);
    provincias.forEach((p) => provSelect.insertAdjacentHTML('beforeend', `<option value="${p.id}">${p.nombre}</option>`));
    provSelect.disabled = false;
    renderMetodosPago();
  });

  provSelect.addEventListener('change', async () => {
    distSelect.innerHTML = '<option value="">Selecciona…</option>';
    distritoSeleccionadoNombre = null;
    if (!provSelect.value) {
      distSelect.disabled = true;
      distSelect.innerHTML = '<option value="">Elige una provincia primero</option>';
      renderMetodosPago();
      return;
    }
    const distritos = await getDistritos(provSelect.value);
    distritos.forEach((d) => distSelect.insertAdjacentHTML('beforeend', `<option value="${d.nombre}">${d.nombre}</option>`));
    distSelect.disabled = false;
    renderMetodosPago();
  });

  distSelect.addEventListener('change', () => {
    distritoSeleccionadoNombre = distSelect.value || null;
    renderMetodosPago();
    actualizarResumen();
  });
}

function renderMetodosPago() {
  const contenedor = document.querySelector('#payment-methods');
  const visibles = metodosPagoVisibles();

  // Si el método elegido ya no está disponible (cambiaron de distrito), se deselecciona.
  if (metodoSeleccionado && !visibles.some((m) => m.id === metodoSeleccionado.id)) {
    metodoSeleccionado = null;
  }

  contenedor.innerHTML = visibles
    .map(
      (m) => `
    <label class="store-payment-option" data-selected="${metodoSeleccionado?.id === m.id}" data-method-id="${m.id}">
      <input class="store-payment-radio" type="radio" name="paymentMethod" value="${m.id}" ${metodoSeleccionado?.id === m.id ? 'checked' : ''} />
      <span>${m.name}${m.code === CODIGO_CONTRAENTREGA ? ' · envío gratis' : ''}</span>
    </label>
  `
    )
    .join('');

  contenedor.querySelectorAll('[data-method-id]').forEach((label) => {
    label.addEventListener('click', () => seleccionarMetodo(Number(label.dataset.methodId)));
  });

  if (!metodoSeleccionado) {
    document.querySelector('#payment-detail').innerHTML = '';
    document.querySelector('#reference-field').hidden = true;
    document.querySelector('#proof-field').hidden = true;
  }
  actualizarResumen();
}

function seleccionarMetodo(id) {
  metodoSeleccionado = metodosPagoTodos.find((m) => m.id === id);
  proveedorSeleccionado = metodoSeleccionado?.type === 'CARD'
    ? (proveedoresPago.find((p) => p.provider === proveedorSeleccionado?.provider) ?? proveedoresPago[0] ?? null)
    : null;
  document.querySelectorAll('#payment-methods [data-method-id]').forEach((label) => {
    const selected = Number(label.dataset.methodId) === id;
    label.dataset.selected = String(selected);
    label.querySelector('input').checked = selected;
  });

  const detalle = document.querySelector('#payment-detail');
  const providerDetail = metodoSeleccionado?.type === 'CARD' ? renderProviderSelection() : '';
  if (metodoSeleccionado?.qrImageUrl) {
    detalle.innerHTML = `
      <div class="store-qr-box">
        <img src="${API_ORIGIN}${metodoSeleccionado.qrImageUrl}" alt="QR de ${metodoSeleccionado.name}" />
        ${metodoSeleccionado.accountHolder ? `<span>${metodoSeleccionado.accountHolder}</span>` : ''}
        ${metodoSeleccionado.accountNumber ? `<span class="mono">${metodoSeleccionado.accountNumber}</span>` : ''}
      </div>
    `;
  } else if (metodoSeleccionado?.accountNumber) {
    detalle.innerHTML = `<p class="store-payment-account"><strong>${metodoSeleccionado.accountHolder ?? ''}</strong> — <span class="mono">${metodoSeleccionado.accountNumber}</span></p>`;
  } else {
    detalle.innerHTML = '';
  }

  if (providerDetail) {
    detalle.insertAdjacentHTML('afterbegin', providerDetail);
    detalle.querySelector('#payment-provider')?.addEventListener('change', (event) => {
      proveedorSeleccionado = proveedoresPago.find((p) => p.provider === event.target.value) ?? null;
    });
  }

  document.querySelector('#reference-field').hidden = !metodoSeleccionado?.requiresReference;
  document.querySelector('#proof-field').hidden = metodoSeleccionado?.type !== 'DIGITAL_WALLET';
  actualizarResumen();
}

function renderProviderSelection() {
  const labels = { NIUBIZ: 'Niubiz', CULQI: 'Culqi', IZIPAY: 'Izipay' };
  if (!proveedoresPago.length) {
    return '<p class="field-hint store-payment-empty">No hay una pasarela online disponible para esta empresa.</p>';
  }
  return `
    <div class="field store-payment-provider">
      <label class="field-label" for="payment-provider">Pasarela</label>
      <select class="select" id="payment-provider" required>
        ${proveedoresPago.map((provider) => `<option value="${provider.provider}" ${proveedorSeleccionado?.provider === provider.provider ? 'selected' : ''}>${labels[provider.provider] ?? provider.provider}</option>`).join('')}
      </select>
      <span class="field-hint">El pago se procesa con las credenciales configuradas por la empresa.</span>
    </div>
  `;
}

function actualizarResumen() {
  const items = getCart();
  const subtotal = cartTotal(items);
  const envio = calcularEnvio();
  const resumen = document.querySelector('#order-summary');
  if (!resumen) return;

  resumen.innerHTML = `
    <h3 class="store-summary-title">Resumen</h3>
    ${items
      .map(
        (it) => `
      <div class="store-summary-row">
        <span>${escapeHtml(it.productName)} (${escapeHtml(it.variantLabel)}) × ${it.quantity}</span>
        <span>${formatCurrency(it.unitPrice * it.quantity)}</span>
      </div>
    `
      )
      .join('')}
    <div class="store-summary-row"><span>Subtotal</span><span>${formatCurrency(subtotal)}</span></div>
    <div class="store-summary-row">
      <span>Envío${!metodoSeleccionado ? ' (estimado)' : ''}</span>
      <span>${envio === 0 ? 'Gratis' : formatCurrency(envio)}</span>
    </div>
    <div class="store-summary-total"><span>Total</span><span>${formatCurrency(subtotal + envio)}</span></div>
  `;
}

async function enviarPedido(event) {
  event.preventDefault();
  const form = event.target;
  const depSelect = document.querySelector('#departamento');
  const provSelect = document.querySelector('#provincia');
  const distSelect = document.querySelector('#distrito');

  if (!metodoSeleccionado) {
    showToast({ type: 'danger', title: 'Elige un método de pago' });
    return;
  }
  if (!depSelect.value || !provSelect.value || !distSelect.value) {
    showToast({ type: 'danger', title: 'Completa departamento, provincia y distrito' });
    return;
  }

  const items = getCart();
  if (!items.length) {
    showToast({ type: 'danger', title: 'Tu carrito está vacío' });
    return;
  }

  const submitButton = form.querySelector('button[type="submit"]');
  submitButton.disabled = true;

  try {
    const pedido = await storeApi.post(
      '/store/orders',
      {
        items: items.map((it) => ({ variantId: it.variantId, quantity: it.quantity })),
        paymentMethodId: metodoSeleccionado.id,
        paymentReference: form.paymentReference.value.trim() || null,
        recipientDni: form.recipientDni.value.trim(),
        recipientFirstName: form.recipientFirstName.value.trim(),
        recipientLastNamePaterno: form.recipientLastNamePaterno.value.trim(),
        recipientLastNameMaterno: form.recipientLastNameMaterno.value.trim(),
        phone: form.phone.value.trim(),
        address: form.address.value.trim(),
        department: depSelect.options[depSelect.selectedIndex].text,
        province: provSelect.options[provSelect.selectedIndex].text,
        district: distSelect.value,
        notes: form.notes.value.trim() || null,
        acceptedTerms: form.acceptedTerms.checked,
        termsVersion: TERMS_VERSION,
      },
      { auth: true }
    );

    if (metodoSeleccionado.type === 'CARD') {
      const resultadoPago = await cobrarConPasarela(pedido, sessionActual);
      if (resultadoPago?.status === 'PENDING') {
        showToast({ type: 'success', title: 'Pedido creado', message: 'El pago quedó pendiente de confirmación.' });
      }
    }

    const proofFile = document.querySelector('#proofInput')?.files?.[0];
    if (proofFile) {
      const formData = new FormData();
      formData.append('file', proofFile);
      try {
        await storeApi.post(`/store/orders/${pedido.id}/payment-proof`, formData, { auth: true });
      } catch {
        // El pedido ya se creó — si el comprobante falla al subir, el cliente lo agrega después desde "Mis pedidos".
        showToast({ type: 'danger', title: 'El pedido se creó, pero no se pudo subir el comprobante', message: 'Puedes agregarlo luego desde "Mis pedidos".' });
      }
    }

    clearCart();
    if (metodoSeleccionado.type !== 'CARD' || !document.querySelector('#checkout-form')?.dataset.paymentPending) {
      showToast({ type: 'success', title: 'Pedido creado' });
    }
    window.location.href = 'cuenta/pedidos.html';
  } catch (error) {
    showToast({ type: 'danger', title: 'No se pudo crear el pedido', message: error instanceof ApiError ? error.message : undefined });
  } finally {
    submitButton.disabled = false;
  }
}

async function cobrarConPasarela(pedido, session) {
  if (!proveedorSeleccionado) {
    throw new ApiError('Selecciona una pasarela de pago', 400, null);
  }
  if (!['CULQI', 'NIUBIZ', 'IZIPAY'].includes(proveedorSeleccionado.provider)) {
    throw new ApiError(
      `El checkout de ${proveedorSeleccionado.provider} todavÃ­a no estÃ¡ disponible en la tienda`,
      409,
      null
    );
  }

  const transaction = await storeApi.post(
    `/store/orders/${pedido.id}/payment-transactions`,
    { provider: proveedorSeleccionado.provider },
    {
      auth: true,
      headers: { 'Idempotency-Key': `store-order-${pedido.id}-${proveedorSeleccionado.provider}` },
    }
  );
  const sourceId = proveedorSeleccionado.provider === 'CULQI'
    ? await obtenerTokenCulqi(transaction, proveedorSeleccionado, session)
    : proveedorSeleccionado.provider === 'NIUBIZ'
      ? await obtenerTokenNiubiz(transaction, proveedorSeleccionado, session)
      : await obtenerTokenIzipay(transaction, proveedorSeleccionado, session);
  const resultado = await storeApi.post(
    `/store/payment-transactions/${transaction.id}/charge`,
    { sourceId },
    { auth: true }
  );

  if (resultado.status === 'PENDING' && proveedorSeleccionado.provider === 'IZIPAY') {
    document.querySelector('#checkout-form')?.setAttribute('data-payment-pending', 'true');
    return resultado;
  }
  if (resultado.status !== 'APPROVED') {
    throw new ApiError(
      resultado.failureMessage || 'El pago no fue aprobado. Puedes intentarlo nuevamente desde tus pedidos.',
      422,
      resultado
    );
  }
}

function cargarIzipaySdk(scriptUrl) {
  if (window.Izipay) return Promise.resolve();
  const url = scriptUrl || 'https://sandbox-checkout.izipay.pe/payments/v1/js/index.js';
  if (izipaySdkPromises.has(url)) return izipaySdkPromises.get(url);
  const promise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = url;
    script.async = true;
    script.dataset.izipaySdk = 'true';
    script.onload = () => window.Izipay ? resolve() : reject(new Error('No se pudo cargar el checkout de Izipay'));
    script.onerror = () => reject(new Error('No se pudo cargar el checkout de Izipay'));
    document.body.appendChild(script);
  });
  izipaySdkPromises.set(url, promise);
  return promise;
}

async function obtenerTokenIzipay(transaction, provider, session) {
  const checkout = await storeApi.get(`/store/payment-transactions/${transaction.id}/checkout`, { auth: true });
  if (!checkout.sessionToken || !checkout.merchantCode || !checkout.correlationId || !checkout.publicKey) {
    throw new Error('Izipay no devolvió una sesión válida de pago');
  }
  await cargarIzipaySdk(checkout.scriptUrl);

  const nombre = (session.customer.fullName || 'Cliente').trim().split(/\s+/);
  const firstName = nombre.shift() || 'Cliente';
  const lastName = nombre.join(' ') || 'Final';
  const form = document.querySelector('#checkout-form');
  const address = form?.address?.value?.trim() || '';
  const phone = form?.phone?.value?.trim() || session.customer.phone || '';
  const documentNumber = form?.recipientDni?.value?.trim() || '';
  const dateTimeTransaction = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14);
  const person = {
    firstName,
    lastName,
    email: session.customer.email,
    phoneNumber: phone,
    street: address,
    city: 'Lima',
    state: 'Lima',
    country: 'PE',
    postalCode: '15000',
    documentType: 'DNI',
    document: documentNumber,
  };
  const iziConfig = {
    transactionId: checkout.correlationId,
    action: 'pay',
    merchantCode: checkout.merchantCode,
    order: {
      orderNumber: checkout.purchaseNumber,
      currency: checkout.currencyCode,
      amount: checkout.amount,
      processType: 'AT',
      merchantBuyerId: `customer-${session.customer.id}`,
      dateTimeTransaction,
    },
    billing: person,
    shipping: person,
  };

  return new Promise((resolve, reject) => {
    let finalizado = false;
    const completar = () => {
      if (finalizado) return;
      finalizado = true;
      resolve(checkout.correlationId);
    };
    try {
      const sdk = new window.Izipay({ config: iziConfig });
      sdk.LoadForm({
        authorization: checkout.sessionToken,
        keyRSA: checkout.publicKey,
        callbackResponse: completar,
      });
    } catch (error) {
      reject(error instanceof Error ? error : new Error('No se pudo abrir el checkout de Izipay'));
    }
  });
}

function cargarNiubizSdk(scriptUrl) {
  if (window.VisanetCheckout) return Promise.resolve();
  const url = scriptUrl || 'https://static-content-qas.vnforapps.com/v2/js/checkout.js?qa=true';
  if (niubizSdkPromises.has(url)) return niubizSdkPromises.get(url);
  const promise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = url;
    script.async = true;
    script.dataset.niubizSdk = 'true';
    script.onload = () => window.VisanetCheckout ? resolve() : reject(new Error('No se pudo cargar el checkout de Niubiz'));
    script.onerror = () => reject(new Error('No se pudo cargar el checkout de Niubiz'));
    document.body.appendChild(script);
  });
  niubizSdkPromises.set(url, promise);
  return promise;
}

async function obtenerTokenNiubiz(transaction, provider, session) {
  const checkout = await storeApi.get(`/store/payment-transactions/${transaction.id}/checkout`, { auth: true });
  if (!checkout.sessionToken || !checkout.merchantCode) {
    throw new Error('Niubiz no devolvió una sesión válida de pago');
  }
  await cargarNiubizSdk(checkout.scriptUrl);

  return new Promise((resolve, reject) => {
    let finalizado = false;
    const completar = (params) => {
      if (finalizado) return;
      const tokenValue = typeof params === 'string'
        ? params
        : params?.transactionToken
          || params?.tokenId
          || params?.token
          || params?.order?.transactionToken
          || params?.order?.tokenId;
      const token = tokenValue && typeof tokenValue === 'object' ? tokenValue.token || tokenValue.id : tokenValue;
      if (token) {
        finalizado = true;
        resolve(String(token));
        return;
      }
      finalizado = true;
      reject(new Error(params?.order?.actionDescription || 'Niubiz no devolvió el token de transacción'));
    };

    try {
      window.VisanetCheckout.configure({
        sessiontoken: checkout.sessionToken,
        channel: 'web',
        merchantid: checkout.merchantCode,
        purchasenumber: checkout.purchaseNumber,
        amount: checkout.amount,
        currency: checkout.currencyCode,
        expirationminutes: String(checkout.expirationMinutes || 20),
        timeouturl: 'about:blank',
        cardholdername: session.customer.fullName || '',
        cardholderemail: session.customer.email || '',
        complete: completar,
      });
      window.VisanetCheckout.open();
    } catch (error) {
      reject(error instanceof Error ? error : new Error('No se pudo abrir el checkout de Niubiz'));
    }
  });
}

function cargarCulqiSdk() {
  if (window.CulqiCheckout) return Promise.resolve();
  if (culqiSdkPromise) return culqiSdkPromise;
  culqiSdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://js.culqi.com/checkout-js';
    script.async = true;
    script.dataset.culqiSdk = 'true';
    script.onload = () => window.CulqiCheckout ? resolve() : reject(new Error('No se pudo cargar el checkout de Culqi'));
    script.onerror = () => reject(new Error('No se pudo cargar el checkout de Culqi'));
    document.body.appendChild(script);
  });
  return culqiSdkPromise;
}

async function obtenerTokenCulqi(transaction, provider, session) {
  if (!provider.publicKey) {
    throw new Error('La empresa no ha configurado la llave pÃºblica de Culqi');
  }
  await cargarCulqiSdk();
  return new Promise((resolve, reject) => {
    const amount = Math.round(Number(transaction.amount) * 100);
    if (!Number.isInteger(amount) || amount <= 0) {
      reject(new Error('El monto del checkout no es vÃ¡lido'));
      return;
    }

    const config = {
      settings: {
        title: 'Pago de pedido',
        currency: transaction.currencyCode || 'PEN',
        amount,
        order: '',
      },
      client: { email: session.customer.email },
      options: {
        lang: 'auto',
        installments: true,
        modal: true,
        paymentMethods: { tarjeta: true },
        paymentMethodsSort: ['tarjeta'],
      },
    };
    const checkout = new window.CulqiCheckout(provider.publicKey, config);
    checkout.culqi = () => {
      if (checkout.token?.id) {
        checkout.close();
        resolve(checkout.token.id);
      } else {
        const message = checkout.error?.user_message || checkout.error?.merchant_message || 'No se pudo tokenizar el pago';
        reject(new Error(message));
      }
    };
    checkout.open();
  });
}

async function init() {
  const session = requireCustomerSession();
  if (!session) return;
  sessionActual = session;

  if (!getCart().length) {
    window.location.href = 'carrito.html';
    return;
  }

  renderStoreShell();
  renderStoreSteps(document.querySelector('#store-steps'), 2);

  const [metodos, envio, proveedores] = await Promise.all([
    storeApi.get('/store/catalog/payment-methods').catch(() => []),
    storeApi.get('/store/catalog/shipping-info').catch(() => ({ flatRate: 0, freeShippingDistrict: null })),
    storeApi.get('/store/catalog/payment-providers').catch(() => []),
  ]);
  metodosPagoTodos = metodos;
  shippingInfo = envio;
  proveedoresPago = proveedores;

  render(session);
}

init();
