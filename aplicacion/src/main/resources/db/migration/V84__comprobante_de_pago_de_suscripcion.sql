-- Comprobante del pago de mensualidad.
--
-- La referencia sola no basta para sustentar un cobro discutido: la captura del Yape o de
-- la transferencia vivía en un chat de WhatsApp y ahí se pierde. Aquí queda pegada al pago.

ALTER TABLE subscription_payments
    ADD COLUMN proof_url VARCHAR(255) NULL AFTER reference;
