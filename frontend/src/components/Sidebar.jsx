import { NavLink } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { GlassCard } from '@/components/GlassCard';
import { useAuth } from '@/context/AuthContext';

/**
 * Sidebar genérico parametrizado por lista de ítems — cada layout de rol le pasa
 * sus propios `items` (ya definidos en el mockup correspondiente), en vez de un
 * único componente con menús condicionales por rol.
 */
export function Sidebar({ titulo, items }) {
  const { usuario, logout } = useAuth();

  return (
    // hidden lg:flex: en base/md este layout colapsa al menú hamburguesa
    // (MobileMenu, ver RoleShell.jsx) — nunca conviven los dos.
    // h-full + min-h-0: dentro del shell (h-screen), el sidebar ocupa toda la
    // altura disponible pero no la excede — habilita que solo el <nav> de abajo
    // scrollee cuando hay muchos ítems, dejando título y "Cerrar sesión" fijos.
    <GlassCard className="hidden h-full w-64 shrink-0 flex-col gap-6 overflow-hidden p-5 lg:flex">
      <div className="shrink-0">
        <div className="text-sm font-semibold text-ink-faint">{titulo}</div>
        {usuario && (
          <div className="mt-1 truncate font-heading text-base font-semibold text-ink">
            {usuario.nombre} {usuario.apellido}
          </div>
        )}
      </div>

      <nav className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
        {items.map(({ to, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              cn(
                'shrink-0 rounded-lg px-3 py-2.5 text-sm font-medium text-ink-soft transition-all duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] hover:translate-x-[3px] hover:bg-accent hover:text-ink',
                isActive && 'bg-accent text-ink',
              )
            }
          >
            {label}
          </NavLink>
        ))}
      </nav>

      <button
        type="button"
        onClick={logout}
        className="shrink-0 rounded-lg px-3 py-2.5 text-left text-sm font-medium text-ink-faint transition-colors hover:text-danger"
      >
        Cerrar sesión
      </button>
    </GlassCard>
  );
}
