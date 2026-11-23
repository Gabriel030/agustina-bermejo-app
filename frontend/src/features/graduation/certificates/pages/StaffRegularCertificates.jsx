import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { MoreHorizontal } from 'lucide-react';
import { toast } from 'sonner';
import { apiFetchBlob } from '@/api/client';
import { describeError } from '@/api/describeError';
import { useAuth } from '@/context/AuthContext';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { TabCount } from '@/components/TabCount';
import { ModalMotivo } from '@/components/ModalMotivo';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { DataTable } from '@/components/table/DataTable';
import { formatearFecha } from '@/lib/comisiones';
import {
  useIssueRegularCertificate,
  useIssuedRegularCertificates,
  usePendingRegularRequests,
  useRejectRegularRequest,
  useRevokeRegularCertificate,
} from '@/features/graduation/certificates/hooks/useStaffRegularCertificates';
import { RequestDebtCell, RequestExceptionCell } from '@/features/graduation/certificate-exceptions/components/RequestDebtCell';
import { RequestExceptionDialog } from '@/features/graduation/certificate-exceptions/components/RequestExceptionDialog';
import { RejectionReasonDialog } from '@/features/graduation/certificate-exceptions/components/RejectionReasonDialog';
import { OwnExceptionsTab } from '@/features/graduation/certificate-exceptions/components/OwnExceptionsTab';
import { useCertificateExceptions } from '@/features/graduation/certificate-exceptions/hooks/useCertificateExceptions';
import {
  REASON_MIN_LENGTH,
  certificateStatusOf,
  saveBlob,
  studentSectionPath,
} from '@/features/graduation/certificates/lib/staffRegularCertificates';

const DASH = '—';
const dialogCopy = {
  emit: {
    titulo: 'Confirmar y emitir',
    placeholder: 'Por ejemplo: comprobante de pago de octubre revisado',
    confirmarLabel: 'Confirmar y emitir',
    confirmarVariant: 'primary',
  },
  emitException: {
    titulo: 'Emitir con excepción',
    placeholder: 'Por ejemplo: excepción aprobada por el Director, expediente verificado',
    confirmarLabel: 'Emitir con excepción',
    confirmarVariant: 'primary',
  },
  reject: {
    titulo: 'Rechazar pedido',
    placeholder: 'Por ejemplo: falta regularizar la cuota de octubre',
    confirmarLabel: 'Rechazar pedido',
    confirmarVariant: 'destructive',
  },
  revoke: {
    titulo: 'Revocar constancia',
    placeholder: 'Motivo de la revocación',
    confirmarLabel: 'Revocar',
    confirmarVariant: 'destructive',
  },
};

function dialogDescription(kind, name) {
  if (kind === 'emitException') return `Se emite la constancia de ${name} usando la excepción aprobada. La excepción se usa una sola vez.`;
  if (kind === 'emit') return `Confirmás que ${name} está al día con sus pagos. Queda registrado a tu nombre. La nota es opcional.`;
  if (kind === 'reject') return `${name} va a ver este motivo en su pantalla.`;
  return `La constancia de ${name} pasa a Revocada y su código deja de verificar. No se puede deshacer.`;
}

function HolderCell({ name }) {
  return <span className="font-heading text-size-14 font-semibold leading-tight text-ink">{name ?? DASH}</span>;
}

function SummaryLinks({ row, role }) {
  if (!row.studentId) return <p className="text-size-13 text-ink-faint">Cargando datos del alumno…</p>;
  return (
    <div className="flex flex-wrap items-center gap-4 py-1 text-size-13">
      <Link className="font-semibold text-primary underline-offset-2 hover:underline" to={studentSectionPath(role, row.studentId, 'academico')}>
        Ver asistencia de {row.studentName ?? 'el alumno'}
      </Link>
      <Link className="font-semibold text-primary underline-offset-2 hover:underline" to={studentSectionPath(role, row.studentId, 'financiero')}>
        Ver pagos de {row.studentName ?? 'el alumno'}
      </Link>
    </div>
  );
}

function pendingColumns({ onAction, canRequest }) {
  return [
    {
      id: 'student',
      header: 'Alumno',
      accessorFn: (row) => row.studentName ?? '',
      enableSorting: false,
      meta: { cardTitle: true },
      cell: ({ row }) => <HolderCell name={row.original.studentName} />,
    },
    {
      id: 'program',
      header: 'Carrera o curso',
      accessorFn: (row) => row.programName ?? DASH,
      enableSorting: false,
      enableColumnFilter: true,
      meta: { filterable: true, filterTitle: 'carrera o curso' },
      cell: ({ row }) => <span className="text-size-13 text-ink">{row.original.programName ?? DASH}</span>,
    },
    {
      id: 'requestedAt',
      header: 'Pedida',
      accessorFn: (row) => row.requestedAt,
      meta: { headerClassName: 'whitespace-nowrap' },
      cell: ({ row }) => <span className="text-size-13 text-ink">{formatearFecha(row.original.requestedAt) ?? DASH}</span>,
    },
    {
      id: 'debt',
      header: 'Deuda',
      accessorFn: (row) => (row.hasDebt ? 'Con deuda' : ''),
      enableSorting: false,
      cell: ({ row }) => <RequestDebtCell row={row.original} />,
    },
    {
      id: 'exception',
      header: 'Excepción',
      accessorFn: (row) => row.exceptionStatus ?? '',
      enableSorting: false,
      meta: { hideInCard: false },
      cell: ({ row }) => (
        <RequestExceptionCell
          row={row.original}
          canRequest={canRequest}
          onRequest={(target) => onAction('request', target)}
          onEmit={(target) => onAction('emitException', target)}
          onShowReason={(target) => onAction('reason', target)}
        />
      ),
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => {
        const name = row.original.studentName ?? 'el alumno';
        return (
          <div className="flex items-center justify-end gap-2">
            {!row.original.hasDebt && (
              <Button size="sm" variant="primary" aria-label={`Confirmar y emitir la constancia de ${name}`} onClick={() => onAction('emit', row.original)}>
                Confirmar y emitir
              </Button>
            )}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button type="button" variant="ghost" size="icon" aria-label={`Más acciones de ${name}`}>
                  <MoreHorizontal aria-hidden="true" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="bg-dialog-bg">
                <DropdownMenuItem variant="destructive" onSelect={() => onAction('reject', row.original)}>
                  Rechazar
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        );
      },
    },
  ];
}

function issuedColumns({ isDirector, onAction, onDownload, onCopyLink }) {
  return [
    {
      id: 'student',
      header: 'Alumno',
      accessorFn: (row) => row.studentName ?? '',
      enableSorting: false,
      meta: { cardTitle: true },
      cell: ({ row }) => <HolderCell name={row.original.studentName} />,
    },
    {
      id: 'program',
      header: 'Carrera o curso',
      accessorFn: (row) => row.programName ?? DASH,
      enableSorting: false,
      enableColumnFilter: true,
      meta: { filterable: true, filterTitle: 'carrera o curso' },
      cell: ({ row }) => <span className="text-size-13 text-ink">{row.original.programName ?? DASH}</span>,
    },
    {
      id: 'issuedAt',
      header: 'Emitida',
      accessorFn: (row) => row.issuedAt,
      meta: { headerClassName: 'whitespace-nowrap' },
      cell: ({ row }) => (
        <span className="text-size-13 text-ink">
          {formatearFecha(row.original.issuedAt) ?? DASH} · vence {formatearFecha(row.original.expiresAt) ?? DASH}
        </span>
      ),
    },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (row) => certificateStatusOf(row.status).label,
      enableSorting: false,
      enableColumnFilter: true,
      meta: { filterable: true, filterTitle: 'estado' },
      cell: ({ row }) => {
        const status = certificateStatusOf(row.original.status);
        return <StatusPill tone={status.tone}>{status.label}</StatusPill>;
      },
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => {
        const name = row.original.studentName ?? 'el alumno';
        return (
          <div className="flex justify-end">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button type="button" variant="ghost" size="icon" aria-label={`Acciones de ${name}`}>
                  <MoreHorizontal aria-hidden="true" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="bg-dialog-bg">
                <DropdownMenuItem onSelect={() => onDownload(row.original)}>Descargar PDF</DropdownMenuItem>
                <DropdownMenuItem onSelect={() => onCopyLink(row.original)}>Copiar enlace de verificación</DropdownMenuItem>
                {isDirector && row.original.status === 'VALID' && (
                  <DropdownMenuItem variant="destructive" onSelect={() => onAction('revoke', row.original)}>
                    Revocar
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        );
      },
    },
  ];
}

function PendingTab({ rows, query, role, canRequest, onAction }) {
  const columns = useMemo(() => pendingColumns({ onAction, canRequest }), [onAction, canRequest]);
  return (
    <div className="flex flex-col gap-3">
      <DataTable
        columns={columns}
        data={rows}
        getRowId={(row) => String(row.id)}
        initialSorting={[{ id: 'requestedAt', desc: false }]}
        isLoading={query.isLoading}
        isError={query.isError}
        onRetry={query.refetch}
        errorMessage="No pudimos cargar los pedidos"
        renderExpanded={(tableRow) => <SummaryLinks row={tableRow.original} role={role} />}
        expandLabel="Ver resumen"
        emptyState={
          <EmptyState
            title="No hay pedidos pendientes"
            description="Cuando un alumno pida su constancia y falte confirmar sus pagos, lo vas a ver acá."
          />
        }
      />
      <p className="text-size-12 text-ink-faint">
        Solo aparecen alumnos que ya cumplen la asistencia. Con facturación activa se emiten solas y no pasan por acá.
      </p>
    </div>
  );
}

function IssuedTab({ rows, query, isDirector, onAction, onDownload, onCopyLink }) {
  const columns = useMemo(
    () => issuedColumns({ isDirector, onAction, onDownload, onCopyLink }),
    [isDirector, onAction, onDownload, onCopyLink],
  );
  return (
    <div className="flex flex-col gap-3">
      <DataTable
        columns={columns}
        data={rows}
        getRowId={(row) => String(row.id)}
        initialSorting={[{ id: 'issuedAt', desc: true }]}
        isLoading={query.isLoading}
        isError={query.isError}
        onRetry={query.refetch}
        errorMessage="No pudimos cargar las constancias emitidas"
        emptyState={<EmptyState title="Todavía no se emitió ninguna constancia" description="Las constancias emitidas quedan acá." />}
      />
      <p className="text-size-12 text-ink-faint">
        {isDirector
          ? 'Como Director podés revocar una constancia vigente.'
          : 'Revocar una constancia es una acción del Director.'}
      </p>
    </div>
  );
}

export function StaffRegularCertificates() {
  const { usuario } = useAuth();
  const roles = usuario?.roles ?? [];
  const isDirector = roles.includes('DIRECTOR');
  const canRequestException = !isDirector;
  const role = isDirector ? 'director' : 'administrativo';

  const pending = usePendingRegularRequests();
  const issued = useIssuedRegularCertificates();
  const issue = useIssueRegularCertificate();
  const reject = useRejectRegularRequest();
  const revoke = useRevokeRegularCertificate();

  const ownExceptions = useCertificateExceptions({ enabled: canRequestException });
  const ownPendingCount = (ownExceptions.data ?? []).filter(
    (exception) => exception.requestedById === usuario?.id && exception.status === 'PENDING',
  ).length;

  const [tab, setTab] = useState('pedidos');
  const [dialog, setDialog] = useState(null);

  const pendingRows = pending.data;
  const issuedRows = issued.data;

  const onAction = useCallback((kind, row) => setDialog({ kind, row }), []);

  const onDownload = useCallback(async (certificate) => {
    try {
      const { blob, filename } = await apiFetchBlob(`/regular-certificates/${certificate.id}/pdf`);
      saveBlob(blob, filename);
    } catch (error) {
      toast.error(describeError(error).message);
    }
  }, []);

  const onCopyLink = useCallback(async (certificate) => {
    try {
      await navigator.clipboard.writeText(certificate.verificationUrl);
      toast.success('Enlace copiado');
    } catch {
      toast.error('No pudimos copiar el enlace. Copialo a mano desde la pantalla de verificación.');
    }
  }, []);

  async function confirm(text) {
    const { kind, row } = dialog;
    try {
      if (kind === 'emit') {
        await issue.mutateAsync({ kind: row.kind, enrollmentId: row.enrollmentId, standingEvidence: text });
        toast.success('Constancia emitida');
      } else if (kind === 'emitException') {
        await issue.mutateAsync({ kind: row.kind, enrollmentId: row.enrollmentId, standingEvidence: text, exceptionId: row.exceptionId });
        toast.success('Constancia emitida con excepción');
      } else if (kind === 'reject') {
        await reject.mutateAsync({ requestId: row.id, reason: text });
        toast.success('Pedido rechazado');
      } else {
        await revoke.mutateAsync({ certificateId: row.id, reason: text });
        toast.success('Constancia revocada');
      }
      setDialog(null);
    } catch (error) {
      toast.error(describeError(error).message);
      return false;
    }
    return true;
  }

  const requestDialog = dialog?.kind === 'request' ? dialog : null;
  const rejectionDialog = dialog?.kind === 'reason' ? dialog : null;
  const reasonDialog = dialog && dialogCopy[dialog.kind] ? dialog : null;
  const copy = reasonDialog ? dialogCopy[reasonDialog.kind] : null;
  const holderName = dialog?.row.studentName ?? 'el alumno';

  return (
    <PageContainer title="Constancias" description="Alumno regular">
      <Tabs className="gap-4" value={tab} onValueChange={setTab}>
        <TabsList variant="segmented">
          <TabsTrigger value="pedidos">
            Pedidos
            <TabCount count={pending.data?.length} />
          </TabsTrigger>
          <TabsTrigger value="emitidas">Emitidas</TabsTrigger>
          {canRequestException && (
            <TabsTrigger value="excepciones">
              Excepciones
              <TabCount count={ownPendingCount} />
            </TabsTrigger>
          )}
        </TabsList>
        <TabsContent value="pedidos" className="mt-0">
          <GlassCard className="p-3 md:p-4">
            <PendingTab rows={pendingRows} query={pending} role={role} canRequest={canRequestException} onAction={onAction} />
          </GlassCard>
        </TabsContent>
        <TabsContent value="emitidas" className="mt-0">
          <GlassCard className="p-3 md:p-4">
            <IssuedTab
              rows={issuedRows}
              query={issued}
              isDirector={isDirector}
              onAction={onAction}
              onDownload={onDownload}
              onCopyLink={onCopyLink}
            />
          </GlassCard>
        </TabsContent>
        {canRequestException && (
          <TabsContent value="excepciones" className="mt-0">
            <GlassCard className="p-3 md:p-4">
              <OwnExceptionsTab onGoToRequests={() => setTab('pedidos')} />
            </GlassCard>
          </TabsContent>
        )}
      </Tabs>
      {requestDialog && <RequestExceptionDialog row={requestDialog.row} onClose={() => setDialog(null)} />}
      {rejectionDialog && (
        <RejectionReasonDialog exceptionId={rejectionDialog.row.exceptionId} studentName={rejectionDialog.row.studentName} onClose={() => setDialog(null)} />
      )}
      {reasonDialog && (
        <ModalMotivo
          key={`${reasonDialog.kind}-${reasonDialog.row.id}`}
          open
          onClose={() => setDialog(null)}
          onConfirmar={confirm}
          confirmando={issue.isPending || reject.isPending || revoke.isPending}
          requerido={reasonDialog.kind !== 'emit' && reasonDialog.kind !== 'emitException'}
          minLength={reasonDialog.kind === 'emit' || reasonDialog.kind === 'emitException' ? 0 : REASON_MIN_LENGTH}
          showCounter
          disableUntilMinLength
          titulo={copy.titulo}
          descripcion={dialogDescription(reasonDialog.kind, holderName)}
          placeholder={copy.placeholder}
          confirmarLabel={copy.confirmarLabel}
          confirmarVariant={copy.confirmarVariant}
          mensajeRequerido="El motivo es obligatorio."
        />
      )}
    </PageContainer>
  );
}
