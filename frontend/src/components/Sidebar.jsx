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
    <GlassCard className="flex h-full w-64 shrink-0 flex-col gap-6 p-5">
      <div>
        <div className="text-sm font-semibold text-ink-faint">{titulo}</div>
        {usuario && (
          <div className="mt-1 truncate font-heading text-base font-semibold text-ink">
            {usuario.nombre} {usuario.apellido}
          </div>
        )}
      </div>

      <nav className="flex flex-1 flex-col gap-1">
        {items.map(({ to, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              cn(
                'rounded-lg px-3 py-2.5 text-sm font-medium text-ink-soft transition-all duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] hover:translate-x-[3px] hover:bg-accent hover:text-ink',
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
        className="rounded-lg px-3 py-2.5 text-left text-sm font-medium text-ink-faint transition-colors hover:text-danger"
      >
        Cerrar sesión
      </button>
    </GlassCard>
  );
}
