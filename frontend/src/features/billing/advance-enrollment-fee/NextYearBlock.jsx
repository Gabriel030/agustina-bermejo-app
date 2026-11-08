import { useLocation, useNavigate } from 'react-router-dom';
import { Clock, Lock, Receipt } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { useEstadoMatricula } from '@/features/enrollment';
import { paymentOriginState } from '@/hooks/usePaymentReturn';
import { formatDate, formatMoney } from '@/lib/billing';
import { advanceKindOf, advanceOf, advanceYearOf, formatLongDate } from './advanceEnrollmentFee';

export const ACCOUNT_ANCHOR = 'student-account-table';

function Row({ icon: Icon, blocked = false, title, children, action }) {
  return (
    <div className="flex flex-wrap items-center gap-4 px-5 py-3.5">
      <div
        className={`grid size-10 shrink-0 place-items-center rounded-xl ${blocked ? 'bg-neutral-bg text-ink-faint' : 'bg-accent-soft text-primary'}`}
      >
        <Icon size={18} />
      </div>
      <div className="min-w-56 flex-1">
        <b className="text-sm font-bold text-ink">{title}</b>
        <p className={`mt-0.5 text-[13px] ${blocked ? 'text-danger' : 'text-ink-soft'}`}>{children}</p>
      </div>
      {action}
    </div>
  );
}

export function NextYearBlock({ enrollmentId }) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { data } = useEstadoMatricula(enrollmentId);
  const advance = advanceOf(data);
  const kind = advanceKindOf(advance);
  const year = advanceYearOf(advance);

  if (kind === 'hidden' || kind === 'issued') return null;

  const title = year ? `Matrícula ${year}` : 'Matrícula del año siguiente';

  function goToConfirmation() {
    navigate(`${pathname}/matricula-anticipada`, { state: paymentOriginState(pathname, 'Volver a las cuotas') });
  }

  function goToOverdueInstallments() {
    document.getElementById(ACCOUNT_ANCHOR)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  return (
    <GlassCard role="region" aria-label={year ? `Para el ${year}` : 'Para el año siguiente'} className="overflow-hidden p-0">
      <h2 className="px-5 pb-1 pt-4 font-heading text-base font-semibold text-ink">{year ? `Para el ${year}` : 'Para el año siguiente'}</h2>
      <div aria-live="polite">
        {kind === 'available' && (
          <Row
            icon={Receipt}
            title={title}
            action={
              <Button variant="gradient" size="lg" onClick={goToConfirmation}>
                {year ? `Pagar la matrícula ${year}` : 'Pagar la matrícula'}
              </Button>
            }
          >
            Al precio de hoy: <b className="tabular-nums text-ink">{formatMoney(advance.amount)}</b>. Vence el {formatDate(advance.dueDate)}.
          </Row>
        )}
        {kind === 'debt' && (
          <Row
            icon={Lock}
            blocked
            title={title}
            action={
              <Button variant="outline" size="lg" onClick={goToOverdueInstallments}>
                Ver mis cuotas vencidas
              </Button>
            }
          >
            Tenés cuotas vencidas: pagalas primero.
          </Row>
        )}
        {kind === 'early' && (
          <Row icon={Clock} blocked title={title}>
            <span className="text-ink-soft">Se puede pagar desde el {formatLongDate(advance.opensOn)}.</span>
          </Row>
        )}
      </div>
    </GlassCard>
  );
}
