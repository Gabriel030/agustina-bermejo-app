import { Bell } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { EmptyState } from '@/components/EmptyState';
import { useMisNotificaciones, useMarcarNotificacionLeida } from '@/hooks/useNotifications';
import { TIPO_NOTIFICACION_LABEL, TONO_NOTIFICACION, tiempoRelativo } from '@/lib/notificaciones';

const BORDE_TONO = {
  warning: 'border-l-warning',
  success: 'border-l-success',
  danger: 'border-l-danger',
  accent2: 'border-l-accent-2',
};

const TEXTO_TONO = {
  warning: 'text-warning',
  success: 'text-success',
  danger: 'text-danger',
  accent2: 'text-accent-2',
};

export function NotificationBell() {
  const { data: notificaciones } = useMisNotificaciones();
  const marcarLeida = useMarcarNotificacionLeida();

  const lista = notificaciones ?? [];
  const noLeidas = lista.filter((n) => !n.read).length;

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-border bg-white/60 text-ink-faint transition-transform hover:scale-105"
        >
          <Bell className="h-[17px] w-[17px]" />
          {noLeidas > 0 && (
            <span className="absolute -right-1 -top-1 flex h-[15px] min-w-[15px] items-center justify-center rounded-full border-2 border-bg1 bg-danger px-1 text-[8.5px] font-bold text-white">
              {noLeidas > 9 ? '9+' : noLeidas}
            </span>
          )}
        </button>
      </PopoverTrigger>

      <PopoverContent
        align="end"
        sideOffset={8}
        className="flex max-h-[26rem] w-[min(22rem,calc(100vw-2.5rem))] flex-col gap-0 overflow-hidden rounded-xl border border-border bg-surface p-0 text-ink shadow-2xl backdrop-blur-[28px] backdrop-saturate-[1.6]"
      >
        <div className="shrink-0 border-b border-border px-4 py-2.5 font-heading text-[13px] font-semibold text-ink">Notificaciones</div>
        <div className="flex flex-col overflow-y-auto">
          {lista.length === 0 ? (
            <EmptyState title="Nada por ahora" className="py-6" />
          ) : (
            lista.map((n) => {
              const tono = TONO_NOTIFICACION[n.type] ?? 'accent2';
              return (
                <button
                  key={n.id}
                  onClick={() => !n.read && marcarLeida.mutate(n.id)}
                  className={`flex flex-col gap-0.5 border-b border-border border-l-4 px-3 py-2.5 text-left transition-colors last:border-b-0 hover:bg-neutral-bg/60 ${
                    BORDE_TONO[tono]
                  } ${!n.read ? 'bg-accent-soft/30' : ''}`}
                >
                  <div className="flex items-center gap-2">
                    {!n.read && <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-brand" />}
                    <span className={`text-[12px] font-bold ${TEXTO_TONO[tono]}`}>{TIPO_NOTIFICACION_LABEL[n.type] ?? n.type}</span>
                  </div>
                  <div className="text-[12px] text-ink-soft">{n.message}</div>
                  <div className="text-[10.5px] text-ink-faint">{tiempoRelativo(n.createdAt)}</div>
                </button>
              );
            })
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}
