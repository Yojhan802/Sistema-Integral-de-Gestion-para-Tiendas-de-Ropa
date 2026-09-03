export type ValidationResult = string | null;

const NAME_PATTERN = /^[\p{L}]+(?:[ '\u2019-][\p{L}]+)*$/u;
const LEGAL_NAME_PATTERN = /^[\p{L}\p{N} .,'&()\-/]+$/u;

function optional(value: unknown) { return String(value ?? '').trim(); }
function decimal(value: unknown) { return /^\d+(?:\.\d{1,2})?$/.test(String(value ?? '').trim()); }

export function digitsOnly(value: string, maxLength: number) { return value.replace(/\D/g, '').slice(0, maxLength); }
export function personNameInput(value: string, maxLength: number) { return value.replace(/[^\p{L}\s'\u2019-]/gu, '').replace(/\s{2,}/g, ' ').slice(0, maxLength); }
export function legalNameInput(value: string) { return value.replace(/[^\p{L}\p{N} .,'&()\-/]/gu, '').replace(/\s{2,}/g, ' ').slice(0, 150); }
export function contactPhoneInput(value: string, maxLength = 20) { return value.replace(/[^0-9+()\- ]/g, '').slice(0, maxLength); }
export function emailInput(value: string) { return value.replace(/\s/g, '').slice(0, 120); }
export function decimalInput(value: string, decimalPlaces = 2) {
  const normalized = value.replace(',', '.').replace(/[^\d.]/g, '');
  const dot = normalized.indexOf('.');
  if (dot < 0) return normalized;
  return `${normalized.slice(0, dot)}.${normalized.slice(dot + 1).replace(/\./g, '').slice(0, decimalPlaces)}`;
}
export function seriesInput(value: string) { return value.replace(/[^A-Za-z0-9]/g, '').toUpperCase().slice(0, 10); }

export function validateDni(value: unknown, required = true): ValidationResult {
  const text = optional(value);
  if (!text && !required) return null;
  if (!text) return 'Ingresa el DNI.';
  return /^\d{8}$/.test(text) ? null : 'El DNI debe contener exactamente 8 dígitos.';
}

export function validateRuc(value: unknown, required = false): ValidationResult {
  const text = optional(value);
  if (!text && !required) return null;
  if (!text) return 'Ingresa el RUC.';
  if (!/^\d{11}$/.test(text)) return 'El RUC debe contener exactamente 11 dígitos.';
  const weights = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  const sum = weights.reduce((total, weight, index) => total + Number(text[index]) * weight, 0);
  const remainder = 11 - (sum % 11);
  const checkDigit = remainder === 10 ? 0 : remainder === 11 ? 1 : remainder;
  return checkDigit === Number(text[10]) ? null : 'El RUC no supera la validación de dígito verificador.';
}

export function validatePeruPhone(value: unknown, required = false): ValidationResult {
  const text = optional(value);
  if (!text && !required) return null;
  if (!text) return 'Ingresa un número de teléfono.';
  if (!/^9\d{8}$/.test(text)) return 'El celular debe tener 9 dígitos y comenzar en 9.';
  return null;
}

export function validateContactPhone(value: unknown, required = false): ValidationResult {
  const text = optional(value);
  if (!text && !required) return null;
  const digits = text.replace(/\D/g, '');
  if (!text) return 'Ingresa un teléfono.';
  if (!/^[0-9+()\- ]+$/.test(text) || digits.length < 7 || digits.length > 15) return 'El teléfono debe contener entre 7 y 15 dígitos y solo caracteres válidos.';
  return null;
}

export function validatePersonName(value: unknown, label: string, maxLength: number): ValidationResult {
  const text = optional(value);
  if (!text) return `Ingresa ${label}.`;
  if (text.length > maxLength) return `${label} no puede superar los ${maxLength} caracteres.`;
  return NAME_PATTERN.test(text) ? null : `${label} solo puede contener letras, espacios, apóstrofes o guiones.`;
}

export function validateLegalName(value: unknown): ValidationResult {
  const text = optional(value);
  if (!text) return 'Ingresa la razón social.';
  if (text.length > 150) return 'La razón social no puede superar los 150 caracteres.';
  return LEGAL_NAME_PATTERN.test(text) ? null : 'La razón social contiene caracteres no permitidos.';
}

export function validateEmail(value: unknown, required = false): ValidationResult {
  const text = optional(value);
  if (!text && !required) return null;
  if (!text) return 'Ingresa un correo electrónico.';
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(text) && text.length <= 120 ? null : 'Ingresa un correo electrónico válido de hasta 120 caracteres.';
}

export function validatePassword(value: unknown, label = 'La contraseña'): ValidationResult {
  const text = String(value ?? '');
  if (!text) return `${label} es obligatoria.`;
  if (text.length < 8 || text.length > 60) return `${label} debe tener entre 8 y 60 caracteres.`;
  return /[A-Za-z]/.test(text) && /\d/.test(text) ? null : `${label} debe contener al menos una letra y un número.`;
}

export function validateDecimal(value: unknown, label: string, options: { required?: boolean; min?: number; max?: number } = {}): ValidationResult {
  const text = optional(value);
  if (!text && !options.required) return null;
  if (!text) return `Ingresa ${label}.`;
  const numeric = Number(text);
  if (!decimal(text) || !Number.isFinite(numeric)) return `${label} debe ser un número con hasta 2 decimales.`;
  if (options.min !== undefined && numeric < options.min) return `${label} no puede ser menor que ${options.min}.`;
  if (options.max !== undefined && numeric > options.max) return `${label} no puede ser mayor que ${options.max}.`;
  return null;
}

export function validateInteger(value: unknown, label: string, options: { required?: boolean; min?: number; max?: number } = {}): ValidationResult {
  const text = optional(value);
  if (!text && !options.required) return null;
  if (!text) return `Ingresa ${label}.`;
  const numeric = Number(text);
  if (!/^\d+$/.test(text) || !Number.isInteger(numeric)) return `${label} debe ser un número entero.`;
  if (options.min !== undefined && numeric < options.min) return `${label} no puede ser menor que ${options.min}.`;
  if (options.max !== undefined && numeric > options.max) return `${label} no puede ser mayor que ${options.max}.`;
  return null;
}

export function validateCurrencyCode(value: unknown): ValidationResult { return /^[A-Z]{3}$/.test(optional(value).toUpperCase()) ? null : 'El código de moneda debe tener exactamente 3 letras (por ejemplo, PEN).'; }
export function validateCurrencySymbol(value: unknown): ValidationResult { const text = optional(value); return text && text.length <= 5 && !/[\d<>]/.test(text) ? null : 'El símbolo debe tener entre 1 y 5 caracteres y no contener números.'; }
export function validateHexColor(value: unknown, label: string): ValidationResult { return /^#[0-9A-Fa-f]{6}$/.test(optional(value)) ? null : `${label} debe tener formato hexadecimal, por ejemplo #17324D.`; }
export function validateUrl(value: unknown, label: string): ValidationResult { const text = optional(value); if (!text) return null; try { const url = new URL(text); return ['http:', 'https:'].includes(url.protocol) && text.length <= 500 ? null : `${label} debe ser una URL http(s) de hasta 500 caracteres.`; } catch { return `${label} debe ser una URL válida.`; } }
export function validateSeries(value: unknown, label: string): ValidationResult { const text = optional(value); if (!text) return null; return /^[A-Za-z0-9]{1,10}$/.test(text) ? null : `${label} debe tener entre 1 y 10 caracteres alfanuméricos, sin espacios.`; }

export function firstError(...results: ValidationResult[]): ValidationResult { return results.find(Boolean) ?? null; }

export function validateCheckoutData(data: { dni: unknown; firstName: unknown; paternalLastName: unknown; maternalLastName: unknown; phone: unknown; address: unknown; paymentReference?: unknown; notes?: unknown; requiresReference?: boolean }): ValidationResult {
  return firstError(
    validateDni(data.dni),
    validatePersonName(data.firstName, 'los nombres', 100),
    validatePersonName(data.paternalLastName, 'el apellido paterno', 60),
    validatePersonName(data.maternalLastName, 'el apellido materno', 60),
    validatePeruPhone(data.phone, true),
    optional(data.address) ? optional(data.address).length <= 255 ? null : 'La dirección no puede superar los 255 caracteres.' : 'Ingresa la dirección.',
    data.requiresReference && !optional(data.paymentReference) ? 'Ingresa el número de operación.' : optional(data.paymentReference).length <= 50 ? null : 'El número de operación no puede superar los 50 caracteres.',
    optional(data.notes).length <= 255 ? null : 'Las notas no pueden superar los 255 caracteres.',
  );
}

export function validateCheckoutBilling(data: {
  type: 'TICKET' | 'BOLETA' | 'FACTURA';
  number?: unknown;
  name?: unknown;
  electronicInvoicingAvailable: boolean;
}): ValidationResult {
  const number = optional(data.number);
  const name = optional(data.name);
  if (data.type === 'TICKET') {
    return number || name ? 'El ticket interno no requiere datos de facturación.' : null;
  }
  if (!data.electronicInvoicingAvailable) {
    return 'La facturación electrónica no está disponible para esta tienda.';
  }
  if (data.type === 'FACTURA') {
    return firstError(validateRuc(number, true), validateLegalName(name));
  }
  if (number && !/^[A-Za-z0-9]{1,15}$/.test(number)) {
    return 'El documento de la boleta solo admite hasta 15 caracteres alfanuméricos.';
  }
  return name.length > 150 ? 'El nombre de la boleta no puede superar los 150 caracteres.' : null;
}
