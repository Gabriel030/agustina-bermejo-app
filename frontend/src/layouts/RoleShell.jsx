import { Outlet } from 'react-router-dom';
import { Sidebar } from '@/components/Sidebar';
import { MeshBackground } from '@/components/MeshBackground';

// Estructura compartida por los 5 layouts de rol: sidebar + área de contenido.
// Lo que varía por rol (título, ítems de nav) lo define cada *Layout.jsx.
export function RoleShell({ titulo, items }) {
  return (
    <div className="flex min-h-screen gap-5 p-5">
      <MeshBackground />
      <Sidebar titulo={titulo} items={items} />
      {/* min-w-0: sin esto, un flex item no se achica más allá del ancho
          intrínseco de su contenido (default min-width:auto) — con una
          pantalla de contenido ancho (ej. Catálogo, Épica 7 Fase 3) esto
          empuja todo el layout más allá del viewport en mobile, rompiendo
          la regla de "nunca scroll horizontal del body". Encontrado
          verificando Catálogo en 495px de ancho. */}
      <main className="flex min-w-0 flex-1 flex-col">
        <Outlet />
      </main>
    </div>
  );
}
