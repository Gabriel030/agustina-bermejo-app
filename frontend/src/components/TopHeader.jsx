import { GlassCard } from '@/components/GlassCard';
import { MobileMenu } from '@/components/MobileMenu';
import { NotificationBell } from '@/components/NotificationBell';
import { useAuth } from '@/context/AuthContext';

function iniciales(nombre, apellido) {
  return `${nombre?.[0] ?? ''}${apellido?.[0] ?? ''}`.toUpperCase();
}

export function TopHeader({ titulo, items }) {
  const { usuario } = useAuth();

  return (
    <div className="mx-auto w-full max-w-[1200px] shrink-0">
      <GlassCard className="flex items-center justify-between gap-3 px-4 py-3">
        <div className="flex min-w-0 items-center gap-3">
          <MobileMenu titulo={titulo} items={items} />
          <div className="truncate font-heading text-base font-bold text-ink lg:hidden">{titulo}</div>
        </div>

        <div className="flex shrink-0 items-center gap-3">
          {usuario && (
            <div className="flex items-center gap-2.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-brand font-heading text-[13px] font-bold text-white">
                {iniciales(usuario.nombre, usuario.apellido)}
              </div>
              <div className="hidden sm:block">
                <div className="truncate text-[13px] font-bold leading-tight text-ink">
                  {usuario.nombre} {usuario.apellido}
                </div>
                <div className="truncate text-[11px] leading-tight text-ink-faint">{titulo}</div>
              </div>
            </div>
          )}
          <NotificationBell />
        </div>
      </GlassCard>
    </div>
  );
}
