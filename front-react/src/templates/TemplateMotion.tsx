import { AnimatePresence, motion, MotionConfig } from 'motion/react';
import type { ReactNode } from 'react';

export function TemplateMotion({ children }: { children: ReactNode }) {
  return <MotionConfig reducedMotion="user" transition={{ duration: 0.36, ease: [0.22, 0.65, 0.28, 1] }}>{children}</MotionConfig>;
}

export function RouteTransition({ routeKey, children }: { routeKey: string; children: ReactNode }) {
  return <AnimatePresence initial={false} mode="wait"><motion.div key={routeKey} className="react-route-layer" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.34, ease: [0.22, 0.65, 0.28, 1] }}>{children}</motion.div></AnimatePresence>;
}
