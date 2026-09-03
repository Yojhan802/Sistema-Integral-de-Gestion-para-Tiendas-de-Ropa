-- Sistema de atributos genéricos por producto (Fase 5, ver plan aprobado): los prompts de IA
-- (asistente de compras, generador de descripciones, analista de reportes) hoy tienen "negocio
-- de ropa en Perú" hardcodeado. business_vertical decide si se activan las instrucciones
-- específicas de ropa (armador de outfits, guardrail de combinaciones talla-color) —
-- default CLOTHING para no cambiar el comportamiento del tenant actual. business_description es
-- un texto libre opcional para la frase de framing del prompt; si es NULL se arma uno genérico
-- a partir de business_vertical (ver AsistenteTiendaService).
ALTER TABLE company_settings
    ADD COLUMN business_vertical VARCHAR(20) NOT NULL DEFAULT 'CLOTHING' AFTER name,
    ADD COLUMN business_description VARCHAR(255) NULL AFTER business_vertical;
