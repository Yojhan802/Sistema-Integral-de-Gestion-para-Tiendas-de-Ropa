import type { FormEvent } from 'react';
import type { StoreTemplate } from '../types';
import { contactPhoneInput, emailInput, personNameInput } from '../services/validation';

export interface AuthSurfaceProps {
  template: StoreTemplate;
  register: boolean;
  fullName: string;
  email: string;
  phone: string;
  password: string;
  error: string;
  loading: boolean;
  setFullName: (value: string) => void;
  setEmail: (value: string) => void;
  setPhone: (value: string) => void;
  setPassword: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

export function AuthSurface({ template, register, fullName, email, phone, password, error, loading, setFullName, setEmail, setPhone, setPassword, onSubmit }: AuthSurfaceProps) {
  return <section className={`template-auth template-auth-${template.toLowerCase()}`} data-template-surface="auth">
    <span className="template-panel-kicker">CUENTA QYNEX</span>
    <h1>{register ? 'Crear cuenta' : 'Iniciar sesión'}</h1>
    <p className="template-auth-intro">{register ? 'Guarda tus pedidos y agiliza tus próximas compras.' : 'Ingresa para continuar con tu compra.'}</p>
    <form className="template-auth-form" onSubmit={onSubmit}>
      {register && <label className="template-field"><span>Nombre completo</span><input required maxLength={150} autoComplete="name" value={fullName} onChange={(event) => setFullName(personNameInput(event.target.value, 150))} /></label>}
      <label className="template-field"><span>Correo electrónico</span><input required maxLength={120} autoComplete="email" type="email" value={email} onChange={(event) => setEmail(emailInput(event.target.value))} /></label>
      {register && <label className="template-field"><span>Teléfono</span><input maxLength={20} inputMode="tel" autoComplete="tel" value={phone} onChange={(event) => setPhone(contactPhoneInput(event.target.value))} /></label>}
      <label className="template-field"><span>Contraseña</span><input required minLength={8} maxLength={60} autoComplete={register ? 'new-password' : 'current-password'} type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
      {error && <div className="template-error" role="alert">{error}</div>}
      <button className="template-submit" disabled={loading} type="submit">{loading ? 'Procesando...' : register ? 'Crear cuenta' : 'Ingresar'}</button>
    </form>
    <a className="template-auth-switch" href={register ? '/cuenta/login' : '/cuenta/registro'}>{register ? 'Ya tengo una cuenta' : 'Crear una cuenta'}</a>
  </section>;
}
