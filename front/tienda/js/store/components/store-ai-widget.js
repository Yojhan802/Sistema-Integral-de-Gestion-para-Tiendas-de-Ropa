import { storeApi, ApiError } from '../core/store-api.js';
import { getCachedPublicBranding, fetchPublicBranding } from '../../../../js/core/public-branding.js';

const MAX_HISTORIAL = 16; // 8 turnos (usuario+asistente) — el backend igual lo recorta por su cuenta.

// Frases reales (nada de descuentos o urgencia inventada) para que el avatar
// "hable" solo y llame la atención sin obligar a nadie a abrir el chat.
const MENSAJES_MARKETING = [
  '¿Buscas algo en especial? Pregúntame 👋',
  'Envío gratis si estás en Huacho 🚚',
  'Puedes pagar con Yape, Plin o contraentrega 💳',
  '¿No sabes tu talla? Te ayudo a elegir',
  '¿Buscas un regalo? Cuéntame qué necesitas 🎁',
  'Compra segura, sin vueltas ni letra chica ✅',
  '¿Tienes dudas del envío? Pregúntame al toque',
];
const MAX_TEASERS_POR_VISITA = 4;
const TEASER_COOLDOWN_MS = 15000;
const TEASER_AUTOHIDE_MS = 6000;
const PRIMER_TEASER_DELAY_MS = 3000;

function escapeHtml(texto) {
  return texto.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Convierte el enlace de producto que el asistente incluye en su respuesta en un link real y clicable. */
function renderizarRespuestaBot(texto) {
  return escapeHtml(texto).replace(
    /producto\.html\?id=(\d+)/g,
    '<a href="producto.html?id=$1" class="ai-widget-link">Ver producto →</a>'
  );
}

/**
 * Widget flotante del asistente de compras (plan IA). Se auto-verifica
 * contra /store/assistant/enabled antes de mostrarse — en instalaciones sin
 * ese plan simplemente no aparece, en vez de mostrar un botón que falla.
 */
export async function mountAiWidget(basePath = '') {
  try {
    await storeApi.get('/store/assistant/enabled');
  } catch {
    return; // Plan sin IA, o asistente no configurado — no se muestra el widget.
  }

  const nombreTienda = getCachedPublicBranding().name;
  const root = document.createElement('div');
  root.className = 'ai-widget';
  root.innerHTML = `
    <button class="ai-widget-teaser" id="ai-widget-teaser" type="button" aria-live="polite" hidden></button>
    <button class="ai-widget-bubble" type="button" aria-label="Abrir asistente de compras" aria-expanded="false">
      <img src="${basePath}../assets/ai/asistente-avatar.png" alt="" width="56" height="56" />
    </button>
    <div class="ai-widget-panel" hidden>
      <div class="ai-widget-header">
        <span>Asistente · <span id="store-ai-widget-name">${nombreTienda}</span></span>
        <button class="ai-widget-close" type="button" aria-label="Cerrar">✕</button>
      </div>
      <div class="ai-widget-messages" id="ai-widget-messages">
        <div class="ai-widget-msg ai-widget-msg-bot">¡Hola! Soy el asistente de <span id="store-ai-widget-greeting-name">${nombreTienda}</span>. Pregúntame por productos, envíos o métodos de pago.</div>
      </div>
      <form class="ai-widget-form" id="ai-widget-form">
        <input class="ai-widget-input" id="ai-widget-input" type="text" maxlength="500" placeholder="Escribe tu pregunta…" autocomplete="off" />
        <button class="ai-widget-send" type="submit" aria-label="Enviar">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
      </form>
    </div>
  `;
  document.body.appendChild(root);

  fetchPublicBranding().then((branding) => {
    root.querySelectorAll('#store-ai-widget-name, #store-ai-widget-greeting-name').forEach((el) => {
      el.textContent = branding.name;
    });
  });

  const bubble = root.querySelector('.ai-widget-bubble');
  const panel = root.querySelector('.ai-widget-panel');
  const teaser = root.querySelector('#ai-widget-teaser');
  const messagesEl = root.querySelector('#ai-widget-messages');
  const form = root.querySelector('#ai-widget-form');
  const input = root.querySelector('#ai-widget-input');
  let historial = [];

  function toggle(abrir) {
    const abierto = abrir ?? panel.hidden;
    panel.hidden = !abierto;
    bubble.setAttribute('aria-expanded', String(abierto));
    if (abierto) {
      input.focus();
      ocultarTeaser();
    }
  }

  bubble.addEventListener('click', () => toggle());
  root.querySelector('.ai-widget-close').addEventListener('click', () => toggle(false));

  // ---- Teaser de marketing: habla solo, sin necesidad de abrir el chat ----
  let mensajesRestantes = [];
  let teasersMostrados = 0;
  let ultimoTeaserEn = 0;
  let teaserAutohideId = null;
  const prefiereMenosMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function siguienteMensaje() {
    if (!mensajesRestantes.length) {
      mensajesRestantes = [...MENSAJES_MARKETING].sort(() => Math.random() - 0.5);
    }
    return mensajesRestantes.shift();
  }

  function ocultarTeaser() {
    teaser.hidden = true;
    bubble.classList.remove('ai-widget-bubble-pulse');
    clearTimeout(teaserAutohideId);
  }

  function mostrarTeaser() {
    if (!panel.hidden || teaser.hidden === false) return; // chat abierto o teaser ya visible
    if (teasersMostrados >= MAX_TEASERS_POR_VISITA) return;
    const ahora = Date.now();
    if (ahora - ultimoTeaserEn < TEASER_COOLDOWN_MS) return;
    ultimoTeaserEn = ahora;
    teasersMostrados += 1;

    teaser.textContent = siguienteMensaje();
    teaser.hidden = false;
    if (!prefiereMenosMovimiento) bubble.classList.add('ai-widget-bubble-pulse');
    teaserAutohideId = setTimeout(ocultarTeaser, TEASER_AUTOHIDE_MS);
  }

  teaser.addEventListener('click', () => {
    ocultarTeaser();
    toggle(true);
  });

  setTimeout(mostrarTeaser, PRIMER_TEASER_DELAY_MS);

  let scrollEnEspera = false;
  window.addEventListener('scroll', () => {
    if (scrollEnEspera) return;
    scrollEnEspera = true;
    setTimeout(() => {
      scrollEnEspera = false;
      mostrarTeaser();
    }, 600);
  }, { passive: true });

  function agregarMensajeUsuario(texto) {
    const el = document.createElement('div');
    el.className = 'ai-widget-msg ai-widget-msg-user';
    el.textContent = texto;
    messagesEl.appendChild(el);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return el;
  }

  function agregarMensajeBot(texto) {
    const el = document.createElement('div');
    el.className = 'ai-widget-msg ai-widget-msg-bot';
    el.innerHTML = renderizarRespuestaBot(texto);
    messagesEl.appendChild(el);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return el;
  }

  function actualizarMensajeBot(el, texto) {
    el.innerHTML = renderizarRespuestaBot(texto);
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const mensaje = input.value.trim();
    if (!mensaje) return;

    agregarMensajeUsuario(mensaje);
    input.value = '';
    input.disabled = true;
    const pensando = agregarMensajeBot('Escribiendo…');

    try {
      const { reply } = await storeApi.post('/store/assistant/chat', { message: mensaje, history: historial });
      actualizarMensajeBot(pensando, reply);
      historial.push({ role: 'user', content: mensaje }, { role: 'assistant', content: reply });
      if (historial.length > MAX_HISTORIAL) historial = historial.slice(-MAX_HISTORIAL);
    } catch (error) {
      actualizarMensajeBot(pensando, error instanceof ApiError && error.status !== 0
        ? 'No pude responder eso. Intenta de nuevo en un momento.'
        : 'Sin conexión — revisa tu internet e intenta de nuevo.');
    } finally {
      input.disabled = false;
      input.focus();
    }
  });
}
