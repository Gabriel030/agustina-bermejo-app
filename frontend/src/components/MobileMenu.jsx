import { useState } from 'react';
import { Dialog as DialogPrimitive } from 'radix-ui';
import { NavLink, useLocation } from 'react-router-dom';
import { Moon, Sun, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { NavGroups } from '@/components/NavGroups';
import { useAuth } from '@/context/AuthContext';
import { RoleSwitcher } from '@/components/RoleSwitcher';
import { useActiveRole } from '@/hooks/useActiveRole';
import { useTheme } from '@/context/ThemeContext';
import { isDesignB } from '@/theme/design';

// Mismo tratamiento del ítem activo que Sidebar.jsx (degradé + blanco, no el
// pill pálido de antes) — ver comentario ahí.
const NAV_ITEM_ACTIVO =
  "bg-(image:--nav-indicator-bg) text-(--nav-active-fg) shadow-sm design-b:bg-none design-b:bg-(color:--nav-indicator-bg) design-b:relative design-b:font-bold design-b:shadow-none design-b:before:absolute design-b:before:inset-y-2 design-b:before:left-0 design-b:before:w-[3px] design-b:before:rounded-r-[3px] design-b:before:bg-(color:--accent) design-b:before:content-['']";
const NAV_ITEM_BASE =
  'flex shrink-0 items-center gap-2.5 rounded-control px-3 py-2.5 text-sm font-medium text-ink-soft transition-colors hover:bg-neutral-bg hover:text-ink design-b:min-h-11 design-b:py-0 design-b:hover:bg-(--row-hover)';

// Entrada escalonada (index * 40ms) al abrir el drawer, en vez de que todos
// los ítems aparezcan juntos — animate-in/fade-in/slide-in-from-left son de
// tw-animate-css (ya en uso en el Overlay/Content de este mismo Dialog).
function ItemNav({ to, label, icon: Icon, onNavigate, index }) {
  return (
    <NavLink
      key={to}
      to={to}
      onClick={onNavigate}
      style={{ animationDelay: `${index * 40}ms` }}
      className={({ isActive }) =>
        cn(NAV_ITEM_BASE, 'animate-in fade-in slide-in-from-left-2 fill-mode-both', isActive && NAV_ITEM_ACTIVO)
      }
    >
      {Icon && <Icon className="h-4 w-4 shrink-0" />}
      <span className="truncate">{label}</span>
    </NavLink>
  );
}

// Botón hamburguesa cuyas 3 líneas giran y forman una X, en vez de cambiar de
// ícono (Menu → X) en seco.
function HamburgerIcon({ open }) {
  return (
    <span className="flex h-4 w-5 shrink-0 flex-col items-center justify-center gap-[5px]">
      <span
        className={cn(
          'h-0.5 w-5 rounded-full bg-current transition-transform duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]',
          open && 'translate-y-[6.5px] rotate-45',
        )}
      />
      <span
        className={cn('h-0.5 w-5 rounded-full bg-current transition-opacity duration-200', open && 'opacity-0')}
      />
      <span
        className={cn(
          'h-0.5 w-5 rounded-full bg-current transition-transform duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]',
          open && '-translate-y-[6.5px] -rotate-45',
        )}
      />
    </span>
  );
}

// Colapso del Sidebar en mobile/tablet (base/md) — decisión revisada con el
// usuario: un menú hamburguesa que abre un drawer lateral, no una bottom tab
// bar (esa era la convención documentada originalmente en
// docs/context/frontend.md, reemplazada acá a pedido explícito). El drawer
// reusa radix-ui Dialog directo (no el wrapper de components/ui/dialog.jsx,
// que centra el contenido) para poder posicionarlo pegado al borde izquierdo.
export function MobileMenu({ titulo, items, groups }) {
  const [open, setOpen] = useState(false);
  const { usuario, logout } = useAuth();
  const { tema, alternarTema } = useTheme();
  const { pathname } = useLocation();
  const { canSwitch } = useActiveRole();
  const designB = isDesignB();

  const principales = items.filter((item) => item.label !== 'Opciones');
  const opciones = designB ? undefined : items.find((item) => item.label === 'Opciones');
  const grouped = designB && groups;
  const dark = tema === 'dark';

  return (
    <DialogPrimitive.Root open={open} onOpenChange={setOpen}>
      <DialogPrimitive.Trigger asChild>
        <button
          type="button"
          aria-label={open ? 'Cerrar menú' : 'Abrir menú'}
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-border bg-surface text-ink backdrop-blur-(--surface-blur) backdrop-saturate-(--surface-saturate) lg:hidden"
        >
          <HamburgerIcon open={open} />
        </button>
      </DialogPrimitive.Trigger>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-(--overlay-bg-menu) duration-150 data-open:animate-in data-open:fade-in-0 data-closed:animate-out data-closed:fade-out-0" />
        <DialogPrimitive.Content
          aria-describedby={undefined}
          className="fixed inset-y-0 left-0 z-50 flex w-72 max-w-[80vw] flex-col gap-6 border-r border-border bg-surface p-5 shadow-overlay backdrop-blur-(--surface-blur) backdrop-saturate-(--surface-saturate) duration-200 data-open:animate-in data-open:slide-in-from-left data-closed:animate-out data-closed:slide-out-to-left"
        >
          <div className="flex items-start justify-between">
            <DialogPrimitive.Title asChild>
              <div>
                <InstitutionBrand layout="horizontal" />
                {!canSwitch && <div className="mt-4 text-sm font-semibold text-ink-faint">{titulo}</div>}
                {usuario && (
                  <div className="mt-1 truncate font-heading text-base font-semibold text-ink">
                    {usuario.nombre} {usuario.apellido}
                  </div>
                )}
              </div>
            </DialogPrimitive.Title>
            <DialogPrimitive.Close asChild>
              <button type="button" aria-label="Cerrar menú" className="rounded-control p-1.5 text-ink-faint hover:text-ink design-b:flex design-b:h-11 design-b:w-11 design-b:items-center design-b:justify-center design-b:p-0">
                <X className="h-5 w-5" />
              </button>
            </DialogPrimitive.Close>
          </div>

          <RoleSwitcher variant="inline" onNavigate={() => setOpen(false)} />

          <nav className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
            {grouped ? (
              <NavGroups
                groups={groups}
                collapsible
                pathname={pathname}
                renderItem={(item, index) => <ItemNav {...item} index={index} onNavigate={() => setOpen(false)} />}
              />
            ) : (
              principales.map((item, index) => (
                <ItemNav key={item.to} {...item} index={index} onNavigate={() => setOpen(false)} />
              ))
            )}
          </nav>

          <div className="shrink-0 border-t border-border pt-3">
            {opciones && <ItemNav {...opciones} onNavigate={() => setOpen(false)} />}
            {designB && (
              <button type="button" onClick={alternarTema} className={cn(NAV_ITEM_BASE, 'w-full text-left')}>
                {dark ? <Sun aria-hidden="true" className="h-4 w-4 shrink-0" /> : <Moon aria-hidden="true" className="h-4 w-4 shrink-0" />}
                {dark ? 'Tema claro' : 'Tema oscuro'}
              </button>
            )}
            <button
              type="button"
              onClick={() => {
                setOpen(false);
                logout();
              }}
              className="mt-1 flex w-full shrink-0 items-center gap-2.5 rounded-control px-3 py-2.5 text-left text-sm font-medium text-ink-faint transition-colors hover:text-danger design-b:min-h-11 design-b:py-0"
            >
              Cerrar sesión
            </button>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
