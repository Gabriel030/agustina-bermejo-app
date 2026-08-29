import { useState } from 'react';
import { Dialog as DialogPrimitive } from 'radix-ui';
import { NavLink } from 'react-router-dom';
import { Menu, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuth } from '@/context/AuthContext';

// Colapso del Sidebar en mobile/tablet (base/md) — decisión revisada con el
// usuario: un menú hamburguesa que abre un drawer lateral, no una bottom tab
// bar (esa era la convención documentada originalmente en
// docs/context/frontend.md, reemplazada acá a pedido explícito). El drawer
// reusa radix-ui Dialog directo (no el wrapper de components/ui/dialog.jsx,
// que centra el contenido) para poder posicionarlo pegado al borde izquierdo.
export function MobileMenu({ titulo, items }) {
  const [open, setOpen] = useState(false);
  const { usuario, logout } = useAuth();

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
            <div>
              <DialogPrimitive.Title className="text-sm font-semibold text-ink-faint">{titulo}</DialogPrimitive.Title>
              {usuario && (
                <div className="mt-1 truncate font-heading text-base font-semibold text-ink">
                  {usuario.nombre} {usuario.apellido}
                </div>
              )}
            </div>
            <DialogPrimitive.Close asChild>
              <button type="button" aria-label="Cerrar menú" className="rounded-lg p-1.5 text-ink-faint hover:text-ink">
                <X className="h-5 w-5" />
              </button>
            </DialogPrimitive.Close>
          </div>

          <nav className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
            {items.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                onClick={() => setOpen(false)}
                className={({ isActive }) =>
                  cn(
                    'shrink-0 rounded-lg px-3 py-2.5 text-sm font-medium text-ink-soft transition-colors hover:bg-accent hover:text-ink',
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
            onClick={() => {
              setOpen(false);
              logout();
            }}
            className="shrink-0 rounded-lg px-3 py-2.5 text-left text-sm font-medium text-ink-faint transition-colors hover:text-danger"
          >
            Cerrar sesión
          </button>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
