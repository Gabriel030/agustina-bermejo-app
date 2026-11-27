import { Link, useLocation } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { GlassCard } from '@/components/GlassCard';
import { MobileMenu } from '@/components/MobileMenu';
import { NotificationBell } from '@/features/notifications';
import { ThemeToggle } from '@/components/ThemeToggle';
import { useAuth } from '@/context/AuthContext';
import { useActiveRole } from '@/hooks/useActiveRole';
import { TOP_NAV_BREAKPOINTS } from '@/components/topNavBreakpoint';
import { usePerfilPropio, useAvatar } from '@/features/identity';

function iniciales(nombre, apellido) {
  return `${nombre?.[0] ?? ''}${apellido?.[0] ?? ''}`.toUpperCase();
}

const MENU_PATH_ALIASES = [{ from: '/cierre-de-comision/', title: 'Cierre de comisión' }];

function pageTitleFor(items, pathname, fallback) {
  const alias = MENU_PATH_ALIASES.find(({ from }) => pathname.includes(from));
  if (alias) return alias.title;
  const matches = items.filter((item) => pathname === item.to || pathname.startsWith(`${item.to}/`));
  const current = matches.sort((a, b) => b.to.length - a.to.length)[0];
  return current?.label ?? fallback;
}

export function TopHeader({ titulo, items, groups, className, breakpoint = TOP_NAV_BREAKPOINTS.standard }) {
  const { usuario } = useAuth();
  const { pathname } = useLocation();
  const { canSwitch } = useActiveRole();
  const mobileTitle = pageTitleFor(items, pathname, titulo);
  const { data: perfil } = usePerfilPropio();
  const { data: avatarUrl } = useAvatar(!!perfil && !!perfil.hasAvatar);

  return (
    <div className={cn('mx-auto w-full max-w-[1280px] shrink-0 px-4 pt-4', className)}>
      <GlassCard className="px-3 py-2">
        <div className="mx-auto flex min-h-11 w-full items-center justify-between gap-2 sm:gap-3">
          <div className="flex min-w-0 items-center gap-2 sm:gap-3">
            <MobileMenu titulo={titulo} items={items} groups={groups} hiddenFrom={breakpoint.mobileHidden} />
            <div className={cn('font-heading text-size-15 font-bold sm:text-base text-ink whitespace-nowrap truncate', breakpoint.mobileHidden)}>{mobileTitle}</div>
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
