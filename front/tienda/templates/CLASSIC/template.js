let scrollListenerAttached = false;

function setupMobileMenu() {
  const header = document.querySelector('#store-header');
  const toggle = header?.querySelector('[data-store-mobile-menu]');
  const nav = header?.querySelector('#store-nav');
  if (!toggle || !nav || toggle.dataset.bound === 'true') return;

  toggle.dataset.bound = 'true';
  toggle.addEventListener('click', () => {
    const open = header.dataset.menuOpen === 'true';
    header.dataset.menuOpen = String(!open);
    toggle.setAttribute('aria-expanded', String(!open));
    nav.classList.toggle('is-open', !open);
  });

  if (nav.dataset.closeBound !== 'true') {
    nav.dataset.closeBound = 'true';
    nav.querySelectorAll('a').forEach((link) => {
      link.addEventListener('click', () => {
        header.dataset.menuOpen = 'false';
        toggle.setAttribute('aria-expanded', 'false');
        nav.classList.remove('is-open');
      });
    });
  }

  if (header.dataset.dismissBound !== 'true') {
    header.dataset.dismissBound = 'true';
    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape' || header.dataset.menuOpen !== 'true') return;
      header.dataset.menuOpen = 'false';
      toggle.setAttribute('aria-expanded', 'false');
      nav.classList.remove('is-open');
      toggle.focus();
    });
    document.addEventListener('click', (event) => {
      if (header.dataset.menuOpen !== 'true' || header.contains(event.target)) return;
      header.dataset.menuOpen = 'false';
      toggle.setAttribute('aria-expanded', 'false');
      nav.classList.remove('is-open');
    });
  }
}

function setupCompactHeader() {
  if (scrollListenerAttached) return;
  scrollListenerAttached = true;
  const header = document.querySelector('#store-header');
  if (!header) return;

  const update = () => header.classList.toggle('is-scrolled', window.scrollY > 32);
  update();
  window.addEventListener('scroll', update, { passive: true });
}

function setupReveal() {
  if (document.body?.dataset.storePage !== 'home') return;
  const root = document.querySelector('#store-main') || document.body;
  if (root.dataset.revealBound === 'true') return;
  root.dataset.revealBound = 'true';

  const selector = '.store-section, .store-category-banner, .store-benefits';
  const observed = new WeakSet();
  const observer = 'IntersectionObserver' in window
    ? new IntersectionObserver(
        (entries, currentObserver) => {
          entries.forEach((entry) => {
            if (!entry.isIntersecting) return;
            entry.target.classList.add('is-visible');
            currentObserver.unobserve(entry.target);
          });
        },
        { threshold: 0.08, rootMargin: '0px 0px -40px' }
      )
    : null;

  const observeTargets = () => {
    root.querySelectorAll(selector).forEach((element) => {
      if (observed.has(element)) return;
      observed.add(element);
      if (observer) observer.observe(element);
      else element.classList.add('is-visible');
    });
  };

  observeTargets();
  const mutationObserver = new MutationObserver(observeTargets);
  mutationObserver.observe(root, { childList: true, subtree: true });
}

function setupSmoothAnchors() {
  if (document.body?.dataset.storePage !== 'home') return;
  if (document.body.dataset.smoothAnchorsBound === 'true') return;
  document.body.dataset.smoothAnchorsBound = 'true';

  document.addEventListener('click', (event) => {
    const link = event.target.closest('a[href*="#"]');
    if (!link) return;

    const destination = new URL(link.href, window.location.href);
    if (destination.pathname !== window.location.pathname || !destination.hash) return;
    const target = document.querySelector(destination.hash);
    if (!target) return;

    event.preventDefault();
    history.pushState(null, '', destination.hash);
    target.scrollIntoView({
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
      block: 'start',
    });
  });
}

export function enhanceClassicStore() {
  setupMobileMenu();
  setupCompactHeader();
  setupReveal();
  setupSmoothAnchors();
}
