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
    confirmarVariant: 'gradient',
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

function pendingColumns({ onAction }) {
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
      id: 'pending',
      header: 'Pendiente',
      accessorFn: () => 'Confirmar pagos',
      enableSorting: false,
      cell: () => <span className="text-size-13 text-ink">Confirmar pagos</span>,
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => {
        const name = row.original.studentName ?? 'el alumno';
        return (
          <div className="flex items-center justify-end gap-2">
            <Button size="sm" variant="gradient" aria-label={`Confirmar y emitir la constancia de ${name}`} onClick={() => onAction('emit', row.original)}>
              Confirmar y emitir
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button type="button" variant="ghost" size="icon" aria-label={`Más acciones de ${name}`}>
                  <MoreHorizontal aria-hidden="true" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="bg-dialog-bg backdrop-blur-[28px] backdrop-saturate-[1.6]">
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
              <DropdownMenuContent align="end" className="bg-dialog-bg backdrop-blur-[28px] backdrop-saturate-[1.6]">
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

function PendingTab({ rows, query, role, onAction }) {
  const columns = useMemo(() => pendingColumns({ onAction }), [onAction]);
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
  const role = isDirector ? 'director' : 'administrativo';

  const pending = usePendingRegularRequests();
  const issued = useIssuedRegularCertificates();
  const issue = useIssueRegularCertificate();
  const reject = useRejectRegularRequest();
  const revoke = useRevokeRegularCertificate();

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

  const copy = dialog ? dialogCopy[dialog.kind] : null;
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
        </TabsList>
        <TabsContent value="pedidos" className="mt-0">
          <GlassCard className="p-3 md:p-4">
            <PendingTab rows={pendingRows} query={pending} role={role} onAction={onAction} />
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
      </Tabs>
      {dialog && (
        <ModalMotivo
          key={`${dialog.kind}-${dialog.row.id}`}
          open
          onClose={() => setDialog(null)}
          onConfirmar={confirm}
          confirmando={issue.isPending || reject.isPending || revoke.isPending}
          requerido={dialog.kind !== 'emit'}
          minLength={dialog.kind === 'emit' ? 0 : REASON_MIN_LENGTH}
          showCounter
          disableUntilMinLength
          titulo={copy.titulo}
          descripcion={dialogDescription(dialog.kind, holderName)}
          placeholder={copy.placeholder}
          confirmarLabel={copy.confirmarLabel}
          confirmarVariant={copy.confirmarVariant}
          mensajeRequerido="El motivo es obligatorio."
        />
      )}
    </PageContainer>
  );
}
