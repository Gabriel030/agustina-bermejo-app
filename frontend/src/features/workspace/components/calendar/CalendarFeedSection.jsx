import { Check, Copy } from 'lucide-react';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { describeError } from '@/api/describeError';
import { useCalendarFeedPanel } from '@/features/workspace/hooks/useCalendarFeedPanel';
import { useCopyToClipboard } from '@/features/workspace/hooks/useCopyToClipboard';
import { formatFeedDate, formatFeedLastRead } from '@/features/workspace/lib/calendarFeed';

const SETUP_STEPS = [
  'En Google Calendar, abrí Otros calendarios y elegí Desde URL.',
  'Pegá el enlace y tocá Agregar calendario.',
  'Google puede tardar unas horas en mostrar los cambios.',
];

function IssuedFeedLink({ issued }) {
  const { copied, copy } = useCopyToClipboard();
  return (
    <div className="flex flex-col gap-3">
      {issued.regenerated && (
        <Notice tone="success" role="status">
          Enlace nuevo listo. El anterior ya no funciona. Pegá este en tu calendario.
        </Notice>
      )}
      <Input
        readOnly
        aria-label="Enlace de suscripción"
        value={issued.url}
        onFocus={(event) => event.target.select()}
        className="min-h-11 w-full text-size-13"
      />
      <Button variant="primary" className="min-h-11 self-start" onClick={() => copy(issued.url)}>
        {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
        {copied ? 'Copiado ✓' : 'Copiar enlace'}
      </Button>
      <p className="text-size-13 text-ink-soft">Pegalo una vez en Google Calendar, Apple o Outlook y se actualiza solo.</p>
      <ol className="flex list-decimal flex-col gap-1 pl-5 text-size-13 text-ink-soft">
        {SETUP_STEPS.map((step) => (
          <li key={step}>{step}</li>
        ))}
      </ol>
    </div>
  );
}

function ActiveFeedSummary({ feed }) {
  return (
    <div className="flex flex-col gap-1.5">
      <p className="font-heading text-size-14 font-semibold text-ink">Tenés un enlace activo</p>
      <p className="text-size-13 text-ink-soft tabular-nums">
        Creado el {formatFeedDate(feed.createdAt)} · Última lectura: {formatFeedLastRead(feed.lastUsedAt)}
      </p>
      <p className="text-size-13 text-ink-soft">Por seguridad no podemos volver a mostrarlo. Si lo perdiste, regeneralo.</p>
    </div>
  );
}

function PendingActionConfirmation({ action, onConfirm, onCancel, isPending }) {
  const regenerate = action === 'regenerate';
  return (
    <Notice tone="warning" className="flex flex-col gap-3">
      <span className="block">
        {regenerate
          ? '¿Regenerar el enlace? El enlace actual deja de funcionar y los calendarios que lo usan dejan de actualizarse hasta que pegues el nuevo.'
          : '¿Revocar el enlace? Los calendarios que lo usan dejan de actualizarse.'}
      </span>
      <span className="flex flex-wrap gap-2">
        <Button variant={regenerate ? 'primary' : 'destructive'} className="min-h-11" disabled={isPending} onClick={onConfirm}>
          {regenerate ? 'Sí, regenerar' : 'Sí, revocar'}
        </Button>
        <Button variant="outline" className="min-h-11" onClick={onCancel}>
          Cancelar
        </Button>
      </span>
    </Notice>
  );
}

export function CalendarFeedSection() {
  const panel = useCalendarFeedPanel();
  const { feed, isActive, issued, issueToken, revokeToken } = panel;
  const hasLink = isActive || issued !== null;

  let body;
  if (feed.isPending) body = <Skeleton className="h-20 w-full motion-reduce:animate-none" />;
  else if (feed.isError) {
    body = (
      <Notice tone="danger" role="alert" className="flex flex-col gap-2">
        <span>{describeError(feed.error, { fallback: 'No pudimos cargar el estado de tu enlace.' }).message}</span>
        <Button variant="outline" className="min-h-11 self-start" onClick={() => feed.refetch()}>
          Reintentar
        </Button>
      </Notice>
    );
  } else {
    body = (
      <div className="flex flex-col gap-3">
        {issued ? <IssuedFeedLink issued={issued} /> : isActive && <ActiveFeedSummary feed={feed.data} />}
        {panel.revoked && !hasLink && <Notice tone="success" role="status">Enlace revocado. Los calendarios que lo usaban dejaron de actualizarse.</Notice>}
        {!hasLink && !issueToken.isError && (
          <>
            <p className="text-size-13 text-ink-soft">
              Todavía no generaste tu enlace. Al generarlo, tu calendario se mantiene al día solo.
            </p>
            <Button variant="primary" className="min-h-11 self-start" disabled={issueToken.isPending} onClick={panel.generate}>
              {issueToken.isPending ? 'Generando…' : 'Generar mi enlace'}
            </Button>
          </>
        )}
        {issueToken.isError && (
          <Notice tone="danger" role="alert" className="flex flex-col gap-2">
            <span>No pudimos generar tu enlace. Intentá de nuevo en unos minutos. Tu archivo .ics se puede descargar igual.</span>
            <Button variant="outline" className="min-h-11 self-start" onClick={panel.generate}>
              Reintentar
            </Button>
          </Notice>
        )}
        {revokeToken.isError && (
          <Notice tone="danger" role="alert">
            {describeError(revokeToken.error, { fallback: 'No pudimos revocar el enlace. Probá de nuevo.' }).message}
          </Notice>
        )}
        {hasLink && (
          <>
            {panel.pendingAction ? (
              <PendingActionConfirmation
                action={panel.pendingAction}
                isPending={issueToken.isPending || revokeToken.isPending}
                onConfirm={panel.pendingAction === 'regenerate' ? panel.generate : panel.revoke}
                onCancel={panel.cancelPendingAction}
              />
            ) : (
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" className="min-h-11" onClick={panel.askToRegenerate}>
                  Regenerar enlace
                </Button>
                <Button variant="outline" className="min-h-11" onClick={panel.askToRevoke}>
                  Revocar enlace
                </Button>
              </div>
            )}
            <p className="text-size-12 text-ink-faint">
              Los calendarios que ya usaban el enlace pueden seguir mostrando datos viejos hasta una hora.
            </p>
          </>
        )}
      </div>
    );
  }

  return (
    <section aria-labelledby="calendar-feed-title" className="flex flex-col gap-3">
      <h3 id="calendar-feed-title" className="font-heading text-size-14 font-semibold text-ink">
        Suscribirme con un enlace
      </h3>
      {body}
    </section>
  );
}
