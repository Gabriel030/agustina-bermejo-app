import { NavLink } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { GlassCard } from '@/components/GlassCard';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { useAuth } from '@/context/AuthContext';

// Clase del ítem activo — degradé + blanco (mismo tratamiento que el resto de
// los elementos "seleccionados" del sistema, ej. tabs de Catálogo), no el pill
// pálido (bg-brand = --accent-soft, muy claro) que se usaba antes: el usuario
// encontró que el texto se leía mal encima de ese fondo tan claro.
const NAV_ITEM_ACTIVO = 'bg-gradient-to-br from-brand to-accent-2 text-white shadow-sm';
const NAV_ITEM_BASE =
  'flex shrink-0 items-center gap-2.5 rounded-lg px-3 py-2.5 text-sm font-medium text-ink-soft transition-all duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] hover:translate-x-[3px] hover:bg-neutral-bg hover:text-ink';

function ItemNav({ to, label, icon: Icon }) {
  return (
    <NavLink key={to} to={to} className={({ isActive }) => cn(NAV_ITEM_BASE, isActive && NAV_ITEM_ACTIVO)}>
      {Icon && <Icon className="h-4 w-4 shrink-0" />}
      <span className="truncate">{label}</span>
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
      <nav className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto overflow-x-hidden">
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
