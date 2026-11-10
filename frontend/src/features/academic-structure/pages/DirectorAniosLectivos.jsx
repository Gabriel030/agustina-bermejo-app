import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { AniosLectivosSection } from '@/features/academic-structure/components/AniosLectivosSection';
import { usePrograms } from '@/features/academic-structure/hooks/usePrograms';
import { NativeSelect } from '@/components/ui/native-select';

export function DirectorAniosLectivos() {
  const { data: programs, isLoading, isError, error, refetch } = usePrograms();
  const [programId, setProgramId] = useState('');

  return (
    <PageContainer title="Años lectivos">
      <Link to="/director/financiero" className="-mt-4 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink">
        <ChevronLeft className="h-3.5 w-3.5" />
        Financiero
      </Link>
      {isLoading ? (
        <Skeleton rows={2} />
      ) : isError ? (
        <LoadErrorState error={error} resource="las carreras" onRetry={refetch} />
      ) : (
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <label htmlFor="anios-carrera" className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">Carrera</label>
            <NativeSelect className="max-w-md" id="anios-carrera" value={programId} onChange={(e) => setProgramId(e.target.value)}>
              <option value="">Elegí una carrera</option>
              {(programs ?? []).map((p) => <option key={p.id} value={String(p.id)}>{p.name}</option>)}
            </NativeSelect>
          </div>
          {programId && <AniosLectivosSection key={programId} programId={programId} canEdit />}
        </div>
      )}
    </PageContainer>
  );
}
