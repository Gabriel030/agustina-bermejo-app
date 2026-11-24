import { Link, useLocation } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { MobileMenu } from '@/components/MobileMenu';
import { NotificationBell } from '@/features/notifications';
import { ThemeToggle } from '@/components/ThemeToggle';
import { useAuth } from '@/context/AuthContext';
import { useActiveRole } from '@/hooks/useActiveRole';
import { usePerfilPropio, useAvatar } from '@/features/identity';

function iniciales(nombre, apellido) {
  return `${nombre?.[0] ?? ''}${apellido?.[0] ?? ''}`.toUpperCase();
}

const MENU_PATH_ALIASES = [['/cierre-de-comision/', '/cierres-de-comision/']];

function pageTitleFor(items, pathname, fallback) {
  const menuPathname = MENU_PATH_ALIASES.reduce((path, [from, to]) => path.replace(from, to), pathname);
  const matches = items.filter((item) => menuPathname === item.to || menuPathname.startsWith(`${item.to}/`));
  const current = matches.sort((a, b) => b.to.length - a.to.length)[0];
  return current?.label ?? fallback;
}

export function TopHeader({ titulo, items, groups }) {
  const { usuario } = useAuth();
  const { pathname } = useLocation();
  const { canSwitch } = useActiveRole();
  const mobileTitle = pageTitleFor(items, pathname, titulo);
  const { data: perfil } = usePerfilPropio();
  const { data: avatarUrl } = useAvatar(!!perfil && !!perfil.hasAvatar);

  return (
    <div className="mx-auto w-full max-w-none shrink-0">
      <GlassCard className="rounded-none border-0 border-b px-3 py-3 sm:px-8">
        <div className="mx-auto flex w-full max-w-[1200px] items-center justify-between gap-2 sm:gap-3">
          <div className="flex min-w-0 items-center gap-2 sm:gap-3">
            <MobileMenu titulo={titulo} items={items} groups={groups} />
            <div className="font-heading text-size-15 font-bold sm:text-base text-ink lg:hidden whitespace-nowrap truncate">{mobileTitle}</div>
          </div>

          <div className="flex shrink-0 items-center gap-1.5 sm:gap-3">
            {usuario && (
              <Link to="/perfil" className="flex min-h-11 items-center gap-2.5 rounded-control transition-opacity hover:opacity-80">
                <div className="h-9 w-9 overflow-hidden rounded-control">
                  {avatarUrl ? (
                    <img src={avatarUrl} alt="Avatar" className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center bg-accent-soft font-heading text-size-13 font-bold text-(--accent)">
                      {iniciales(usuario.nombre, usuario.apellido)}
                    </div>
                  )}
                </div>
                <div className="hidden sm:block">
                  <div className="truncate text-size-13 font-bold leading-tight text-ink">
                    {usuario.nombre} {usuario.apellido}
                  </div>
                  {!canSwitch && <div className="truncate text-size-11 leading-tight text-ink-faint">{titulo}</div>}
                </div>
                <span className="sr-only">Mi cuenta</span>
              </Link>
            )}
            <ThemeToggle />
            <NotificationBell />
          </div>
        </div>
      </GlassCard>
    </div>
  );
}
