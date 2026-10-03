import { useState } from 'react';
import { Search, CheckCircle2, XCircle } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { ModalMotivo } from '@/components/director/ModalMotivo';
import { useSubjects, useApproveSubject, useRejectSubject } from '@/features/gestion-academica/hooks/useSubjects';

const GRADE_MODE_LABELS = {
  NUMERIC: 'Numérica',
  PASS_FAIL: 'Aprobado / Desaprobado',
};

function SubjectReviewRow({ subject, onApprove, onReject, isActing }) {
  return (
    <div className="flex w-full items-center justify-between gap-4 rounded-2xl px-4 py-3">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">{subject.code}</span>
        </div>
        <span className="block truncate text-[14px] font-medium text-ink">{subject.name}</span>
        {subject.gradeMode && (
          <span className="text-[12px] text-ink-faint">{GRADE_MODE_LABELS[subject.gradeMode] ?? subject.gradeMode}</span>
        )}
        {subject.description && (
          <p className="mt-1 line-clamp-2 text-[12px] text-ink-faint">{subject.description}</p>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-2">
        <StatusPill tone="warning">Pendiente</StatusPill>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onReject(subject.id)}
          disabled={isActing}
          className="text-danger hover:border-danger/50 hover:text-danger"
        >
          <XCircle className="mr-1.5 h-3.5 w-3.5" />
          Rechazar
        </Button>
        <Button
          variant="gradient"
          size="sm"
          onClick={() => onApprove(subject.id)}
          disabled={isActing}
        >
          <CheckCircle2 className="mr-1.5 h-3.5 w-3.5" />
          Aprobar
        </Button>
      </div>
    </div>
  );
}

export function DirectorSubjectReview() {
  const [search, setSearch] = useState('');
  const [subjectToReject, setSubjectToReject] = useState(null);
  const { data, isLoading, isError } = useSubjects('PENDING_APPROVAL');
  const approveSubject = useApproveSubject();
  const rejectSubject = useRejectSubject();

  const isActing = approveSubject.isPending || rejectSubject.isPending;

  const filtered = (data ?? []).filter((s) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return s.name.toLowerCase().includes(q) || s.code.toLowerCase().includes(q);
  });

  async function handleApprove(id) {
    try {
      await approveSubject.mutateAsync(id);
      toast.success('Materia aprobada');
    } catch {
      toast.error('No se pudo aprobar la materia. Intentá de nuevo.');
    }
  }

  async function handleReject(reason) {
    try {
      await rejectSubject.mutateAsync({ id: subjectToReject, reason });
      setSubjectToReject(null);
      toast.success('Materia rechazada');
    } catch {
      toast.error('No se pudo rechazar la materia. Intentá de nuevo.');
    }
  }

  return (
    <PageContainer titulo="Banco de materias">
      <GlassCard className="p-4">
        <div className="mb-4 flex items-center justify-between gap-4">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
            <Input
              placeholder="Buscar por nombre o código…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>
        </div>

        <div className="mb-3 px-4">
          <h2 className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">
            Pendientes de aprobación
          </h2>
        </div>

        {isLoading && <Skeleton rows={4} />}
        {isError && <EmptyState title="Error al cargar materias" description="Intentá de nuevo en unos momentos." />}
        {!isLoading && !isError && filtered.length === 0 && (
          <EmptyState
            title="Sin materias pendientes"
            description={search ? 'Ninguna materia coincide con la búsqueda.' : 'No hay materias esperando aprobación.'}
          />
        )}
        {!isLoading && !isError && filtered.length > 0 && (
          <div className="divide-y divide-neutral-bg">
            {filtered.map((s) => (
              <SubjectReviewRow
                key={s.id}
                subject={s}
                onApprove={handleApprove}
                onReject={setSubjectToReject}
                isActing={isActing}
              />
            ))}
          </div>
        )}
      </GlassCard>
      <ModalMotivo
        open={subjectToReject !== null}
        onClose={() => setSubjectToReject(null)}
        onConfirmar={handleReject}
        confirmando={rejectSubject.isPending}
        requerido
      />
    </PageContainer>
  );
}
