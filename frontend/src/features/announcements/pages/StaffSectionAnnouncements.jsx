import { useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Breadcrumb } from '@/components/Breadcrumb';
import { LoadErrorState } from '@/components/LoadErrorState';
import { ProgressBar } from '@/components/ProgressBar';
import { InfoTip } from '@/components/charts';
import { DataTable } from '@/components/table/DataTable';
import { useReadReceiptsDeSeccion } from '../hooks/useAvisos';
import { useStaffSectionContext } from '../hooks/useStaffSectionContext';
import { announcementStatus, canInspectReaders, dispatchDate, readPercent } from '../lib/readReceipts';

function ReadCell({ announcement }) {
  if (announcement.status === 'SCHEDULED') {
    return <span className="text-xs text-ink-faint">Se calcula al enviar</span>;
  }
  if (!announcement.readTracked) {
    return (
      <span className="inline-flex items-center gap-1">
        <StatusPill tone="neutral">Sin registro</StatusPill>
        <InfoTip label="Por qué no hay registro">Se envió antes de que existiera el acuse de lectura.</InfoTip>
      </span>
    );
  }
  const total = announcement.recipientCount;
  return (
    <ProgressBar
      className="w-full min-w-[140px]"
      label={`${announcement.readCount} de ${total}`}
      value={readPercent(announcement)}
      tone={total > 0 && announcement.readCount === total ? 'success' : 'accent'}
      showValue={false}
    />
  );
}

function buildColumns(announcementsPath) {
  return [
    {
      id: 'title',
      header: 'Aviso',
      accessorKey: 'title',
      enableSorting: false,
      meta: { cardTitle: true },
      cell: ({ row }) => (
        <div className="flex flex-wrap items-center gap-2 whitespace-normal">
          <span className="text-size-13 font-semibold text-ink">{row.original.title}</span>
          {row.original.urgent && <StatusPill tone="danger">Urgente</StatusPill>}
        </div>
      ),
    },
    {
      id: 'status',
      header: 'Estado',
      enableSorting: false,
      cell: ({ row }) => {
        const status = announcementStatus(row.original);
        return <StatusPill tone={status.tone}>{status.label}</StatusPill>;
      },
    },
    {
      id: 'dispatch',
      header: 'Envío',
      enableSorting: false,
      cell: ({ row }) => <span className="tabular-nums text-ink-faint">{dispatchDate(row.original)}</span>,
    },
    {
      id: 'readers',
      header: 'Lo abrieron',
      enableSorting: false,
      cell: ({ row }) => <ReadCell announcement={row.original} />,
    },
    {
      id: 'action',
      header: '',
      enableSorting: false,
      cell: ({ row }) =>
        canInspectReaders(row.original) ? (
          <Link
            to={`${announcementsPath}/${row.original.id}`}
            aria-label={`Quién lo abrió de ${row.original.title}`}
            className="text-size-13 font-semibold text-primary hover:underline"
          >
            Quién lo abrió ›
          </Link>
        ) : (
          <span className="text-ink-faint">—</span>
        ),
    },
  ];
}

export function StaffSectionAnnouncements() {
  const { cycleId, sectionId } = useParams();
  const navigate = useNavigate();
  const { sectionType, sectionLabel, announcementsPath, breadcrumb } = useStaffSectionContext(cycleId, sectionId);
  const { data: announcements, isLoading, isError, error, refetch } = useReadReceiptsDeSeccion(sectionId, sectionType);
  const columns = useMemo(() => buildColumns(announcementsPath), [announcementsPath]);
  const loading = isLoading || !sectionType;

  return (
    <PageContainer className="gap-3 md:gap-6">
      <div className="flex flex-col gap-1.5">
        <Breadcrumb items={[...breadcrumb, { label: 'Avisos' }]} />
        <h1 className="font-heading text-xl font-bold text-ink md:text-2xl">Avisos de la comisión</h1>
        {sectionLabel && <p className="text-size-12-5 text-ink-faint">{sectionLabel}</p>}
      </div>

      {isError ? (
        <LoadErrorState error={error} onRetry={() => refetch()} resource="los avisos" />
      ) : loading ? (
        <GlassCard className="flex flex-col gap-2 p-3" aria-busy="true" aria-label="Cargando">
          {Array.from({ length: 6 }, (_, index) => <Skeleton key={index} className="h-11 w-full rounded-xl" />)}
        </GlassCard>
      ) : !announcements?.length ? (
        <GlassCard className="p-6">
          <EmptyState icon="📣" title="Esta comisión todavía no tiene avisos" description="Los avisos los crea el docente de la comisión." />
        </GlassCard>
      ) : (
        <GlassCard className="flex flex-col gap-3 p-3 md:p-5">
          <p className="text-size-11-5 text-ink-faint">Lo abrieron marca cuándo se abrió el aviso en Novedades</p>
          <DataTable
            compact
            columns={columns}
            data={announcements}
            getRowId={(announcement) => String(announcement.id)}
            minWidth={720}
            onRowClick={(announcement) => canInspectReaders(announcement) && navigate(`${announcementsPath}/${announcement.id}`)}
          />
        </GlassCard>
      )}
    </PageContainer>
  );
}
