import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Paginador } from '@/components/Paginador';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { formatDate } from '@/lib/billing';
import { useSimulatedEmails } from '@/features/billing/hooks/useCollectionReminders';
import { MESSAGE_TYPES } from './reminderSchedule';

const PAGE_SIZE = 20;

const DELIVERY_STATUS_LABELS = {
  QUEUED: 'En cola',
  SENT: 'Enviado',
  SENT_SIMULATED: 'Simulado',
  SUPPRESSED: 'Omitido',
  DEFERRED: 'Postergado',
  DISCARDED: 'Descartado',
  FAILED: 'Falló',
};

const STATUS_TONES = {
  SENT: 'success',
  SENT_SIMULATED: 'success',
  QUEUED: 'accent',
  DEFERRED: 'warning',
  SUPPRESSED: 'neutral',
  DISCARDED: 'neutral',
  FAILED: 'danger',
};

const DELIVERY_KIND_LABELS = {
  POST_WITHDRAWAL_SUMMARY: 'Resumen tras una baja',
};

const DIGEST_FORMAT = 'DAILY_DIGEST';

function deliveryKindLabel(kind) {
  if (DELIVERY_KIND_LABELS[kind]) return DELIVERY_KIND_LABELS[kind];
  const [format, messageType] = String(kind ?? '').split(':');
  if (format === DIGEST_FORMAT) return 'Resumen diario';
  return MESSAGE_TYPES[messageType]?.label ?? 'Recordatorio de pago';
}

function StatusCell({ mail }) {
  const pill = <StatusPill tone={STATUS_TONES[mail.status] ?? 'neutral'}>{DELIVERY_STATUS_LABELS[mail.status] ?? mail.status}</StatusPill>;
  if (!mail.statusReason) return pill;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="inline-flex">{pill}</span>
      </TooltipTrigger>
      <TooltipContent>{mail.statusReason}</TooltipContent>
    </Tooltip>
  );
}

function buildColumns(onOpen) {
  return [
    {
      id: 'date',
      header: 'Fecha',
      accessorFn: (mail) => mail.scheduledFor ?? '',
      cell: ({ row }) => formatDate(row.original.scheduledFor),
    },
    {
      id: 'kind',
      header: 'Tipo',
      accessorFn: (mail) => mail.kind,
      cell: ({ row }) => deliveryKindLabel(row.original.kind),
      meta: { filterable: true, filterTitle: 'tipo', filterLabel: deliveryKindLabel },
    },
    {
      id: 'recipient',
      header: 'Destinatario',
      enableSorting: false,
      accessorFn: (mail) => mail.maskedRecipient,
      cell: ({ row }) => <span className="text-ink-soft">{row.original.maskedRecipient}</span>,
    },
    {
      id: 'subject',
      header: 'Asunto',
      enableSorting: false,
      accessorFn: (mail) => mail.subject,
      cell: ({ row }) => (
        <button type="button" onClick={() => onOpen(row.original)} className="text-left font-semibold text-primary hover:underline">
          {row.original.subject}
        </button>
      ),
    },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (mail) => mail.status,
      cell: ({ row }) => <StatusCell mail={row.original} />,
      meta: { filterable: true, filterTitle: 'estado', filterLabel: (status) => DELIVERY_STATUS_LABELS[status] ?? status },
    },
  ];
}

export function SimulatedMailsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [opened, setOpened] = useState(null);
  const mails = useSimulatedEmails(page - 1, PAGE_SIZE);
  const columns = useMemo(() => buildColumns(setOpened), []);

  if (mails.error?.status === 404) {
    return (
      <PageContainer title="Mails simulados">
        <GlassCard>
          <EmptyState
            title="Esta pantalla no existe"
            description="Los mails simulados solo están disponibles en los entornos local y de demostración."
            action={
              <Button variant="outline" onClick={() => navigate(-1)}>
                Volver
              </Button>
            }
          />
        </GlassCard>
      </PageContainer>
    );
  }

  const total = mails.data?.totalElements ?? 0;

  return (
    <PageContainer title="Mails simulados" description="Los mails que el sistema enviaría en producción. Acá no sale ninguno de verdad.">
      <TooltipProvider>
        <GlassCard className="p-0">
          <DataTable
            columns={columns}
            data={mails.data?.items ?? []}
            getRowId={(mail) => String(mail.id)}
            isLoading={mails.isLoading}
            isError={mails.isError}
            onRetry={mails.refetch}
            errorMessage="No pudimos cargar los mails simulados."
            emptyState={<EmptyState icon="📭" title="Todavía no hay mails simulados" />}
            initialSorting={[{ id: 'date', desc: true }]}
            mobileLayout="scroll"
            minWidth={760}
          />
        </GlassCard>
      </TooltipProvider>
      <Paginador page={page} totalPages={Math.ceil(total / PAGE_SIZE)} onChange={setPage} />
      <Sheet open={opened !== null} onOpenChange={(open) => !open && setOpened(null)}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-lg">
          {opened && (
            <SheetHeader>
              <SheetTitle>{opened.subject}</SheetTitle>
              <SheetDescription>Para {opened.maskedRecipient}</SheetDescription>
            </SheetHeader>
          )}
          {opened && <pre className="whitespace-pre-wrap px-4 pb-6 font-body text-sm text-ink">{opened.bodyText}</pre>}
        </SheetContent>
      </Sheet>
    </PageContainer>
  );
}
