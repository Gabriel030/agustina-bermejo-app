import { useEffect, useRef } from 'react';
import { useLocation, useOutlet } from 'react-router-dom';
import { AnimatePresence, m } from 'motion/react';
import { Sidebar } from '@/components/Sidebar';
import { MeshBackground } from '@/components/MeshBackground';
import { Breadcrumb } from '@/components/Breadcrumb';
import { TopHeader } from '@/components/TopHeader';
import { useBreadcrumb } from '@/hooks/useBreadcrumb';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { ErrorFallback } from '@/components/ErrorFallback';
import { SkipToContent } from '@/components/SkipToContent';
import { SystemBar } from '@/pwa/SystemBar';
import { LegalNoticeBanner } from '@/features/identity/legal/LegalNoticeBanner';
import { isDesignB } from '@/theme/design';

const HEADING_WAIT_MS = 2000;

function useFocusHeadingOnNavigation(mainRef, pathname) {
  const previousPath = useRef(pathname);
  useEffect(() => {
    if (previousPath.current === pathname) return undefined;
    previousPath.current = pathname;
    const main = mainRef.current;
    if (!main || !isDesignB()) return undefined;
    const focusHeading = () => {
      const heading = main.querySelector('h1');
      if (!heading) return false;
      heading.setAttribute('tabindex', '-1');
      heading.focus({ preventScroll: true });
      return true;
    };
    if (focusHeading()) return undefined;
    const observer = new MutationObserver(() => {
      if (focusHeading()) observer.disconnect();
    });
    observer.observe(main, { childList: true, subtree: true });
    const timer = setTimeout(() => observer.disconnect(), HEADING_WAIT_MS);
    return () => {
      observer.disconnect();
      clearTimeout(timer);
    };
  }, [mainRef, pathname]);
}

// Estructura compartida por los 5 layouts de rol: sidebar + área de contenido.
// Lo que varía por rol (título, ítems de nav) lo define cada *Layout.jsx.
export function RoleShell({ titulo, items, groups }) {
  const location = useLocation();
  // useOutlet() (no <Outlet /> directo): captura el elemento ya resuelto de la
  // ruta actual en esta pasada de render. Con <Outlet /> puesto directo dentro
  // del motion.div, mientras el motion.div de la ruta ANTERIOR todavía está
  // montado (animación de salida en curso), ese <Outlet /> resuelve igual a la
  // ruta NUEVA — no está atado a qué motion.div lo contiene — así que ambos
  // motion.div terminan mostrando el mismo contenido en vez de que uno muestre
  // la pantalla vieja saliendo y el otro la nueva entrando.
  const element = useOutlet();
  const breadcrumbItems = useBreadcrumb();
  const mainRef = useRef(null);
  useFocusHeadingOnNavigation(mainRef, location.pathname);

  return (
    // h-screen + overflow-hidden en la raíz: el shell ocupa exactamente el viewport
    // y son el sidebar y el <main> los que scrollean cada uno por su cuenta — antes
    // el body entero se movía al entrar a una pantalla con contenido largo (ej.
    // Detalle de carrera), feedback explícito del usuario.
    //
    // flex-col en base/md, flex-row recién en lg: — el Sidebar fijo (hidden lg:flex)
    // colapsa a un menú hamburguesa (MobileMenu, drawer lateral) en pantallas chicas,
    // no a una bottom tab bar — decisión revisada con el usuario (ver docs/context/frontend.md).
    <div className="flex h-screen flex-col gap-(--shell-gap) overflow-hidden p-(--shell-pad) lg:flex-row">
      <SkipToContent />
      <MeshBackground />
      <Sidebar titulo={titulo} items={items} groups={groups} />
      {/* Columna de contenido junto al Sidebar, en mobile y desktop por igual —
          apila TopHeader arriba de <main>. Antes este wrapper colapsaba con
          lg:contents porque solo contenía <main>; con TopHeader también
          persistiendo en desktop, colapsarlo lo deja como columna angosta
          suelta en el flex-row de arriba en vez de ocupar el resto del ancho. */}
      <div className="flex min-w-0 flex-1 flex-col gap-(--shell-gap) overflow-hidden">
        <TopHeader titulo={titulo} items={items} groups={groups} />
        <SystemBar />
        <LegalNoticeBanner />
        {/* min-w-0: sin esto, un flex item no se achica más allá del ancho
            intrínseco de su contenido (default min-width:auto) — con una
            pantalla de contenido ancho (ej. Catálogo, Épica 7 Fase 3) esto
            empuja todo el layout más allá del viewport en mobile, rompiendo
            la regla de "nunca scroll horizontal del body". Encontrado
            verificando Catálogo en 495px de ancho. */}
        <main
          ref={mainRef}
          id="main-content"
          tabIndex={-1}
          className="flex min-w-0 flex-1 flex-col overflow-y-auto focus:outline-none design-b:px-4 design-b:py-5 sm:design-b:px-8 sm:design-b:py-6"
        >
          {/* Breadcrumb: vive acá (no en cada página) porque es el único lugar que
              todas las rutas de rol comparten — ver useBreadcrumb.js para el mapeo
              ruta→items. No forma parte de la animación de entrada/salida de abajo
              (es chrome de navegación, no contenido de la pantalla) y devuelve null
              para cualquier ruta sin un patrón conocido, así que no aparece en
              pantallas de un solo nivel (Inicio, placeholders, etc). max-w-[1200px]
              replica el ancho de PageContainer para que quede alineado con el
              contenido de abajo. */}
          {breadcrumbItems.length > 0 && (
            <div className="mx-auto w-full max-w-[1200px] pb-3">
              <Breadcrumb items={breadcrumbItems} />
            </div>
          )}
          {/* mode="popLayout" (no "wait"): la pantalla nueva entra ya, sin
              esperar a que la vieja termine de salir — con "wait" la salida y
              la entrada corrían una después de la otra (~360ms en serie), lo
              que en la práctica se veía como pantalla vieja congelada → blanco
              → pantalla nueva, un "flash" en vez de fluidez (reportado por el
              usuario). popLayout además saca a la pantalla saliente del flujo
              (position: absolute) mientras se anima, para que no empuje el
              scroll del <main> ni dispare el scrollbar apareciendo/desapareciendo
              a mitad de la transición. Solo opacity (sin translate en Y): un
              desplazamiento vertical durante el fade es lo que se leía como el
              "flash" del scroll vertical. */}
          <AnimatePresence mode="popLayout" initial={false}>
            <m.div
              key={location.pathname}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15, ease: 'easeOut' }}
              className="flex min-w-0 flex-1 flex-col"
            >
              <ErrorBoundary scope="screen" resetKey={location.pathname} fallback={(props) => <ErrorFallback {...props} />}>
                {element}
              </ErrorBoundary>
            </m.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  );
}
