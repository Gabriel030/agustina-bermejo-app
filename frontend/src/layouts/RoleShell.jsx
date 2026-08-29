import { Outlet } from 'react-router-dom';
import { Sidebar } from '@/components/Sidebar';
import { MobileMenu } from '@/components/MobileMenu';
import { MeshBackground } from '@/components/MeshBackground';

// Estructura compartida por los 5 layouts de rol: sidebar + área de contenido.
// Lo que varía por rol (título, ítems de nav) lo define cada *Layout.jsx.
export function RoleShell({ titulo, items }) {
  return (
    // h-screen + overflow-hidden en la raíz: el shell ocupa exactamente el viewport
    // y son el sidebar y el <main> los que scrollean cada uno por su cuenta — antes
    // el body entero se movía al entrar a una pantalla con contenido largo (ej.
    // Detalle de carrera), feedback explícito del usuario.
    //
    // flex-col en base/md, flex-row recién en lg: — el Sidebar fijo (hidden lg:flex)
    // colapsa a un menú hamburguesa (MobileMenu, drawer lateral) en pantallas chicas,
    // no a una bottom tab bar — decisión revisada con el usuario (ver docs/context/frontend.md).
    <div className="flex h-screen flex-col gap-5 overflow-hidden p-5 lg:flex-row">
      <MeshBackground />
      <Sidebar titulo={titulo} items={items} />
      {/* lg:contents: en desktop este wrapper desaparece del layout (sus hijos —
          la topbar lg:hidden y <main> — quedan directamente en el flex-row de
          arriba, igual que antes de agregar el menú mobile); en base/md sí es
          un contenedor real (flex-col) que apila la topbar del hamburguesa
          arriba de <main>. */}
      <div className="flex min-w-0 flex-1 flex-col gap-5 overflow-hidden lg:contents">
        <div className="flex shrink-0 items-center gap-3 lg:hidden">
          <MobileMenu titulo={titulo} items={items} />
          <div className="font-heading text-base font-bold text-ink">{titulo}</div>
        </div>
        {/* min-w-0: sin esto, un flex item no se achica más allá del ancho
            intrínseco de su contenido (default min-width:auto) — con una
            pantalla de contenido ancho (ej. Catálogo, Épica 7 Fase 3) esto
            empuja todo el layout más allá del viewport en mobile, rompiendo
            la regla de "nunca scroll horizontal del body". Encontrado
            verificando Catálogo en 495px de ancho. */}
        <main className="flex min-w-0 flex-1 flex-col overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
