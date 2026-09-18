import { useLayoutEffect, useRef, useState } from 'react';

// Mide la posición del elemento activo dentro de un contenedor (leyendo el DOM
// vía MutationObserver, no el estado de quien lo usa) para poder deslizar un
// fondo detrás de él — usado por TabsList variant="segmented" y por el
// Sidebar. `activeSelector` identifica al elemento activo actual (ej. un
// data-state o una clase que ya cambia sola con la navegación/selección).
export function useSlidingIndicator(activeSelector) {
  const containerRef = useRef(null);
  const [rect, setRect] = useState(null);

  useLayoutEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    function medir() {
      const activo = container.querySelector(activeSelector);
      if (activo) {
        setRect({ top: activo.offsetTop, left: activo.offsetLeft, width: activo.offsetWidth, height: activo.offsetHeight });
      }
    }

    medir();
    const observer = new MutationObserver(medir);
    observer.observe(container, { attributes: true, attributeFilter: ['class', 'data-state'], subtree: true });
    window.addEventListener('resize', medir);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', medir);
    };
  }, [activeSelector]);

  return { containerRef, rect };
}
