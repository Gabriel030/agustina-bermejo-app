import { useState } from 'react';
import { Lock } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Skeleton } from '@/components/Skeleton';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import {
  usePreferenciasNotificaciones,
  useActualizarPreferenciasNotificaciones,
} from '@/features/identity/hooks/useMiPerfil';

const CHANNEL_LABELS = { APP: 'En la app', EMAIL: 'Por email' };
const CHANNELS = Object.keys(CHANNEL_LABELS);

const COLLECTION_EVENT = 'PAYMENT_DUE_SOON';

const EVENT_LABELS = {
  [COLLECTION_EVENT]: 'Recordatorio antes del vencimiento',
  ACCOUNT_CREATED: 'Cuenta creada',
  SUBJECT_ENROLLMENT: 'Inscripción a materia',
  GRADE_POSTED: 'Nota publicada',
  INVOICE_GENERATED: 'Factura generada',
  EXAM_BOARD_REGISTERED: 'Mesa de examen registrada',
  INSTITUTIONAL_NOTICE: 'Aviso institucional',
};

const GENERAL_EVENTS = Object.keys(EVENT_LABELS).filter((event) => event !== COLLECTION_EVENT);

const ALWAYS_ON_COLLECTION_ROWS = [
  'Cuota vencida',
  'Antes de una posible suspensión',
  'Resumen de tu deuda',
  'Resumen mensual tras una baja con deuda',
];

const ALWAYS_ON_CAPTION = 'Siempre activo, no se puede apagar';
const LOCK_TEXT = 'Este aviso es obligatorio: no se puede desactivar.';

function LockedMark({ label }) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label={`${label}: ${LOCK_TEXT}`}
          className="inline-flex items-center gap-1.5 rounded-md p-1 text-ink-faint hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <Lock className="size-3.5" />
          <span className="hidden text-xs sm:inline">Siempre activo</span>
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-60 bg-dialog-bg p-3 text-xs text-ink-soft text-pretty">{LOCK_TEXT}</PopoverContent>
    </Popover>
  );
}

function NotificationName({ children, alwaysOn }) {
  return (
    <TableCell className="whitespace-normal text-ink">
      {children}
      {alwaysOn && <span className="mt-0.5 block text-xs text-ink-faint sm:hidden">{ALWAYS_ON_CAPTION}</span>}
    </TableCell>
  );
}

function ChannelCell({ event, channel, locked, active, disabled, onToggle }) {
  const label = `${EVENT_LABELS[event]} — ${CHANNEL_LABELS[channel]}`;
  return (
    <TableCell className="text-center">
      {locked ? (
        <LockedMark label={label} />
      ) : (
        <Switch
          checked={active}
          onCheckedChange={() => onToggle(event, channel)}
          disabled={disabled}
          aria-label={`${label}: ${active ? 'activado' : 'desactivado'}`}
        />
      )}
    </TableCell>
  );
}

function SectionRow({ title }) {
  return (
    <TableRow className="hover:bg-transparent">
      <TableCell colSpan={CHANNELS.length + 1} className="bg-neutral-bg/55 py-2 text-xs font-bold text-ink-soft">
        {title}
      </TableCell>
    </TableRow>
  );
}

export function NotificationMatrix() {
  const { data, isLoading } = usePreferenciasNotificaciones();
  const update = useActualizarPreferenciasNotificaciones();
  const [pending, setPending] = useState(null);

  const original = data?.preferences ?? {};
  const mandatory = data?.mandatoryChannels ?? {};
  const prefs = pending ?? original;
  const isLocked = (event, channel) => (mandatory[event] ?? []).includes(channel);
  const isActive = (event, channel) => isLocked(event, channel) || (prefs[event]?.[channel] ?? true);
  const emailOffForCollection = !isActive(COLLECTION_EVENT, 'EMAIL');

  function toggle(event, channel) {
    if (isLocked(event, channel)) return;
    const base = pending ?? original;
    const current = base[event]?.[channel] ?? true;
    setPending({ ...base, [event]: { ...(base[event] ?? {}), [channel]: !current } });
  }

  async function save() {
    try {
      await update.mutateAsync({ preferences: pending });
      setPending(null);
      toast.success('Guardamos tus preferencias de notificaciones.');
    } catch (err) {
      toast.error(err.message ?? 'No se pudo guardar.');
    }
  }

  if (isLoading) {
    return (
      <div className="flex flex-col gap-2">
        {[...Array(7)].map((_, i) => (
          <Skeleton key={i} className="h-10 w-full" />
        ))}
      </div>
    );
  }

  const renderRow = (event) => (
    <TableRow key={event}>
      <NotificationName alwaysOn={CHANNELS.every((channel) => isLocked(event, channel))}>{EVENT_LABELS[event]}</NotificationName>
      {CHANNELS.map((channel) => (
        <ChannelCell
          key={channel}
          event={event}
          channel={channel}
          locked={isLocked(event, channel)}
          active={isActive(event, channel)}
          disabled={update.isPending}
          onToggle={toggle}
        />
      ))}
    </TableRow>
  );

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Notificación</TableHead>
              {CHANNELS.map((channel) => (
                <TableHead key={channel} className="text-center">
                  {CHANNEL_LABELS[channel]}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            <SectionRow title="Cobranza" />
            {renderRow(COLLECTION_EVENT)}
            {ALWAYS_ON_COLLECTION_ROWS.map((label) => (
              <TableRow key={label}>
                <NotificationName alwaysOn>{label}</NotificationName>
                {CHANNELS.map((channel) => (
                  <TableCell key={channel} className="text-center">
                    <LockedMark label={`${label} — ${CHANNEL_LABELS[channel]}`} />
                  </TableCell>
                ))}
              </TableRow>
            ))}
            <SectionRow title="General" />
            {GENERAL_EVENTS.map(renderRow)}
          </TableBody>
        </Table>
      </div>
      {emailOffForCollection && (
        <p role="status" className="rounded-xl bg-neutral-bg px-3 py-2 text-xs text-ink-soft">
          Si apagás el mail, no te escribimos antes del vencimiento de cada cuota. Vas a seguir viendo el aviso en la app y
          vas a recibir por mail los avisos de cuota vencida y de deuda.
        </p>
      )}
      {pending !== null && (
        <div className="flex items-center justify-end gap-2">
          <Button size="sm" variant="ghost" onClick={() => setPending(null)} disabled={update.isPending}>
            Cancelar
          </Button>
          <Button size="sm" variant="gradient" onClick={save} loading={update.isPending}>
            Guardar
          </Button>
        </div>
      )}
    </div>
  );
}
