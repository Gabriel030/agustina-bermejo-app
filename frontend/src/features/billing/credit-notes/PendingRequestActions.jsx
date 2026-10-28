import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { WithdrawRequestDialog } from '../authorization/WithdrawRequestDialog';
import { inboxPath, roleBaseOf } from '../authorization/authorizationRequests';
import { pendingOperationTitle } from './creditNotes';

export function PendingRequestActions({ pending, subject }) {
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const [withdrawing, setWithdrawing] = useState(false);
  const own = pending.requestedByPersonId != null && pending.requestedByPersonId === usuario?.id;

  return (
    <>
      <Button asChild variant="outline" size="sm">
        <Link to={inboxPath(roleBase)}>{roleBase === 'director' ? 'Ver en Aprobaciones' : 'Ver mis pedidos'}</Link>
      </Button>
      {own && (
        <Button type="button" variant="outline" size="sm" onClick={() => setWithdrawing(true)}>
          Retirar pedido
        </Button>
      )}
      {withdrawing && (
        <WithdrawRequestDialog
          request={{ id: pending.id, operationLabel: pendingOperationTitle(pending), studentName: subject }}
          onClose={() => setWithdrawing(false)}
        />
      )}
    </>
  );
}
