import { Button } from '@/components/ui/button';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { describeError } from '@/api/describeError';
import { useRegisterForInformativeSession } from '@/features/enrollment/hooks/useInformativeSessions';
import {
  ALL_PROGRAMS_LABEL,
  describeRemainingSeats,
  describeSessionDate,
  describeSessionPlace,
  isSessionFull,
} from '@/features/enrollment/lib/informativeSessions';

const GENERIC_REGISTER_ERROR = 'No pudimos reservar tu lugar. Probá de nuevo.';

function registerErrorMessage(error) {
  return error?.code ? describeError(error).message : GENERIC_REGISTER_ERROR;
}

export function InformativeSessionCard({ session, programName }) {
  const register = useRegisterForInformativeSession(session.id);
  const date = describeSessionDate(session.scheduledAt);
  const full = isSessionFull(session) && !session.registeredByMe;
  const hasFailed = register.isError && !full && !session.registeredByMe;

  return (
    <GlassCard className="flex flex-col gap-2 p-4" role="article" aria-label={session.title}>
      <div className="flex items-center gap-3">
        <span
          role="img"
          aria-label={date.accessibleLabel}
          className="flex w-13 flex-none flex-col items-center rounded-control border border-border py-1 text-center leading-tight"
        >
          <b className="font-heading text-lg font-bold text-ink">{date.day}</b>
          <span className="text-[10.5px] font-bold tracking-wider text-ink-faint uppercase">{date.monthShort}</span>
        </span>
        <h3 className="font-heading text-size-15 font-semibold text-ink">{session.title}</h3>
      </div>
      <div className="text-size-13 text-ink-soft">{describeSessionPlace(session)}</div>
      <div>
        <StatusPill>{programName ?? ALL_PROGRAMS_LABEL}</StatusPill>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        {full ? (
          <StatusPill tone="danger">Completa</StatusPill>
        ) : (
          <span className="text-size-13 text-ink-soft tabular-nums">{describeRemainingSeats(session)}</span>
        )}
        {session.registeredByMe ? (
          <StatusPill tone="success">Ya reservaste</StatusPill>
        ) : full ? null : (
          <Button variant="primary" size="sm" className="max-sm:h-11 max-sm:w-full" loading={register.isPending} onClick={() => register.mutate()}>
            {hasFailed ? 'Reintentar' : 'Reservar lugar'}
          </Button>
        )}
      </div>
      {hasFailed && (
        <div role="alert" className="rounded-control bg-danger-bg px-2.5 py-2 text-size-13 text-danger-ink">
          {registerErrorMessage(register.error)}
        </div>
      )}
    </GlassCard>
  );
}
