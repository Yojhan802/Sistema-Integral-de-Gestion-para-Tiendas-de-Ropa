import { useEffect, type ReactNode } from 'react';

type ConfirmDialogProps = {
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: 'primary' | 'danger';
  busy?: boolean;
  onClose: () => void;
  onConfirm: () => void;
};

export function ConfirmDialog({ title, message, confirmLabel = 'Confirmar', cancelLabel = 'Cancelar', tone = 'primary', busy = false, onClose, onConfirm }: ConfirmDialogProps) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [busy, onClose]);

  return <div className="react-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target && !busy) onClose(); }}>
    <section className="react-dialog react-dialog-small" role="dialog" aria-modal="true" aria-labelledby="react-confirm-dialog-title">
      <div className="react-dialog-header">
        <h2 id="react-confirm-dialog-title">{title}</h2>
        <button className="btn btn-ghost btn-sm" type="button" aria-label="Cerrar" disabled={busy} onClick={onClose}>×</button>
      </div>
      <div className="react-confirm-dialog-content"><p>{message}</p></div>
      <div className="react-dialog-actions">
        <button className="btn btn-secondary" type="button" disabled={busy} onClick={onClose}>{cancelLabel}</button>
        <button className={`btn ${tone === 'danger' ? 'btn-danger' : 'btn-primary'}`} type="button" disabled={busy} onClick={onConfirm}>{busy ? 'Procesando…' : confirmLabel}</button>
      </div>
    </section>
  </div>;
}
