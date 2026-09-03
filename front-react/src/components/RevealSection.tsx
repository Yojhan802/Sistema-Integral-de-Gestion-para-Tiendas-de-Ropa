import { useEffect } from 'react';

/**
 * Shared reveal behavior for dynamic storefront sections.
 * Template CSS can animate `.store-section` and must reveal it with
 * `.store-section.is-visible`. Keeping the observer here means every
 * template receives the same behavior without duplicating page logic.
 */
export function useRevealSections(dependency = 0) {
  useEffect(() => {
    const sections = Array.from(document.querySelectorAll<HTMLElement>('.store-section'));
    if (!sections.length) return;

    const show = (section: HTMLElement) => section.classList.add('is-visible');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reducedMotion || !('IntersectionObserver' in window)) {
      sections.forEach(show);
      return;
    }

    const observer = new IntersectionObserver(([entry]) => {
      if (!entry?.isIntersecting) return;
      show(entry.target as HTMLElement);
      observer.unobserve(entry.target);
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.01 });

    sections.forEach((section) => {
      if (section.classList.contains('is-visible')) return;
      observer.observe(section);
    });
    return () => observer.disconnect();
  }, [dependency]);
}
