import { useState, type FormEvent } from 'react';
import { ApiError, setCustomerSession, storeApi } from '../services/api';
import { StoreShell } from '../components/StoreShell';
import { useStoreTemplate } from '../components/TemplateProvider';
import { AuthSurface } from '../templates/AuthSurface';
import { firstError, validateContactPhone, validateEmail, validatePassword, validatePersonName } from '../services/validation';
import { showToast } from '../components/ToastHost';

export function CustomerAuthPage({ register = false }: { register?: boolean }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const template = useStoreTemplate();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validation = firstError(
      validateEmail(email, true),
      register ? validatePassword(password) : password.trim() ? null : 'Ingresa la contraseña.',
      register ? validatePersonName(fullName, 'el nombre completo', 150) : null,
      register ? validateContactPhone(phone) : null,
    );
    if (validation) { setError(validation); return; }
    setLoading(true);
    setError('');
    try {
      const data = register
        ? await storeApi.post<{ accessToken: string; refreshToken: string; customer: { id: number; email: string; fullName: string } }>('/store/auth/register', { email, password, fullName, phone })
        : await storeApi.post<{ accessToken: string; refreshToken: string; customer: { id: number; email: string; fullName: string } }>('/store/auth/login', { email, password });
      setCustomerSession(data);
      showToast(register ? 'Cuenta creada correctamente.' : 'Sesión iniciada correctamente.');
      const params = new URLSearchParams(window.location.search);
      const destination = params.get('volver') ? `/checkout${params.has('previewTemplate') ? `?previewTemplate=${encodeURIComponent(params.get('previewTemplate') || '')}` : ''}` : '/';
      window.history.pushState({}, '', destination);
      window.dispatchEvent(new PopStateEvent('popstate'));
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'No se pudo iniciar sesión');
    } finally {
      setLoading(false);
    }
  }

  return <StoreShell><div className="template-auth-host"><AuthSurface template={template} register={register} fullName={fullName} email={email} phone={phone} password={password} error={error} loading={loading} setFullName={setFullName} setEmail={setEmail} setPhone={setPhone} setPassword={setPassword} onSubmit={submit} /></div></StoreShell>;
}
