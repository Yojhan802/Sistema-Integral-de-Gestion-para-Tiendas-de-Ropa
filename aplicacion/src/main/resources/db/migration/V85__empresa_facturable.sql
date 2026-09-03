-- Distingue las empresas que facturan de las que no.
--
-- La tienda propia y la de demostración son empresas normales en todo salvo en que nadie
-- paga por ellas. Contándolas, el ingreso mensual sale inflado y el promedio por empresa
-- deja de significar nada.
--
-- No afecta al acceso: una empresa no facturable funciona igual que cualquier otra.

ALTER TABLE company_settings
    ADD COLUMN billable BOOLEAN NOT NULL DEFAULT TRUE AFTER subscription_status;
