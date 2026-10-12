import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { MobileMenu } from '@/components/MobileMenu';
import { NotificationBell } from '@/features/notifications';
import { ThemeToggle } from '@/components/ThemeToggle';
import { useAuth } from '@/context/AuthContext';
import { usePerfilPropio, useAvatar } from '@/features/identity/hooks/useMiPerfil';

function iniciales(nombre, apellido) {
  return `${nombre?.[0] ?? ''}${apellido?.[0] ?? ''}`.toUpperCase();
}

export function TopHeader({ titulo, items }) {
  const { usuario } = useAuth();
  const { data: perfil } = usePerfilPropio();
  const { data: avatarUrl } = useAvatar(!!perfil && !!perfil.hasAvatar);

  return (
    <div className="mx-auto w-full max-w-[1200px] shrink-0">
      <GlassCard className="flex items-center justify-between gap-3 px-4 py-3">
        <div className="flex min-w-0 items-center gap-3">
          <MobileMenu titulo={titulo} items={items} />
          <div className="truncate font-heading text-base font-bold text-ink lg:hidden">{titulo}</div>
        </div>

        <div className="flex shrink-0 items-center gap-3">
          {usuario && (
            <Link to="/perfil" className="flex items-center gap-2.5 rounded-lg transition-opacity hover:opacity-80">
              <div className="h-9 w-9 overflow-hidden rounded-xl">
                {avatarUrl ? (
                  <img src={avatarUrl} alt="Avatar" className="h-full w-full object-cover" />
                ) : (
                  <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-primary to-brand font-heading text-[13px] font-bold text-white">
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
            </Link>
          )}
          <ThemeToggle />
          <NotificationBell />
        </div>
      </GlassCard>
    </div>
  );
}
