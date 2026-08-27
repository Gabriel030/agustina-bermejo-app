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
      <main className="flex flex-1 flex-col">
        <Outlet />
      </main>
    </div>
  );
}
