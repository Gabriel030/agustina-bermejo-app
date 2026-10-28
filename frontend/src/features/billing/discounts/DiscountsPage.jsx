import { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { useAuth } from '@/context/AuthContext';
import { useDiscountDefinitions } from '../hooks/useDiscountDefinitions';
import { useOfferNames } from '../hooks/useOfferNames';
import { roleBaseOf } from '../authorization/authorizationRequests';
import { DefinitionsSection } from './DefinitionsSection';
import { GrantedDiscountsTab } from './GrantedDiscountsTab';
import { LostBenefitsSection } from './LostBenefitsSection';
import { PendingRequestsNotice } from './PendingRequestsNotice';
import { buildCatalogColumns, buildPromotionColumns } from './definitionColumns';
import { CATALOG_DISCOUNT_TYPES, definitionStatusKey } from './discountLabels';

const TABS = ['catalog', 'promotions', 'granted'];
const DEFAULT_TAB = 'catalog';

const CATALOG_TIP =
  'Un descuento no se acumula con otros: en cada cuota se aplica el que deja el menor precio. Si empatan, gana beca, después promoción de cohorte, después pago anual.';
const PROMOTIONS_TIP =
  'Se aplica sola al inscribirse dentro de la ventana de inscripción y mientras haya cupo. Cada alumno puede tener una sola promoción de cohorte; si cumple varias, se le asigna la que más ahorra.';

const CATALOG_GROUPS = [
  { id: 'all', label: 'Todos', match: () => true },
  { id: 'active', label: 'Vigentes', match: (entry) => definitionStatusKey(entry) === 'ACTIVE' },
  { id: 'closed', label: 'Cerrados', match: (entry) => definitionStatusKey(entry) === 'CLOSED' },
];

const PROMOTION_GROUPS = [
  { id: 'all', label: 'Todas', match: () => true },
  { id: 'open', label: 'Abiertas', match: (entry) => entry.cohortPromotionStatus === 'OPEN' },
  { id: 'closed', label: 'Cerradas', match: (entry) => entry.cohortPromotionStatus === 'CLOSED' },
];

export function DiscountsPage() {
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const isDirector = roleBase === 'director';
  const basePath = `/${roleBase}/descuentos`;
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab');
  const tab = TABS.includes(requestedTab) ? requestedTab : DEFAULT_TAB;

  const definitionsQuery = useDiscountDefinitions();
  const names = useOfferNames();
  const catalogEntries = useMemo(
    () => (definitionsQuery.data ?? []).filter((entry) => CATALOG_DISCOUNT_TYPES.includes(entry.discountType)),
    [definitionsQuery.data],
  );
  const promotionEntries = useMemo(
    () => (definitionsQuery.data ?? []).filter((entry) => entry.discountType === 'COHORT_PROMOTION'),
    [definitionsQuery.data],
  );
  const catalogColumns = useMemo(() => buildCatalogColumns({ basePath }), [basePath]);
  const promotionColumns = useMemo(() => buildPromotionColumns({ basePath, names }), [basePath, names]);

  function changeTab(next) {
    setSearchParams(next === DEFAULT_TAB ? {} : { tab: next }, { replace: true });
  }

  return (
    <PageContainer title="Descuentos y promociones" description="Becas, pago anual, promociones de cohorte y descuentos otorgados">
      <PendingRequestsNotice roleBase={roleBase} />
      <Tabs value={tab} onValueChange={changeTab} className="gap-4">
        <TabsList variant="segmented" className="w-full sm:w-fit">
          <TabsTrigger value="catalog" className="flex-1">Becas y pago anual</TabsTrigger>
          <TabsTrigger value="promotions" className="flex-1">Promociones de cohorte</TabsTrigger>
          <TabsTrigger value="granted" className="flex-1">Otorgados</TabsTrigger>
        </TabsList>

        <TabsContent value="catalog" className="flex flex-col gap-6">
          <DefinitionsSection
            title="Becas y pago anual"
            tip={CATALOG_TIP}
            createTo={`${basePath}/nuevo`}
            createLabel={isDirector ? 'Nuevo descuento' : 'Proponer descuento'}
            emptyTitle="Todavía no hay descuentos"
            emptyDescription={isDirector ? 'Creá el primero para ofrecerlo a los alumnos.' : 'Proponé el primero y el director lo aprueba.'}
            entries={catalogEntries}
            isLoading={definitionsQuery.isLoading}
            isError={definitionsQuery.isError}
            onRetry={() => definitionsQuery.refetch()}
            columns={catalogColumns}
            detailPath={(entry) => `${basePath}/${entry.id}`}
            mobileGroups={CATALOG_GROUPS}
          />
        </TabsContent>

        <TabsContent value="promotions" className="flex flex-col gap-6">
          <DefinitionsSection
            title="Promociones de cohorte"
            tip={PROMOTIONS_TIP}
            createTo={`${basePath}/promociones/nueva`}
            createLabel={isDirector ? 'Nueva promoción' : 'Proponer promoción'}
            emptyTitle="Todavía no hay promociones"
            emptyDescription={isDirector ? 'Creá una para una carrera, una cohorte o un curso.' : 'Proponé la primera y el director la aprueba.'}
            entries={promotionEntries}
            isLoading={definitionsQuery.isLoading}
            isError={definitionsQuery.isError}
            onRetry={() => definitionsQuery.refetch()}
            columns={promotionColumns}
            detailPath={(entry) => `${basePath}/${entry.id}`}
            mobileGroups={PROMOTION_GROUPS}
          />
          {promotionEntries.length > 0 && <LostBenefitsSection roleBase={roleBase} promotions={promotionEntries} />}
        </TabsContent>

        <TabsContent value="granted">
          <GrantedDiscountsTab roleBase={roleBase} />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
