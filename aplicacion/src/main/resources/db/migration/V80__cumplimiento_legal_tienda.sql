-- Cumplimiento legal del canal online (Ola 1 de la auditoría de tienda).
--
-- 1. El Anexo II del D.S. 011-2011-PCM exige el domicilio del consumidor en la
--    hoja de reclamación. La columna nace NULL porque las hojas ya registradas
--    no lo tienen; el formulario público sí lo pide como obligatorio.
-- 2. El Código de Consumo exige que el comprador acepte los términos antes de
--    contratar a distancia. Guardamos el instante y la versión aceptada para
--    poder probar qué texto aceptó, no solo que aceptó algo.

ALTER TABLE complaint_book_entries
    ADD COLUMN consumer_address VARCHAR(255) NULL AFTER consumer_phone;

ALTER TABLE orders
    ADD COLUMN terms_accepted_at DATETIME(6) NULL AFTER billing_name,
    ADD COLUMN terms_version VARCHAR(20) NULL AFTER terms_accepted_at;
