import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { formatearFechaCorta } from '@/lib/avisos';
import { useMiSolicitudEgreso } from '../hooks/useGraduation';
import { GRADUATION_STATE, GRADUATION_STATE_PILL, deriveGraduationState, missingCredits } from '../lib/graduationState';

function summaryFor(state, own) {
  const request = own.request;
  switch (state) {
    case GRADUATION_STATE.NOT_ELIGIBLE:
      return 'Todavía no cumplís los requisitos del plan para poder pedir el egreso.';
    case GRADUATION_STATE.ELIGIBLE:
      return 'Cumplís con los requisitos del plan. Podés solicitar tu egreso.';
    case GRADUATION_STATE.REQUESTED:
      return `Enviaste tu solicitud el ${formatearFechaCorta(request.requestedAt)}.`;
    case GRADUATION_STATE.ADMIN_VERIFIED:
      return `Administración verificó tu situación de deuda el ${formatearFechaCorta(request.verifiedAt ?? request.requestedAt)}.`;
    case GRADUATION_STATE.APPROVED:
      return request?.decidedAt
        ? `La Dirección aprobó tu egreso el ${formatearFechaCorta(request.decidedAt)}.`
        : 'Ya egresaste de esta carrera.';
    default:
      return `La Dirección rechazó tu solicitud el ${formatearFechaCorta(request.decidedAt)}.`;
  }
}

function contextFor(state, own) {
  switch (state) {
    case GRADUATION_STATE.ELIGIBLE:
      return 'Al solicitarlo, Administración revisa tu situación de deuda y la Dirección decide.';
    case GRADUATION_STATE.REQUESTED:
      return 'Paso siguiente: Administración verifica que no tengas deuda pendiente.';
    case GRADUATION_STATE.ADMIN_VERIFIED:
      return 'Paso siguiente: la Dirección aprueba o rechaza.';
    case GRADUATION_STATE.REJECTED:
      return own.eligible ? 'Resolvé el motivo y volvé a solicitar.' : null;
    default:
      return null;
  }
}

function MissingRequirements({ own, onSeeStudyPlan }) {
  const credits = missingCredits(own);
  const hasItems = own.pendingMandatorySubjects.length > 0 || own.unfulfilledElectiveGroups.length > 0 || credits > 0;
  if (!hasItems) return null;
  return (
    <div className="flex flex-col gap-2 text-sm text-ink-soft">
      {own.pendingMandatorySubjects.length > 0 && (
        <div>
          <div className="font-semibold text-ink">Materias obligatorias pendientes</div>
          <ul className="mt-1 list-disc pl-5">
            {own.pendingMandatorySubjects.map((subject) => (
              <li key={subject}>{subject}</li>
            ))}
          </ul>
        </div>
      )}
      {own.unfulfilledElectiveGroups.length > 0 && (
        <div>
          <div className="font-semibold text-ink">Grupos electivos sin completar</div>
          <ul className="mt-1 list-disc pl-5">
            {own.unfulfilledElectiveGroups.map((group) => (
              <li key={group}>{group}</li>
            ))}
          </ul>
        </div>
      )}
      {credits > 0 && <div>Te faltan {credits} créditos para completar el plan.</div>}
      <Button variant="link" className="h-auto w-fit p-0" onClick={onSeeStudyPlan}>
        Ver en Plan de estudio →
      </Button>
    </div>
  );
}

export function GraduationTab({ carreraId, onSeeStudyPlan }) {
  const { data: own, isLoading, isError, refetch } = useMiSolicitudEgreso(carreraId);

  if (isLoading) return <Skeleton className="h-40" />;

  if (isError || !own) {
    return (
      <GlassCard className="p-6">
        <EmptyState
          title="No pudimos cargar tu egreso"
          description="Revisá tu conexión y probá de nuevo."
          action={
            <Button variant="outline" onClick={() => refetch()}>
              Reintentar
            </Button>
          }
        />
      </GlassCard>
    );
  }

  const state = deriveGraduationState(own);
  const pill = GRADUATION_STATE_PILL[state];
  const context = contextFor(state, own);
  const rejectionReason = state === GRADUATION_STATE.REJECTED ? own.request.rejectionReason : null;
  const showMissing = state === GRADUATION_STATE.NOT_ELIGIBLE || (state === GRADUATION_STATE.REJECTED && !own.eligible);

  return (
    <GlassCard className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2.5">
        <h2 className="font-heading text-base font-semibold text-ink">Egreso</h2>
        <StatusPill tone={pill.tone} dot>
          {pill.label}
        </StatusPill>
      </div>
      <p className="text-sm text-ink">{summaryFor(state, own)}</p>
      {rejectionReason && <div className="text-[13px] font-bold text-danger">Motivo: “{rejectionReason}”</div>}
      {showMissing && <MissingRequirements own={own} onSeeStudyPlan={onSeeStudyPlan} />}
      {context && <p className="text-[13px] text-ink-soft">{context}</p>}
    </GlassCard>
  );
}
