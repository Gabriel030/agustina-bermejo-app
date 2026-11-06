import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Breadcrumb } from '@/components/Breadcrumb';
import { Buscador } from '@/components/Buscador';
import { LoadErrorState } from '@/components/LoadErrorState';
import { TabCount } from '@/components/TabCount';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { DataTable } from '@/components/table/DataTable';
import { formatearFechaHora } from '@/lib/avisos';
import { useDestinatariosDeAviso, useReadReceiptsDeSeccion } from '../hooks/useAvisos';
import { useStaffSectionContext } from '../hooks/useStaffSectionContext';
import { canInspectReaders, dispatchDate, matchesRecipient, splitRecipients } from '../lib/readReceipts';

const PAGE_SIZE = 10;

function buildColumns(basePath, showReadAt) {
  const columns = [
    {
      id: 'name',
      header: 'Alumno',
      accessorKey: 'name',
      meta: { cardTitle: true },
      sortingFn: (a, b) => a.original.name.localeCompare(b.original.name, 'es'),
      cell: ({ row }) => (
        <Link to={`${basePath}/alumnos/${row.original.studentId}`} className="text-[13px] font-semibold text-primary hover:underline">
          {row.original.name}
        </Link>
      ),
    },
    {
      id: 'file',
      header: 'Legajo',
      accessorKey: 'file',
      enableSorting: false,
      cell: ({ row }) => <span className="tabular-nums text-ink-faint">{row.original.file}</span>,
    },
  ];
  if (showReadAt) {
    columns.push({
      id: 'readAt',
      header: 'Lo abrió',
      accessorKey: 'readAt',
      enableSorting: false,
      cell: ({ row }) => <span className="tabular-nums text-ink-faint">{formatearFechaHora(row.original.readAt)}</span>,
    });
  }
  return columns;
}

function emptyStateFor(tab, search) {
  if (search.trim()) return <EmptyState icon="🔎" title="Sin resultados" description="Probá con otra búsqueda." />;
  if (tab === 'unread') return <EmptyState icon="✅" title="Lo abrieron todos" description="Ya no queda nadie sin abrirlo." />;
  return <EmptyState icon="⏳" title="Todavía nadie lo abrió" description="Puede tardar un rato en registrarse." />;
}

export function StaffAnnouncementReaders() {
  const { cycleId, sectionId, announcementId } = useParams();
  const [search, setSearch] = useState('');
  const [chosenTab, setChosenTab] = useState(null);
  const { basePath, sectionType, sectionLabel, announcementsPath, breadcrumb } = useStaffSectionContext(cycleId, sectionId);
  const announcementsQuery = useReadReceiptsDeSeccion(sectionId, sectionType);
  const announcement = announcementsQuery.data?.find((candidate) => String(candidate.id) === String(announcementId));
  const recipientsQuery = useDestinatariosDeAviso(announcement && canInspectReaders(announcement) ? announcementId : undefined);
  const { unread, read } = useMemo(() => splitRecipients(recipientsQuery.data ?? []), [recipientsQuery.data]);
  const tab = chosenTab ?? (unread.length ? 'unread' : 'read');
  const visible = (tab === 'unread' ? unread : read).filter((recipient) => matchesRecipient(recipient, search));
  const columns = useMemo(() => buildColumns(basePath, tab === 'read'), [basePath, tab]);

  const header = (
    <div className="flex flex-col gap-1.5">
      <Breadcrumb items={[...breadcrumb, { label: 'Avisos', to: announcementsPath }, { label: 'Quién lo abrió' }]} />
      <h1 className="font-heading text-xl font-bold text-ink md:text-2xl">{announcement?.title ?? 'Quién lo abrió'}</h1>
      {sectionLabel && <p className="text-[12.5px] text-ink-faint">{sectionLabel}</p>}
      {announcement && recipientsQuery.data && (
        <p className="text-[12.5px] text-ink-faint">
          Enviado {dispatchDate(announcement)} · {read.length} de {recipientsQuery.data.length} lo abrieron
        </p>
      )}
    </div>
  );

  function changeTab(next) {
    setChosenTab(next);
    setSearch('');
  }

  let body;
  if (announcementsQuery.isError) {
    body = <LoadErrorState error={announcementsQuery.error} onRetry={() => announcementsQuery.refetch()} resource="el aviso" backTo={announcementsPath} backLabel="los avisos" />;
  } else if (recipientsQuery.isError) {
    body = <LoadErrorState error={recipientsQuery.error} onRetry={() => recipientsQuery.refetch()} resource="quién lo abrió" />;
  } else if (announcementsQuery.isLoading || !sectionType || (announcement && recipientsQuery.isLoading)) {
    body = (
      <GlassCard className="flex flex-col gap-2 p-3" aria-busy="true" aria-label="Cargando">
        {Array.from({ length: 7 }, (_, index) => <Skeleton key={index} className="h-11 w-full rounded-xl" />)}
      </GlassCard>
    );
  } else if (!announcement) {
    body = <LoadErrorState error={{ status: 404 }} backTo={announcementsPath} backLabel="los avisos" />;
  } else if (!canInspectReaders(announcement)) {
    body = (
      <GlassCard className="p-6">
        <EmptyState icon="🕘" title="Este aviso no tiene registro de aperturas" description="Se envió antes de que existiera el acuse de lectura o todavía no se envió." />
      </GlassCard>
    );
  } else if (!recipientsQuery.data?.length) {
    body = (
      <GlassCard className="p-6">
        <EmptyState icon="👥" title="Este aviso no tuvo destinatarios" description="No había alumnos inscriptos cuando se envió." />
      </GlassCard>
    );
  } else {
    body = (
      <GlassCard className="flex flex-col gap-3 p-3 md:p-5">
        <Tabs value={tab} onValueChange={changeTab}>
          <TabsList variant="segmented" className="w-full px-1 sm:w-fit">
            <TabsTrigger value="unread" className="flex-1">
              Sin abrir
              <TabCount count={unread.length} />
            </TabsTrigger>
            <TabsTrigger value="read" className="flex-1">
              Lo abrieron
              <TabCount count={read.length} />
            </TabsTrigger>
          </TabsList>
        </Tabs>
        <Buscador value={search} onChange={setSearch} placeholder="Buscar por nombre o legajo" />
        <DataTable
          compact
          columns={columns}
          data={visible}
          getRowId={(recipient) => String(recipient.studentId)}
          initialSorting={[{ id: 'name', desc: false }]}
          minWidth={480}
          pageSize={PAGE_SIZE}
          emptyState={emptyStateFor(tab, search)}
        />
      </GlassCard>
    );
  }

  return (
    <PageContainer className="gap-3 md:gap-6">
      {header}
      {body}
    </PageContainer>
  );
}
