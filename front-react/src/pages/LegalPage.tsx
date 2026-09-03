import { StoreShell } from '../components/StoreShell';
import { useStoreConfig } from '../components/TemplateProvider';
import { ErrorState } from '../components/States';
import { findLegalDocument, legalDocuments } from '../services/legal';

function navigate(event: React.MouseEvent<HTMLAnchorElement>, path: string) {
  event.preventDefault();
  window.history.pushState({}, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
}

/**
 * Renderiza los cuatro documentos legales desde una sola plantilla: comparten
 * estructura, así que duplicar la maqueta por documento solo generaría deriva.
 */
export function LegalPage({ path }: { path: string }) {
  const config = useStoreConfig();
  const document = findLegalDocument(path, config);
  const others = legalDocuments(config).filter((item) => item.path !== path);

  if (!document) return <StoreShell><ErrorState message="No encontramos ese documento." /></StoreShell>;

  return <StoreShell>
    <div className="store-page-heading store-legal-heading">
      <span className="store-kicker">{document.kicker}</span>
      <h1>{document.title}</h1>
      <p>{document.summary}</p>
    </div>
    <div className="store-legal-layout">
      <article className="store-legal-doc">
        {document.sections.map((section) => <section key={section.heading}>
          <h2>{section.heading}</h2>
          {section.paragraphs.map((paragraph) => <p key={paragraph.slice(0, 40)}>{paragraph}</p>)}
          {section.bullets && <ul>{section.bullets.map((bullet) => <li key={bullet.slice(0, 40)}>{bullet}</li>)}</ul>}
        </section>)}
      </article>
      <aside className="store-legal-aside">
        <div className="store-info-card">
          <span className="store-kicker">TAMBIÉN AQUÍ</span>
          <h2>Otros documentos</h2>
          <div className="store-legal-links">
            {others.map((item) => <a href={item.path} key={item.path} onClick={(event) => navigate(event, item.path)}>{item.title}</a>)}
            <a href="/libro-reclamaciones" onClick={(event) => navigate(event, '/libro-reclamaciones')}>Libro de Reclamaciones</a>
          </div>
        </div>
        {(config.legalName || config.ruc || config.address) && <div className="store-info-card">
          <span className="store-kicker">PROVEEDOR</span>
          <p className="store-legal-provider">
            {config.legalName && <strong>{config.legalName}</strong>}
            {config.ruc && <span>RUC {config.ruc}</span>}
            {config.address && <span>{config.address}</span>}
            {config.phone && <span>{config.phone}</span>}
            {config.email && <span>{config.email}</span>}
          </p>
        </div>}
      </aside>
    </div>
  </StoreShell>;
}
