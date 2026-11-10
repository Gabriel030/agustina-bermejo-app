import { Link, useLocation } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { MobileMenu } from '@/components/MobileMenu';
import { NotificationBell } from '@/features/notifications';
import { ThemeToggle } from '@/components/ThemeToggle';
import { useAuth } from '@/context/AuthContext';
import { isDesignB } from '@/theme/design';
import { usePerfilPropio, useAvatar } from '@/features/identity/hooks/useMiPerfil';

function iniciales(nombre, apellido) {
  return `${nombre?.[0] ?? ''}${apellido?.[0] ?? ''}`.toUpperCase();
}

function pageTitleFor(items, pathname, fallback) {
  const matches = items.filter((item) => pathname === item.to || pathname.startsWith(`${item.to}/`));
  const current = matches.sort((a, b) => b.to.length - a.to.length)[0];
  return current?.label ?? fallback;
}

export function TopHeader({ titulo, items, groups }) {
  const { usuario } = useAuth();
  const { pathname } = useLocation();
  const designB = isDesignB();
  const mobileTitle = designB ? pageTitleFor(items, pathname, titulo) : titulo;
  const { data: perfil } = usePerfilPropio();
  const { data: avatarUrl } = useAvatar(!!perfil && !!perfil.hasAvatar);

  return (
    <div className="mx-auto w-full max-w-[1200px] shrink-0 design-b:max-w-none">
      <GlassCard className="design-b:rounded-none design-b:border-0 design-b:border-b px-3 py-3 sm:px-4 sm:design-b:px-8">
        <div className="mx-auto flex w-full max-w-[1200px] items-center justify-between gap-2 sm:gap-3">
          <div className="flex min-w-0 items-center gap-2 sm:gap-3">
            <MobileMenu titulo={titulo} items={items} groups={groups} />
            <div className="font-heading text-[15px] font-bold sm:text-base text-ink lg:hidden whitespace-nowrap truncate">{mobileTitle}</div>
          </div>

          <div className="flex shrink-0 items-center gap-1.5 sm:gap-3">
            {usuario && (
              <Link to="/perfil" className="flex min-h-11 items-center gap-2.5 rounded-control transition-opacity hover:opacity-80">
                <div className="h-9 w-9 overflow-hidden rounded-xl design-b:rounded-control">
                  {avatarUrl ? (
                    <img src={avatarUrl} alt="Avatar" className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-primary to-brand font-heading text-[13px] font-bold text-white design-b:bg-none design-b:bg-(color:--accent-soft) design-b:text-(--accent)">
                      {iniciales(usuario.nombre, usuario.apellido)}
                    </div>
                  )}
                </div>
                <div className="hidden sm:block">
                  <div className="truncate text-[13px] font-bold leading-tight text-ink">
                    {usuario.nombre} {usuario.apellido}
                  </div>
                  <div className="truncate text-[11px] leading-tight text-ink-faint">{titulo}</div>
                </div>
                {designB && <span className="sr-only">Mi cuenta</span>}
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
