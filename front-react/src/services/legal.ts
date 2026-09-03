import type { StoreConfig } from '../types';

/**
 * Textos legales de la tienda. Viven en el frontend porque son el mismo documento
 * para todos los tenants: lo que cambia es la identificación del proveedor, que se
 * inyecta desde la configuración pública (`/store/catalog/config`).
 *
 * Al cambiar el fondo de cualquier documento hay que subir TERMS_VERSION: el pedido
 * guarda qué versión aceptó el comprador, de modo que una modificación posterior no
 * reescribe retroactivamente lo que aceptó.
 */
export const TERMS_VERSION = '2026-09';

/**
 * Plazo de cambio voluntario que el negocio ofrece por encima de la garantía legal.
 * La ley peruana no impone un derecho general de retracto en venta a distancia, así
 * que este número es una decisión comercial: confírmalo con el negocio antes de
 * publicar. La garantía legal por falta de idoneidad (Ley 29571) es independiente
 * de este plazo y no se puede reducir.
 */
export const PLAZO_CAMBIO_DIAS = 7;

export type LegalSection = { heading: string; paragraphs: string[]; bullets?: string[] };
export type LegalDocument = { slug: string; path: string; title: string; kicker: string; summary: string; sections: LegalSection[] };

function proveedor(config: StoreConfig) {
  const nombre = config.legalName || config.name || 'la empresa';
  const ruc = config.ruc ? ` (RUC ${config.ruc})` : '';
  const domicilio = config.address ? `, con domicilio en ${config.address}` : '';
  return `${nombre}${ruc}${domicilio}`;
}

function canalesDeContacto(config: StoreConfig) {
  const canales = [
    config.email ? `correo ${config.email}` : null,
    config.phone ? `teléfono ${config.phone}` : null,
  ].filter(Boolean);
  return canales.length ? canales.join(' o ') : 'los canales de atención publicados en esta tienda';
}

export function privacyPolicy(config: StoreConfig): LegalDocument {
  return {
    slug: 'politica-privacidad',
    path: '/politica-privacidad',
    title: 'Política de Privacidad',
    kicker: 'PROTECCIÓN DE DATOS PERSONALES',
    summary: 'Qué datos personales recogemos en esta tienda, para qué los usamos y cómo puedes ejercer tus derechos.',
    sections: [
      {
        heading: 'Quién trata tus datos',
        paragraphs: [
          `El titular del banco de datos personales es ${proveedor(config)}, en adelante «el proveedor». El tratamiento se realiza conforme a la Ley N° 29733, Ley de Protección de Datos Personales, y su reglamento aprobado por D.S. 003-2013-JUS.`,
          `Puedes comunicarte con nosotros por ${canalesDeContacto(config)} para cualquier asunto relacionado con tus datos personales.`,
        ],
      },
      {
        heading: 'Qué datos recogemos',
        paragraphs: ['Solo pedimos los datos necesarios para vender, entregar y facturar. En concreto:'],
        bullets: [
          'Datos de tu cuenta: nombre completo, correo electrónico, teléfono y contraseña (que se guarda cifrada, nunca en texto plano).',
          'Datos de entrega: nombre y DNI de quien recibe, dirección, referencia, distrito, provincia y departamento.',
          'Datos de facturación: tipo de comprobante y, si pides factura, el RUC y la razón social que nos indiques.',
          'Datos de la compra: productos, cantidades, importes, método de pago y, si lo subes, la imagen del comprobante de pago.',
          'Datos de una reclamación: los que declares en el Libro de Reclamaciones, incluido tu domicilio, exigido por el Anexo II del D.S. 011-2011-PCM.',
        ],
      },
      {
        heading: 'Para qué los usamos',
        paragraphs: ['Cada dato tiene una finalidad concreta y no lo usamos para otra cosa sin pedírtelo antes:'],
        bullets: [
          'Procesar tu pedido, cobrarlo, entregarlo y emitir el comprobante electrónico correspondiente.',
          'Atender consultas, cambios, devoluciones y reclamaciones.',
          'Cumplir obligaciones tributarias y de conservación documentaria frente a SUNAT e INDECOPI.',
        ],
      },
      {
        heading: 'Con quién los compartimos',
        paragraphs: [
          'No vendemos ni cedemos tus datos personales. Los compartimos únicamente con quienes hacen falta para completar tu compra: la pasarela de pago que elijas, el proveedor de facturación electrónica autorizado por SUNAT y la empresa de reparto que entrega tu pedido. Cada uno recibe solo lo mínimo necesario para su función.',
          'Los datos de tu tarjeta se procesan directamente en el entorno de la pasarela de pago. La tienda no los recibe ni los almacena en ningún momento.',
        ],
      },
      {
        heading: 'Cuánto tiempo los conservamos',
        paragraphs: [
          'Conservamos los datos mientras tu cuenta esté activa y, después, durante los plazos que exige la normativa tributaria y de protección al consumidor para poder sustentar la operación ante una fiscalización o un reclamo. Cumplidos esos plazos, se eliminan o anonimizan.',
        ],
      },
      {
        heading: 'Tus derechos',
        paragraphs: [
          'Puedes ejercer en cualquier momento tus derechos de acceso, rectificación, cancelación y oposición sobre tus datos personales, además de los derechos de información y de tratamiento objetivo que reconoce la Ley N° 29733.',
          `Para ejercerlos, escríbenos por ${canalesDeContacto(config)} indicando tu nombre, el derecho que quieres ejercer y un documento que acredite tu identidad. Si consideras que no atendimos tu solicitud, puedes acudir a la Autoridad Nacional de Protección de Datos Personales del Ministerio de Justicia y Derechos Humanos.`,
        ],
      },
      {
        heading: 'Seguridad',
        paragraphs: [
          'Aplicamos medidas técnicas y organizativas para proteger tus datos: las contraseñas se almacenan con funciones de hash, el acceso del personal está limitado por permisos y toda operación sensible queda registrada en un histórico de auditoría. Ninguna medida es infalible, pero trabajamos para que el riesgo sea el mínimo razonable.',
        ],
      },
    ],
  };
}

export function termsAndConditions(config: StoreConfig): LegalDocument {
  return {
    slug: 'terminos-condiciones',
    path: '/terminos-condiciones',
    title: 'Términos y Condiciones',
    kicker: `CONDICIONES DE COMPRA · VERSIÓN ${TERMS_VERSION}`,
    summary: 'Las reglas que rigen las compras realizadas en esta tienda online. Al confirmar un pedido aceptas este documento.',
    sections: [
      {
        heading: 'Identificación del proveedor',
        paragraphs: [
          `Esta tienda online es operada por ${proveedor(config)}. Puedes contactarnos por ${canalesDeContacto(config)}.`,
          'La relación de consumo se rige por la Ley N° 29571, Código de Protección y Defensa del Consumidor, y por estos términos.',
        ],
      },
      {
        heading: 'Precios y comprobantes',
        paragraphs: [
          'Todos los precios se muestran en soles (S/) e incluyen el IGV. El precio que ves en el producto es el precio final del producto; el costo de envío, cuando corresponde, se calcula y se muestra por separado en el checkout antes de que confirmes.',
          'Emitimos boleta o factura electrónica según lo que elijas al comprar. Si eliges factura, debes indicarnos un RUC válido y la razón social; los datos que declares son de tu responsabilidad.',
        ],
      },
      {
        heading: 'Cómo se forma el pedido',
        paragraphs: [
          'Agregar productos al carrito no reserva stock ni genera obligación alguna. El pedido nace cuando lo confirmas en el checkout: en ese momento el sistema valida el precio y el stock reales contra nuestro servidor, retiene las unidades y te asigna un número de pedido.',
          'Si un producto quedó sin stock entre que lo agregaste y confirmaste, te lo indicamos y el pedido no se crea. Si detectamos un error evidente de precio o una diferencia entre lo mostrado y lo real, te contactaremos antes de continuar y podrás desistir sin costo.',
        ],
      },
      {
        heading: 'Pago',
        paragraphs: [
          'Aceptamos los medios de pago que aparecen habilitados en el checkout. Los pagos con tarjeta se procesan íntegramente en el entorno de la pasarela; nosotros nunca recibimos los datos de tu tarjeta.',
          'En los pagos por billetera digital o transferencia, el pedido queda pendiente hasta que verifiquemos la operación. Puedes adjuntar tu constancia de pago al confirmar o después, desde «Mis pedidos».',
        ],
      },
      {
        heading: 'Entrega',
        paragraphs: [
          'Entregamos en la dirección que registres en el checkout. Los plazos que informemos son estimados y se cuentan desde que el pago queda verificado, no desde que se crea el pedido.',
          'Es tu responsabilidad que la dirección, la referencia y el teléfono sean correctos. Si la entrega falla por datos incorrectos o por ausencia reiterada en el domicilio, podemos cobrarte el costo de un nuevo intento.',
        ],
      },
      {
        heading: 'Garantía legal e idoneidad',
        paragraphs: [
          'Todos los productos cuentan con la garantía legal de idoneidad prevista en los artículos 97 y siguientes de la Ley N° 29571: deben corresponder a lo ofrecido y servir para el fin al que están destinados. Si un producto llega dañado, incompleto o distinto a lo comprado, nos haremos cargo de la reposición, el cambio o la devolución del importe, a tu elección y sin costo para ti.',
          'Esta garantía es independiente de nuestra política comercial de cambios, que se describe en su propia página y que se ofrece en adición a la ley, nunca en su lugar.',
        ],
      },
      {
        heading: 'Cancelación de un pedido',
        paragraphs: [
          'Puedes solicitar la cancelación de un pedido mientras siga pendiente de pago. Una vez confirmado el pago, la cancelación depende del estado de preparación y se atiende como una devolución conforme a la política de cambios y devoluciones.',
        ],
      },
      {
        heading: 'Tu cuenta',
        paragraphs: [
          'Para comprar necesitas una cuenta. Eres responsable de la veracidad de los datos que registres y de mantener tu contraseña en reserva. Avísanos de inmediato si detectas un uso no autorizado.',
        ],
      },
      {
        heading: 'Libro de Reclamaciones',
        paragraphs: [
          'Esta tienda cuenta con Libro de Reclamaciones virtual conforme al D.S. 011-2011-PCM. Puedes presentar un reclamo o una queja de forma gratuita, sin necesidad de haber comprado, y recibirás una constancia con el código de tu hoja al momento de registrarla.',
        ],
      },
      {
        heading: 'Cambios en estos términos',
        paragraphs: [
          `Podemos actualizar estos términos. Cada pedido queda regido por la versión vigente al momento de confirmarlo: el sistema guarda qué versión aceptaste, de modo que una modificación posterior no altera lo que ya compraste. La versión actual es ${TERMS_VERSION}.`,
        ],
      },
    ],
  };
}

export function returnsPolicy(config: StoreConfig): LegalDocument {
  return {
    slug: 'cambios-devoluciones',
    path: '/cambios-devoluciones',
    title: 'Cambios y Devoluciones',
    kicker: 'DESPUÉS DE LA COMPRA',
    summary: 'Qué hacer si el producto llegó mal, no es lo que esperabas o quieres cambiarlo.',
    sections: [
      {
        heading: 'Si el producto llegó con un defecto o no es el que pediste',
        paragraphs: [
          'Esto lo cubre la garantía legal de idoneidad (Ley N° 29571) y se resuelve sin costo para ti. Escríbenos apenas lo detectes, con tu número de pedido y una foto de lo recibido.',
          'Según el caso, repondremos el producto, lo cambiaremos por otro equivalente o te devolveremos el importe pagado. La elección es tuya. El costo del envío de retorno lo asumimos nosotros.',
        ],
      },
      {
        heading: 'Si quieres cambiarlo por talla, color o modelo',
        paragraphs: [
          `Este es un cambio comercial, distinto de la garantía legal: lo ofrecemos por decisión propia dentro de los ${PLAZO_CAMBIO_DIAS} días calendario siguientes a la entrega.`,
          'Para que proceda, el producto debe estar sin uso, con sus etiquetas y su empaque original, y debes presentar la boleta o factura de la compra.',
        ],
      },
      {
        heading: 'Qué no se cambia',
        paragraphs: [
          'No aplican cambios comerciales sobre productos de higiene personal o ropa interior una vez abierto el empaque, ni sobre productos personalizados por encargo, ni sobre artículos vendidos como liquidación cuando el defecto que motiva el cambio fue el mismo que se informó al momento de la venta.',
          'Ninguna de estas exclusiones limita la garantía legal: si el producto no es idóneo, respondemos igual.',
        ],
      },
      {
        heading: 'Cómo se devuelve el dinero',
        paragraphs: [
          'Cuando corresponde una devolución de dinero, esta se realiza por el mismo medio con el que pagaste. El plazo depende de tu banco o de la pasarela; te informaremos la fecha en que lo procesamos y podrás verlo reflejado en tu comprobante o nota de crédito electrónica.',
        ],
      },
      {
        heading: 'Cómo iniciarlo',
        paragraphs: [
          `Escríbenos por ${canalesDeContacto(config)} indicando tu número de pedido y el motivo. Si prefieres dejar constancia formal, puedes registrar un reclamo en nuestro Libro de Reclamaciones; ambas vías se atienden, pero el Libro te da un código de seguimiento y un plazo de respuesta.`,
        ],
      },
    ],
  };
}

export function cookiePolicy(config: StoreConfig): LegalDocument {
  return {
    slug: 'politica-cookies',
    path: '/politica-cookies',
    title: 'Política de Cookies',
    kicker: 'ALMACENAMIENTO EN TU NAVEGADOR',
    summary: 'Qué guardamos en tu navegador, para qué sirve y cómo puedes borrarlo.',
    sections: [
      {
        heading: 'Qué usamos realmente',
        paragraphs: [
          'Esta tienda no usa cookies publicitarias ni de seguimiento de terceros. Lo que guardamos vive en el almacenamiento local de tu propio navegador y solo lo lee esta tienda.',
        ],
        bullets: [
          'Tu carrito, para que no pierdas lo que agregaste al recargar la página o volver más tarde.',
          'Tu sesión de cliente, para mantenerte identificado mientras compras sin pedirte la contraseña en cada página.',
          'Tu decisión sobre este aviso, para no volver a mostrártelo en cada visita.',
        ],
      },
      {
        heading: 'Qué pasa si lo rechazas',
        paragraphs: [
          'El carrito y la sesión son imprescindibles para que la tienda funcione: sin ellos no podrías comprar. Por eso, rechazar solo desactiva cualquier medición opcional y deja activo lo estrictamente necesario para el servicio que pediste.',
        ],
      },
      {
        heading: 'Cómo borrarlo',
        paragraphs: [
          'Puedes eliminar este almacenamiento en cualquier momento desde la configuración de tu navegador, borrando los datos del sitio. Al hacerlo perderás el carrito guardado y se cerrará tu sesión, pero tu cuenta y tus pedidos no se ven afectados.',
          `Si tienes dudas sobre este punto, escríbenos por ${canalesDeContacto(config)}.`,
        ],
      },
    ],
  };
}

/**
 * Aviso de precio final. El Código de Consumo exige informar el precio con impuestos
 * incluidos, así que se muestra en producto, carrito y checkout. Devuelve null si el
 * tenant no tiene IGV configurado: es preferible callar a declarar una tasa inventada.
 */
export function igvNotice(config: StoreConfig): string | null {
  const rate = Number(config.igvRate ?? 0);
  if (!Number.isFinite(rate) || rate <= 0) return null;
  const percent = rate * 100;
  const label = Number.isInteger(percent) ? String(percent) : percent.toFixed(2);
  return `Precios en soles con IGV incluido (${label}%).`;
}

/** Versión corta para colocar junto a un precio o un total, sin repetir la moneda. */
export function igvShortNotice(config: StoreConfig): string | null {
  return igvNotice(config) ? 'IGV incluido' : null;
}

export function legalDocuments(config: StoreConfig): LegalDocument[] {
  return [termsAndConditions(config), privacyPolicy(config), returnsPolicy(config), cookiePolicy(config)];
}

export function findLegalDocument(path: string, config: StoreConfig): LegalDocument | undefined {
  return legalDocuments(config).find((document) => document.path === path);
}
