import { Link, useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { formatDate } from '@/lib/billing';
import { TermsDocument } from './TermsDocument';
import { TERMS_PATH } from './annualTerms';
import { useCurrentTerms, useTermsVersion } from './useAnnualUpfrontTerms';

function LoadingTerms() {
  return (
    <GlassCard className="flex flex-col gap-3 p-6" aria-busy="true" aria-label="Cargando los términos">
      {[0, 1, 2, 3, 4].map((line) => (
        <Skeleton key={line} className="h-4 w-full" />
      ))}
    </GlassCard>
  );
}

function TermsView({ terms }) {
  const navigate = useNavigate();
  return (
    <>
      {!terms.current && (
        <GlassCard className="flex flex-wrap items-center justify-between gap-3 p-4" role="status">
          <span className="text-sm text-ink-soft">Esta versión ya no rige.</span>
          <Button asChild variant="outline" size="sm">
            <Link to={TERMS_PATH}>Ver la versión vigente</Link>
          </Button>
        </GlassCard>
      )}
      <GlassCard className="flex flex-col gap-4 p-6">
        <div className="flex flex-wrap items-center gap-2 text-sm text-ink-soft">
          <StatusPill tone={terms.current ? 'success' : 'neutral'}>{terms.current ? 'Vigente' : 'Reemplazada'}</StatusPill>
          <span className="font-semibold text-ink">Versión {terms.version}</span>
          <span>Desde el {formatDate(terms.publishedAt)}</span>
        </div>
        <TermsDocument text={terms.text} />
      </GlassCard>
      <div>
        <Button variant="outline" onClick={() => navigate(-1)}>
          Volver
        </Button>
      </div>
    </>
  );
}

export function AnnualTermsPage() {
  const { versionNumber } = useParams();
  const current = useCurrentTerms(versionNumber == null);
  const specific = useTermsVersion(versionNumber);
  const query = versionNumber == null ? current : specific;

  return (
    <PageContainer title="Términos y condiciones" description="Pago anual por adelantado.">
      {query.isPending && <LoadingTerms />}
      {query.isError && <LoadErrorState error={query.error} onRetry={() => query.refetch()} resource="los términos" />}
      {query.data && <TermsView terms={query.data} />}
    </PageContainer>
  );
}
