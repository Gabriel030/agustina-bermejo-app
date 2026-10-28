import { Link } from 'react-router-dom';
import { CheckCircle2, Clock } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { useAuthorizationRequests } from '../hooks/useAuthorizationRequests';
import { PENDING_STATUSES, inboxPath } from '../authorization/authorizationRequests';

const PENDING_COUNT_SEARCH = { statuses: PENDING_STATUSES, sort: 'EXPIRES_ON', descending: false, page: 0, size: 1 };

function pluralize(count, singular, plural) {
  return count === 1 ? singular : plural;
}

export function PendingRequestsNotice({ roleBase }) {
  const { data } = useAuthorizationRequests(PENDING_COUNT_SEARCH);
  const total = data?.totalElements ?? 0;
  if (!total) return null;
  const isDirector = roleBase === 'director';
  const Icon = isDirector ? CheckCircle2 : Clock;

  return (
    <GlassCard className="flex flex-wrap items-center gap-3 px-4 py-3">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-neutral-bg text-ink-soft">
        <Icon className="size-4" aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-1 text-sm">
        <p className="font-semibold text-ink">
          {isDirector
            ? `${total} ${pluralize(total, 'pedido espera', 'pedidos esperan')} tu decisión`
            : `Tenés ${total} ${pluralize(total, 'pedido esperando', 'pedidos esperando')} al director`}
        </p>
        <p className="text-ink-faint">
          {isDirector
            ? 'Descuentos, promociones y otras operaciones de facturación propuestas.'
            : 'Aparecen en la lista cuando el director los aprueba.'}
        </p>
      </div>
      <Button asChild variant="outline" size="sm">
        <Link to={inboxPath(roleBase)}>{isDirector ? 'Ver en Aprobaciones' : 'Ver mis pedidos'}</Link>
      </Button>
    </GlassCard>
  );
}
