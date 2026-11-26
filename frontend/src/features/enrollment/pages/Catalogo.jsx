import { Link, useSearchParams } from 'react-router-dom';
import { Breadcrumb } from '@/components/Breadcrumb';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';
import { NextInformativeSessionStrip } from '@/features/enrollment/components/NextInformativeSessionStrip';
import { OfferingResults } from '@/features/enrollment/components/OfferingResults';
import { TabMisSolicitudes } from '@/features/enrollment/components/TabMisSolicitudes';
import { useCatalogOffering, useProgramAdmissionBadges } from '@/features/enrollment/hooks/useCatalogOffering';
import {
  CATALOG_PATH,
  describeOfferingView,
  filterOfferingByQuery,
  hasOfferingFilters,
  parseOfferingFilters,
} from '@/features/enrollment/lib/catalogOffering';

const MY_APPLICATIONS_TAB = 'mis-solicitudes';

function MyApplicationsView() {
  return (
    <PageContainer>
      <Breadcrumb items={[{ label: 'Oferta académica', to: CATALOG_PATH }, { label: 'Mis solicitudes' }]} />
      <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">Mis solicitudes</h1>
      <TabMisSolicitudes />
    </PageContainer>
  );
}

function OfferingView({ filters }) {
  const offering = useCatalogOffering(filters);
  const items = filterOfferingByQuery(offering.items, filters.q);
  const badges = useProgramAdmissionBadges(items);
  const filtered = hasOfferingFilters(filters);
  const { title, crumbs } = describeOfferingView(filters);
  let status = 'ready';
  if (offering.isLoading) status = 'loading';
  else if (offering.isError) status = 'error';

  return (
    <PageContainer>
      {filtered && <Breadcrumb items={crumbs} />}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">{title}</h1>
          {!filtered && <p className="text-sm text-ink-soft">Elegí una carrera o un curso para ver el detalle e inscribirte.</p>}
        </div>
        <Button asChild variant="outline" className="min-h-11 sm:min-h-9">
          <Link to={`${CATALOG_PATH}?tab=${MY_APPLICATIONS_TAB}`}>Mis solicitudes</Link>
        </Button>
      </div>
      {!filtered && status === 'ready' && <NextInformativeSessionStrip />}
      <OfferingResults
        status={status}
        items={items}
        badges={badges}
        filtered={filtered}
        error={offering.error}
        onRetry={offering.refetch}
      />
    </PageContainer>
  );
}

export function Catalogo() {
  const [searchParams] = useSearchParams();
  if (searchParams.get('tab') === MY_APPLICATIONS_TAB) return <MyApplicationsView />;
  return <OfferingView filters={parseOfferingFilters(searchParams)} />;
}
