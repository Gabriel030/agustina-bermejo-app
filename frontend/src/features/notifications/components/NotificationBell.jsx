import { Button } from '@/components/ui/button';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, TriangleAlert } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { EmptyState } from '@/components/EmptyState';
import { Paginador } from '@/components/Paginador';
import { NOTIFICATIONS_PAGE_SIZE, useMisNotificaciones, useNotificacionesNoLeidas, useMarcarNotificacionLeida } from '@/features/notifications/hooks/useNotifications';
import { StaleStamp } from '@/components/offline/StaleStamp';
import { useActiveRole } from '@/hooks/useActiveRole';
import { notificationDestination } from '@/lib/notificationDestination';
import { TONO_NOTIFICACION, etiquetaNotificacion, tiempoRelativo } from '@/lib/notificaciones';

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
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const { activeRole } = useActiveRole();
  const [page, setPage] = useState(0);
  const { data: notificaciones, isError, isFetching, refetch } = useMisNotificaciones({ page, enabled: open });
  const { data: unread, isError: countFailed, isFetching: countFetching, dataUpdatedAt: countUpdatedAt } = useNotificacionesNoLeidas();
  const marcarLeida = useMarcarNotificacionLeida();

  const lista = notificaciones?.items ?? [];
  const totalPages = notificaciones ? Math.max(1, Math.ceil(notificaciones.totalElements / (notificaciones.size || NOTIFICATIONS_PAGE_SIZE))) : 1;
  const noLeidas = unread?.count ?? 0;
  const badgeFailed = countFailed && unread === undefined;
  const loadFailed = isError && notificaciones === undefined;

  function handleOpenChange(next) {
    setOpen(next);
    if (!next) setPage(0);
  }

  function handleClick(n) {
    if (!n.read) marcarLeida.mutate(n.id);
    const destination = notificationDestination(n, activeRole);
    if (!destination) return;
    handleOpenChange(false);
    navigate(destination);
  }

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label={
            badgeFailed
              ? 'Notificaciones, no pudimos cargarlas'
              : noLeidas > 0
                ? `Notificaciones, ${noLeidas} sin leer`
                : 'Notificaciones'
          }
          className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-border bg-surface text-ink-faint transition-transform hover:scale-105"
        >
          <Bell aria-hidden="true" className="h-[17px] w-[17px]" />
          {badgeFailed && (
            <span
              aria-hidden="true"
              data-testid="bell-error-indicator"
              className="absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full border-2 border-bg1 bg-warning"
            />
          )}
          {!badgeFailed && noLeidas > 0 && (
            <span
              aria-hidden="true"
              className="absolute -right-1 -top-1 flex h-4 min-w-4 animate-[badge-pulse_1.8s_ease-in-out_infinite] items-center justify-center rounded-full border-2 border-bg1 bg-danger px-1 text-xs font-bold leading-none text-on-accent motion-reduce:animate-none design-b:animate-none"
            >
              {noLeidas > 9 ? '9+' : noLeidas}
            </span>
          )}
        </button>
      </PopoverTrigger>

      <PopoverContent
        align="end"
        aria-label="Notificaciones"
        sideOffset={8}
        className="flex max-h-[26rem] w-[min(22rem,calc(100vw-2.5rem))] flex-col gap-0 overflow-hidden rounded-xl border border-border bg-surface p-0 text-ink shadow-overlay backdrop-blur-(--surface-blur) backdrop-saturate-(--surface-saturate)"
      >
        <div className="flex shrink-0 items-center gap-2 border-b border-border px-4 py-2.5 font-heading text-size-13 font-semibold text-ink">
          Notificaciones
          <StaleStamp dataUpdatedAt={countUpdatedAt} isFetching={countFetching} isError={countFailed} hasData={unread !== undefined} />
        </div>
        <div className="flex flex-col overflow-y-auto">
          {loadFailed ? (
            <div role="alert" className="flex flex-col items-center gap-2 px-4 py-6 text-center">
              <TriangleAlert aria-hidden="true" className="h-5 w-5 text-warning" />
              <p className="text-sm text-ink-soft">No pudimos cargar tus notificaciones.</p>
              <Button
                variant="outline"
                size="sm"
                type="button"
                onClick={() => refetch()}
                disabled={isFetching}
                className="px-3 font-semibold text-ink">
                Reintentar
              </Button>
            </div>
          ) : lista.length === 0 ? (
            <EmptyState title="Nada por ahora" className="py-6" />
          ) : (
            lista.map((n) => {
              const tono = TONO_NOTIFICACION[n.type] ?? 'accent2';
              return (
                <button
                  key={n.id}
                  type="button"
                  onClick={() => handleClick(n)}
                  className={`flex flex-col gap-0.5 border-b border-border border-l-4 px-3 py-2.5 text-left transition-colors last:border-b-0 hover:bg-neutral-bg/60 ${
                    BORDE_TONO[tono]
                  } ${!n.read ? 'bg-accent-soft/30' : ''}`}
                >
                  <div className="flex items-center gap-2">
                    <span
                      aria-hidden="true"
                      className={`h-1.5 w-1.5 shrink-0 rounded-full bg-brand transition-all duration-300 ${
                        n.read ? 'scale-0 opacity-0' : 'scale-100 opacity-100'
                      }`}
                    />
                    <span className={`text-xs font-bold ${TEXTO_TONO[tono]}`}>{etiquetaNotificacion(n.type)}</span>
                    {!n.read && <span className="sr-only">Sin leer</span>}
                  </div>
                  <div className="text-xs text-ink-soft">{n.message}</div>
                  <div className="text-xs text-ink-faint">{tiempoRelativo(n.createdAt)}</div>
                </button>
              );
            })
          )}
          <Paginador page={page + 1} totalPages={totalPages} onChange={(next) => setPage(next - 1)} />
        </div>
      </PopoverContent>
    </Popover>
  );
}
