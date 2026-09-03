import { useState } from 'react';
import { Dialog as DialogPrimitive } from 'radix-ui';
import { NavLink } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { useAuth } from '@/context/AuthContext';

// Mismo tratamiento del ítem activo que Sidebar.jsx (degradé + blanco, no el
// pill pálido de antes) — ver comentario ahí.
const NAV_ITEM_ACTIVO = 'bg-gradient-to-br from-brand to-accent-2 text-white shadow-sm';
const NAV_ITEM_BASE =
  'flex shrink-0 items-center gap-2.5 rounded-lg px-3 py-2.5 text-sm font-medium text-ink-soft transition-colors hover:bg-neutral-bg hover:text-ink';

function ItemNav({ to, label, icon: Icon, onNavigate }) {
  return (
    <NavLink key={to} to={to} onClick={onNavigate} className={({ isActive }) => cn(NAV_ITEM_BASE, isActive && NAV_ITEM_ACTIVO)}>
      {Icon && <Icon className="h-4 w-4 shrink-0" />}
      <span className="truncate">{label}</span>
    </NavLink>
  );
}

// Colapso del Sidebar en mobile/tablet (base/md) — decisión revisada con el
// usuario: un menú hamburguesa que abre un drawer lateral, no una bottom tab
// bar (esa era la convención documentada originalmente en
// docs/context/frontend.md, reemplazada acá a pedido explícito). El drawer
// reusa radix-ui Dialog directo (no el wrapper de components/ui/dialog.jsx,
// que centra el contenido) para poder posicionarlo pegado al borde izquierdo.
export function MobileMenu({ titulo, items }) {
  const [open, setOpen] = useState(false);
  const { usuario, logout } = useAuth();

  const principales = items.filter((item) => item.label !== 'Opciones');
  const opciones = items.find((item) => item.label === 'Opciones');

  return (
    <DialogPrimitive.Root open={open} onOpenChange={setOpen}>
      <DialogPrimitive.Trigger asChild>
        <button
          type="button"
          aria-label="Abrir menú"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-border bg-surface text-ink backdrop-blur-[28px] backdrop-saturate-[1.6] lg:hidden"
        >
          <Menu className="h-5 w-5" />
        </button>
      </DialogPrimitive.Trigger>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/20 duration-150 data-open:animate-in data-open:fade-in-0 data-closed:animate-out data-closed:fade-out-0" />
        <DialogPrimitive.Content
          aria-describedby={undefined}
          className="fixed inset-y-0 left-0 z-50 flex w-72 max-w-[80vw] flex-col gap-6 border-r border-border bg-surface p-5 shadow-2xl backdrop-blur-[28px] backdrop-saturate-[1.6] duration-200 data-open:animate-in data-open:slide-in-from-left data-closed:animate-out data-closed:slide-out-to-left"
        >
          <div className="flex items-start justify-between">
            <DialogPrimitive.Title asChild>
              <div>
                <InstitutionBrand layout="horizontal" />
                <div className="mt-4 text-sm font-semibold text-ink-faint">{titulo}</div>
                {usuario && (
                  <div className="mt-1 truncate font-heading text-base font-semibold text-ink">
                    {usuario.nombre} {usuario.apellido}
                  </div>
                )}
              </div>
            </DialogPrimitive.Title>
            <DialogPrimitive.Close asChild>
              <button type="button" aria-label="Cerrar menú" className="rounded-lg p-1.5 text-ink-faint hover:text-ink">
                <X className="h-5 w-5" />
              </button>
            </DialogPrimitive.Close>
          </div>

          <nav className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
            {principales.map((item) => (
              <ItemNav key={item.to} {...item} onNavigate={() => setOpen(false)} />
            ))}
          </nav>

          <div className="shrink-0 border-t border-border pt-3">
            {opciones && <ItemNav {...opciones} onNavigate={() => setOpen(false)} />}
            <button
              type="button"
              onClick={() => {
                setOpen(false);
                logout();
              }}
              className="mt-1 flex w-full shrink-0 items-center gap-2.5 rounded-lg px-3 py-2.5 text-left text-sm font-medium text-ink-faint transition-colors hover:text-danger"
            >
              Cerrar sesión
            </button>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
