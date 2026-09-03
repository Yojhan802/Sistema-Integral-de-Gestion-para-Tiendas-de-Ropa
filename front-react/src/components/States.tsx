export function LoadingState({ label = 'Cargando…' }: { label?: string }) { return <div className="empty-state store-loading" role="status"><span className="store-spinner" aria-hidden="true" />{label}</div>; }
export function ErrorState({ message = 'No se pudo cargar la información.' }: { message?: string }) { return <div className="empty-state store-error" role="alert">{message}</div>; }
export function EmptyState({ children }: { children: React.ReactNode }) { return <div className="empty-state">{children}</div>; }
