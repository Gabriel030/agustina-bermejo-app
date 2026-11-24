import { useMemo } from 'react';
import { Ban } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { formatInstant } from '../lib/invitationWaveMath';
import { EXCLUSION_LABELS, personLabel, recipientDetail } from '../lib/invitationWaveLabels';
import { RecipientStatusPill } from './WaveStatusPill';

function PersonCell({ recipient }) {
  return (
    <div className="flex min-w-0 flex-col">
      <span className="font-semibold text-ink">{personLabel(recipient)}</span>
      {recipient.displayName != null && (
        <span className="text-size-12-5 font-medium text-ink-faint">{recipient.email ?? 'Sin email'}</span>
      )}
    </div>
  );
}

function ExclusionPill({ reason }) {
  return (
    <StatusPill tone="warning" icon={Ban}>
      {EXCLUSION_LABELS[reason] ?? 'Excluido'}
    </StatusPill>
  );
}

function buildColumns(variant, zone) {
  const person = {
    id: 'person',
    header: 'Persona',
    enableSorting: false,
    cell: ({ row }) => <PersonCell recipient={row.original} />,
    meta: { cardTitle: true },
  };
  if (variant === 'eligible') {
    return [person, { id: 'status', header: 'Estado', enableSorting: false, cell: ({ row }) => <RecipientStatusPill status={row.original.status} /> }];
  }
  if (variant === 'excluded') {
    return [person, { id: 'reason', header: 'Motivo de exclusión', enableSorting: false, cell: ({ row }) => <ExclusionPill reason={row.original.exclusionReason} /> }];
  }
  return [
    person,
    { id: 'status', header: 'Estado', enableSorting: false, cell: ({ row }) => <RecipientStatusPill status={row.original.status} /> },
    { id: 'detail', header: 'Detalle', enableSorting: false, cell: ({ row }) => recipientDetail(row.original) },
    {
      id: 'lastSend',
      header: 'Último envío',
      enableSorting: false,
      cell: ({ row }) => formatInstant(row.original.lastAttemptAt ?? row.original.sentAt, zone) || '—',
    },
  ];
}

export function RecipientsTable({ rows, variant, zone, isLoading, isError, onRetry, emptyState }) {
  const columns = useMemo(() => buildColumns(variant, zone), [variant, zone]);
  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={(recipient) => String(recipient.personId)}
      isLoading={isLoading}
      isError={isError}
      onRetry={onRetry}
      errorMessage="No pudimos cargar los destinatarios."
      emptyState={emptyState}
      minWidth={variant === 'detail' ? 640 : 420}
    />
  );
}
