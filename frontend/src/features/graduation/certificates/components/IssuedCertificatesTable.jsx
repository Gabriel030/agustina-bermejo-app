import { useMemo, useState } from 'react';
import { Download, Eye } from 'lucide-react';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { useAuth } from '@/context/AuthContext';
import { GlassCard } from '@/components/GlassCard';
import { Paginador } from '@/components/Paginador';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { DataTable } from '@/components/table/DataTable';
import { formatearFecha } from '@/lib/comisiones';
import { RevokeReissueDialog } from '@/features/graduation/certificates/components/RevokeReissueDialogs';
import {
  fetchGraduationCertificatePdf,
  useGraduationCertificateDetail,
  useIssuedGraduationCertificates,
  useReissueGraduationCertificate,
  useRevokeGraduationCertificate,
} from '@/features/graduation/certificates/hooks/useIssuedGraduationCertificates';
import {
  ISSUED_KIND_TABS,
  ISSUED_STATUS_OPTIONS,
  isDownloadable,
  isRevocable,
  issuedStatusOf,
} from '@/features/graduation/certificates/lib/issuedGraduationCertificates';
import { saveBlob } from '@/features/graduation/certificates/lib/saveBlob';

const DASH = '—';

async function downloadCertificate(certificate) {
  try {
    const { blob, filename } = await fetchGraduationCertificatePdf(certificate.id);
    saveBlob(blob, filename);
  } catch (error) {
    toast.error(describeError(error).message);
  }
}

const KIND_LABEL = { PROGRAM: 'Carrera', COURSE: 'Curso' };

function issuedColumns({ onDetail, onDownload }) {
  return [
    {
      id: 'holder',
      header: 'Alumno',
      accessorFn: (row) => row.holderFullName,
      enableSorting: false,
      meta: { cardTitle: true },
      cell: ({ row }) => <span className="font-heading text-size-14 font-semibold leading-tight text-ink">{row.original.holderFullName}</span>,
    },
    {
      id: 'title',
      header: 'Título',
      accessorFn: (row) => row.titleName,
      enableSorting: false,
      cell: ({ row }) => <span className="text-size-13 text-ink">{row.original.titleName}</span>,
    },
    {
      id: 'issuedAt',
      header: 'Emitido',
      accessorFn: (row) => row.issuedAt,
      meta: { headerClassName: 'whitespace-nowrap' },
      cell: ({ row }) => <span className="text-size-13 tabular-nums text-ink">{formatearFecha(row.original.issuedAt) ?? DASH}</span>,
    },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (row) => issuedStatusOf(row.status).label,
      enableSorting: false,
      cell: ({ row }) => {
        const status = issuedStatusOf(row.original.status);
        return <StatusPill tone={status.tone}>{status.label}</StatusPill>;
      },
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => {
        const name = row.original.holderFullName;
        return (
          <div className="flex items-center justify-end gap-2">
            {isDownloadable(row.original) && (
              <Button size="sm" variant="outline" aria-label={`Descargar PDF de ${name}`} onClick={() => onDownload(row.original)}>
                <Download aria-hidden="true" />
                PDF
              </Button>
            )}
            <Button size="sm" variant="outline" aria-label={`Ver detalle de ${name}`} onClick={() => onDetail(row.original)}>
              <Eye aria-hidden="true" />
              Detalle
            </Button>
          </div>
        );
      },
    },
  ];
}

function IssuedCertificateDetail({ certificateId, isDirector, onClose, onOpenCertificate, onDownload, onAction }) {
  const detail = useGraduationCertificateDetail(certificateId);
  const certificate = detail.data;

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {detail.isPending && <p className="text-size-14 text-ink-faint">Cargando el certificado…</p>}
        {detail.isError && (
          <p role="alert" className="text-size-14 font-medium text-danger">
            No pudimos cargar el certificado
          </p>
        )}
        {certificate && (
          <>
            <DialogHeader>
              <DialogTitle>{certificate.holderFullName}</DialogTitle>
              <DialogDescription>
                {certificate.titleName} · {KIND_LABEL[certificate.kind]}
              </DialogDescription>
            </DialogHeader>
            <dl className="grid grid-cols-2 gap-3 text-size-13">
              <dt className="text-ink-faint">Estado</dt>
              <dd>
                <StatusPill tone={issuedStatusOf(certificate.status).tone}>{issuedStatusOf(certificate.status).label}</StatusPill>
              </dd>
              <dt className="text-ink-faint">Registro</dt>
              <dd className="tabular-nums text-ink">{certificate.registryNumber ?? DASH}</dd>
              <dt className="text-ink-faint">Egreso</dt>
              <dd className="text-ink">{formatearFecha(certificate.graduationDate) ?? DASH}</dd>
              <dt className="text-ink-faint">Emitido</dt>
              <dd className="text-ink">{formatearFecha(certificate.issuedAt) ?? DASH}</dd>
              <dt className="text-ink-faint">Código</dt>
              <dd className="break-all text-size-12 text-ink">{certificate.code}</dd>
            </dl>

            {certificate.status === 'REVOKED' && (
              <div className="flex flex-col gap-1 rounded-control border border-border p-3">
                <span className="text-size-12 font-semibold uppercase text-ink-faint">Motivo de la revocación</span>
                <p className="text-size-14 text-ink">{certificate.revocationReason ?? DASH}</p>
                {certificate.revokedAt && (
                  <span className="text-size-12 text-ink-faint">Revocado el {formatearFecha(certificate.revokedAt)}</span>
                )}
              </div>
            )}

            {certificate.replacedByCertificateId && (
              <Button variant="outline" onClick={() => onOpenCertificate(certificate.replacedByCertificateId)}>
                Ver el certificado que lo reemplaza
              </Button>
            )}
            {certificate.replacesCertificateId && (
              <Button variant="outline" onClick={() => onOpenCertificate(certificate.replacesCertificateId)}>
                Reemplaza al certificado #{certificate.replacesCertificateId}
              </Button>
            )}

            <DialogFooter>
              {isDownloadable(certificate) && (
                <Button variant="outline" onClick={() => onDownload(certificate)}>
                  <Download aria-hidden="true" />
                  Descargar PDF
                </Button>
              )}
              {isDirector && isRevocable(certificate) && (
                <>
                  <Button variant="outline" onClick={() => onAction('reissue', certificate)}>
                    Reemitir
                  </Button>
                  <Button variant="destructive" onClick={() => onAction('revoke', certificate)}>
                    Revocar
                  </Button>
                </>
              )}
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

function CertificateOperationDialog({ operation, revoke, reissue, onClose }) {
  const mutation = operation.action === 'revoke' ? revoke : reissue;
  const certificateId = operation.certificate.id;
  return (
    <RevokeReissueDialog
      key={`${operation.action}-${certificateId}`}
      action={operation.action}
      certificate={operation.certificate}
      pending={revoke.isPending || reissue.isPending}
      onClose={onClose}
      onConfirm={(reason) => mutation.mutateAsync({ id: certificateId, reason })}
    />
  );
}

export function IssuedCertificatesTable() {
  const { usuario } = useAuth();
  const isDirector = (usuario?.roles ?? []).includes('DIRECTOR');
  const [kind, setKind] = useState('PROGRAM');
  const [status, setStatus] = useState('ALL');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [detailId, setDetailId] = useState(null);
  const [operation, setOperation] = useState(null);

  const issued = useIssuedGraduationCertificates({ kind, status, query, page });
  const revoke = useRevokeGraduationCertificate();
  const reissue = useReissueGraduationCertificate();

  const rows = issued.data?.content ?? [];
  const totalPages = issued.data?.totalPages ?? 1;

  const columns = useMemo(() => issuedColumns({ onDetail: (row) => setDetailId(row.id), onDownload: downloadCertificate }), []);

  function changeKind(nextKind) {
    setKind(nextKind);
    setPage(1);
  }

  function changeStatus(nextStatus) {
    setStatus(nextStatus);
    setPage(1);
  }

  function changeQuery(nextQuery) {
    setQuery(nextQuery);
    setPage(1);
  }

  function startOperation(action, certificate) {
    setDetailId(null);
    setOperation({ action, certificate });
  }

  return (
    <div className="flex flex-col gap-4">
      <Tabs value={kind} onValueChange={changeKind}>
        <TabsList variant="segmented">
          {ISSUED_KIND_TABS.map((tab) => (
            <TabsTrigger key={tab.value} value={tab.value}>
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>
        {ISSUED_KIND_TABS.map((tab) => (
          <TabsContent key={tab.value} value={tab.value} className="hidden" />
        ))}
      </Tabs>

      <GlassCard className="flex flex-col gap-3 p-3 md:p-4">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <Input
            type="search"
            aria-label="Buscar por nombre, documento o código"
            placeholder="Buscar por nombre, documento o código"
            value={query}
            onChange={(event) => changeQuery(event.target.value)}
            className="md:max-w-xs"
          />
          <NativeSelect className="md:w-56" aria-label="Filtrar por estado" value={status} onChange={(event) => changeStatus(event.target.value)}>
            {ISSUED_STATUS_OPTIONS.map((option) => (
              <NativeSelectOption key={option.value} value={option.value}>
                {option.label}
              </NativeSelectOption>
            ))}
          </NativeSelect>
        </div>

        <DataTable
          columns={columns}
          data={rows}
          getRowId={(row) => String(row.id)}
          isLoading={issued.isPending}
          isError={issued.isError}
          onRetry={issued.refetch}
          errorMessage="No pudimos cargar los certificados emitidos"
          emptyState={
            <EmptyState
              title="No hay certificados emitidos para mostrar"
              description={query ? 'Probá con otro nombre, documento o código.' : 'Los certificados que emitas aparecen acá.'}
            />
          }
        />
        <Paginador page={page} totalPages={totalPages} onChange={setPage} />
      </GlassCard>

      {detailId != null && (
        <IssuedCertificateDetail
          certificateId={detailId}
          isDirector={isDirector}
          onClose={() => setDetailId(null)}
          onOpenCertificate={setDetailId}
          onDownload={downloadCertificate}
          onAction={startOperation}
        />
      )}

      {operation !== null && (
        <CertificateOperationDialog operation={operation} revoke={revoke} reissue={reissue} onClose={() => setOperation(null)} />
      )}
    </div>
  );
}
