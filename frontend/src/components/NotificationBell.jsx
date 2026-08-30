import { useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { useMisNotificaciones, useMarcarNotificacionLeida } from '@/hooks/useNotifications';
import { TIPO_NOTIFICACION_LABEL, tiempoRelativo } from '@/lib/notificaciones';
import { useOnClickOutside } from '@/hooks/useOnClickOutside';

export function NotificationBell() {
  const [abierto, setAbierto] = useState(false);
  const contenedorRef = useRef(null);
  const { data: notificaciones } = useMisNotificaciones();
  const marcarLeida = useMarcarNotificacionLeida();

  useOnClickOutside(contenedorRef, () => setAbierto(false));

  const lista = notificaciones ?? [];
  const noLeidas = lista.filter((n) => !n.read).length;

  return (
    <div ref={contenedorRef} className="relative">
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        className="relative flex h-10 w-10 items-center justify-center rounded-xl border border-border bg-white/60 text-ink-faint transition-transform hover:scale-105"
      >
        <Bell className="h-[17px] w-[17px]" />
        {noLeidas > 0 && (
          <span className="absolute -right-1 -top-1 flex h-[15px] min-w-[15px] items-center justify-center rounded-full border-2 border-bg1 bg-danger px-1 text-[8.5px] font-bold text-white">
            {noLeidas > 9 ? '9+' : noLeidas}
          </span>
        )}
      </button>

      {abierto && (
        <GlassCard className="absolute right-0 top-12 z-50 flex max-h-96 w-80 flex-col overflow-y-auto p-2">
          <div className="px-2 py-1.5 font-heading text-[13px] font-semibold text-ink">Notificaciones</div>
          {lista.length === 0 ? (
            <EmptyState title="Nada por ahora" className="py-6" />
          ) : (
            lista.map((n) => (
              <button
                key={n.id}
                onClick={() => !n.read && marcarLeida.mutate(n.id)}
                className={`flex flex-col gap-0.5 rounded-xl px-3 py-2.5 text-left transition-colors hover:bg-neutral-bg/60 ${
                  !n.read ? 'bg-accent-soft/40' : ''
                }`}
              >
                <div className="flex items-center gap-2">
                  {!n.read && <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-accent" />}
                  <span className="text-[12px] font-bold text-ink">{TIPO_NOTIFICACION_LABEL[n.type] ?? n.type}</span>
                </div>
                <div className="text-[12px] text-ink-soft">{n.message}</div>
                <div className="text-[10.5px] text-ink-faint">{tiempoRelativo(n.createdAt)}</div>
              </button>
            ))
          )}
        </GlassCard>
      )}
    </div>
  );
}
