import { useNavigate } from 'react-router-dom';
import { Ban, FilePlus2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { InfoTip } from '@/components/charts/InfoTip';
import { useAuth } from '@/context/AuthContext';
import { formatDate } from '@/lib/billing';
import { roleBaseOf } from '../authorization/authorizationRequests';
import { PendingRequestActions } from './PendingRequestActions';
import { creditNotePath, invoiceActionsOf } from './creditNotes';

function GuardedButton({ tip, children, ...props }) {
  return (
    <span className="inline-flex items-center gap-1">
      <Button type="button" variant="outline" size="sm" disabled {...props}>
        {children}
      </Button>
      <InfoTip label={`Por qué no está disponible: ${children}`}>{tip}</InfoTip>
    </span>
  );
}

export function InvoiceActions({ invoice, invoices, studentId, studentName, onCancel }) {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const roleBase = roleBaseOf(usuario);
  const actions = invoiceActionsOf(invoice, { invoices });

  if (actions.pending) {
    const { pending } = actions;
    return (
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="text-ink-soft">
          Pedido de {pending.requestedByName ?? 'otra persona'}
          {pending.requestedAt ? ` enviado el ${formatDate(pending.requestedAt)}` : ''}. Sigue vigente hasta que lo autorice la dirección.
        </span>
        <PendingRequestActions pending={pending} subject={studentName} />
      </div>
    );
  }

  if (!actions.showButtons && !actions.note) return null;

  const cancelLabel = actions.lateFee ? 'Anular recargo' : 'Cancelar cuota';

  return (
    <div className="flex flex-wrap items-center gap-2">
      {actions.showButtons && !actions.lateFee && (
        actions.canCredit ? (
          <Button type="button" variant="outline" size="sm" onClick={() => navigate(creditNotePath(roleBase, invoice.id, studentId))}>
            <FilePlus2 aria-hidden="true" />
            Emitir nota de crédito
          </Button>
        ) : (
          <GuardedButton tip={actions.tip ?? actions.creditTip ?? 'Esta cuota no admite una nota de crédito en este momento.'}>
            Emitir nota de crédito
          </GuardedButton>
        )
      )}
      {actions.showButtons && (
        actions.canCancel ? (
          <Button type="button" variant="outline" size="sm" className="text-danger" onClick={() => onCancel(invoice)}>
            <Ban aria-hidden="true" />
            {cancelLabel}
          </Button>
        ) : actions.tip ? (
          <GuardedButton tip={actions.tip}>{cancelLabel}</GuardedButton>
        ) : null
      )}
      {actions.note && <span className="text-sm text-ink-soft">{actions.note}</span>}
    </div>
  );
}
