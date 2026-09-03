import { useLocation, useOutlet } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';

// Login/Registrarse/NoAutorizado no cuelgan de RoleShell (no hay sidebar/rol
// todavía) así que no heredaban su fade de transición entre pantallas — mismo
// patrón (duración, easing) que RoleShell.jsx para que se sienta consistente
// en toda la app, no una animación distinta acá.
export function PublicLayout() {
  const location = useLocation();
  const element = useOutlet();

  return (
    <AnimatePresence mode="popLayout" initial={false}>
      <motion.div
        key={location.pathname}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.15, ease: 'easeOut' }}
      >
        {element}
      </motion.div>
    </AnimatePresence>
  );
}
