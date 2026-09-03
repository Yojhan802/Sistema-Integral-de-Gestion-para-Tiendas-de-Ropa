import { requireSession, logout } from '../core/auth.js';
import { api, ApiError } from '../core/api.js';
import { renderShell } from '../components/shell.js';
import { showToast } from '../components/toast.js';

const session = requireSession();

if (session) {
  renderShell('cambiar-contrasena');

  const form = document.querySelector('#change-password-form');
  const errorAlert = document.querySelector('#change-password-error');
  const submit = document.querySelector('#change-password-submit');

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    errorAlert.hidden = true;

    const currentPassword = document.querySelector('#current-password').value;
    const newPassword = document.querySelector('#new-password').value;
    const confirmPassword = document.querySelector('#confirm-password').value;
    if (newPassword !== confirmPassword) {
      errorAlert.querySelector('.alert-message').textContent = 'Las nuevas contraseñas no coinciden';
      errorAlert.hidden = false;
      return;
    }

    submit.disabled = true;
    try {
      const endpoint = session.user?.mustChangePassword
        ? '/auth/complete-password-change'
        : '/auth/change-password';
      await api.post(endpoint, { currentPassword, newPassword });
      showToast({ type: 'success', title: 'Contraseña actualizada', message: 'Vuelve a iniciar sesión con tu nueva contraseña.' });
      setTimeout(() => logout(), 1200);
    } catch (error) {
      errorAlert.querySelector('.alert-message').textContent = error instanceof ApiError
        ? error.message
        : 'No se pudo cambiar la contraseña';
      errorAlert.hidden = false;
      submit.disabled = false;
    }
  });
}
