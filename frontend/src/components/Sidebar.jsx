import { NavLink } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { GlassCard } from '@/components/GlassCard';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { useAuth } from '@/context/AuthContext';
import { useSlidingIndicator } from '@/hooks/useSlidingIndicator';

// El fondo del ítem activo ya no lo pinta el propio NavLink — es un único
// degradé que vive detrás de todos (ver SidebarIndicator) y se desliza de una
// opción a otra en vez de aparecer/desaparecer de golpe con cada navegación.
// `nav-item-activo` es solo un marcador para que el indicador sepa a qué
// elemento seguir — no aplica ningún estilo por sí solo.
const NAV_ITEM_BASE =
  'relative z-10 flex shrink-0 items-center gap-2.5 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] hover:translate-x-[3px]';
const NAV_ITEM_ACTIVO = 'nav-item-activo text-white';
const NAV_ITEM_INACTIVO = 'text-ink-soft hover:bg-neutral-bg hover:text-ink';

function ItemNav({ to, label, icon: Icon }) {
  return (
    <NavLink key={to} to={to} className={({ isActive }) => cn(NAV_ITEM_BASE, isActive ? NAV_ITEM_ACTIVO : NAV_ITEM_INACTIVO)}>
      {Icon && <Icon className="h-4 w-4 shrink-0" />}
      <span className="truncate" title={label}>{label}</span>
    </NavLink>
  );
}

/**
 * Sidebar genérico parametrizado por lista de ítems — cada layout de rol le pasa
 * sus propios `items` (ya definidos en el mockup correspondiente), en vez de un
 * único componente con menús condicionales por rol.
 */
export function Sidebar({ titulo, items }) {
  const { usuario, logout } = useAuth();
  const { containerRef, rect } = useSlidingIndicator('.nav-item-activo');

  // "Opciones" se separa del resto de la navegación de contenido y se agrupa
  // visualmente junto a "Cerrar sesión" (pedido explícito del usuario) — el
  // resto de los roles que no tienen ese ítem no se ven afectados.
  const principales = items.filter((item) => item.label !== 'Opciones');
  const opciones = items.find((item) => item.label === 'Opciones');

  return (
    // hidden lg:flex: en base/md este layout colapsa al menú hamburguesa
    // (MobileMenu, ver RoleShell.jsx) — nunca conviven los dos.
    // h-full + min-h-0: dentro del shell (h-screen), el sidebar ocupa toda la
    // altura disponible pero no la excede — habilita que solo el <nav> de abajo
    // scrollee cuando hay muchos ítems, dejando título y "Cerrar sesión" fijos.
    <GlassCard className="hidden h-full w-64 shrink-0 flex-col gap-6 overflow-hidden p-5 lg:flex">
      <div className="shrink-0">
        <InstitutionBrand layout="horizontal" />
        <div className="mt-4 text-sm font-semibold text-ink-faint">{titulo}</div>
        {usuario && (
          <div className="mt-1 truncate font-heading text-base font-semibold text-ink">
            {usuario.nombre} {usuario.apellido}
          </div>
        )}
      </div>

      {/* overflow-x-hidden explícito: sin esto, fijar solo overflow-y hace que el
          browser compute overflow-x como "auto" también (no "visible") — el
          hover:translate-x-[3px] de los NavLink alcanza para gatillar un scroll
          horizontal fantasma en el menú. */}
      <nav
        ref={containerRef}
        className="scroll-fade-y relative flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto overflow-x-hidden"
      >
        {rect && (
          <div
            aria-hidden="true"
            className="absolute left-0 z-0 rounded-lg bg-gradient-to-br from-brand to-accent-2 shadow-sm transition-transform duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]"
            style={{ transform: `translateY(${rect.top}px)`, width: rect.width, height: rect.height }}
          />
        )}
        {principales.map((item) => (
          <ItemNav key={item.to} {...item} />
        ))}
      </nav>

      <div className="shrink-0 border-t border-border pt-3">
        {opciones && <ItemNav {...opciones} />}
        <button
          type="button"
          onClick={logout}
          className="mt-1 flex w-full shrink-0 items-center gap-2.5 rounded-lg px-3 py-2.5 text-left text-sm font-medium text-ink-faint transition-colors hover:text-danger"
        >
          Cerrar sesión
        </button>
      </div>
    </GlassCard>
  );
}
