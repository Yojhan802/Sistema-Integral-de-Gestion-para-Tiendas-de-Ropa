import { login, getSession } from '../core/auth.js';
import { fetchPublicBranding, resolveLogoUrl, applyFavicon } from '../core/public-branding.js';

// Sin caché-primero a propósito: el login se carga una sola vez por sesión (a
// diferencia del sidebar del panel, que se repinta en cada navegación), así
// que el ahorro de una caché no compensa el riesgo de mostrar por un
// instante una marca vieja si cambió desde la última vez. Mientras se
// resuelve el fetch, el HTML ya trae el Qynex por defecto como estado de carga.
fetchPublicBranding().then(aplicarMarca);

function aplicarMarca(branding) {
  document.querySelector('#page-title').textContent = `Iniciar sesión · ${branding.name}`;
  document.querySelector('#brand-name-desktop').textContent = branding.name;
  document.querySelector('#brand-footer').textContent = `© ${new Date().getFullYear()} ${branding.name} — Sistema Integral de Gestión`;

  const logoUrl = resolveLogoUrl(branding);
  if (logoUrl) {
    document.querySelector('#brand-logo-desktop').src = logoUrl;
    document.querySelector('#brand-logo-mobile').src = logoUrl;
  }
  document.querySelector('#brand-logo-mobile').alt = branding.name;
  applyFavicon(logoUrl);

  // Quedaban ocultos por CSS (visibility:hidden en el HTML) para no mostrar
  // ni un instante el Qynex por defecto mientras se resuelve este fetch.
  ['#brand-logo-desktop', '#brand-name-desktop', '#brand-footer', '#brand-logo-mobile'].forEach((sel) => {
    document.querySelector(sel).style.visibility = 'visible';
  });
}

const form = document.querySelector('#login-form');
const errorAlert = document.querySelector('#login-error');
const submitButton = document.querySelector('#login-submit');
const passwordInput = document.querySelector('#password');
const togglePasswordButton = document.querySelector('#toggle-password');

const existingSession = getSession();
if (existingSession) {
  window.location.href = existingSession.user?.mustChangePassword
    ? 'cambiar-contrasena.html'
    : 'dashboard.html';
}

togglePasswordButton?.addEventListener('click', () => {
  const isPassword = passwordInput.type === 'password';
  passwordInput.type = isPassword ? 'text' : 'password';
  togglePasswordButton.setAttribute('aria-label', isPassword ? 'Ocultar contraseña' : 'Mostrar contraseña');
  togglePasswordButton.querySelector('[data-icon-open]').hidden = isPassword;
  togglePasswordButton.querySelector('[data-icon-closed]').hidden = !isPassword;
});

form?.addEventListener('submit', async (event) => {
  event.preventDefault();
  errorAlert.hidden = true;

  const username = document.querySelector('#username').value;
  const password = passwordInput.value;

  submitButton.disabled = true;
  submitButton.querySelector('.spinner').hidden = false;
  submitButton.querySelector('.btn-label').textContent = 'Ingresando…';

  try {
    const response = await login(username, password);
    window.location.href = response.user.mustChangePassword ? 'cambiar-contrasena.html' : 'dashboard.html';
  } catch (error) {
    errorAlert.querySelector('.alert-message').textContent = error.message;
    errorAlert.hidden = false;
    submitButton.disabled = false;
    submitButton.querySelector('.spinner').hidden = true;
    submitButton.querySelector('.btn-label').textContent = 'Ingresar';
    document.querySelector('#username').focus();
  }
});
