import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Gift, Plus } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { InfoTip } from '@/components/charts/InfoTip';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { enrollmentKey, useEnrollmentStudentDiscounts } from '../hooks/useStudentDiscounts';
import { inboxPath } from '../authorization/authorizationRequests';
import { RevokeDiscountDialog } from './RevokeDiscountDialog';
import { StudentDiscountDetail } from './StudentDiscountDetail';
import { buildStudentDiscountColumns } from './studentDiscountColumns';

export function StudentDiscountsSection({ studentId, enrollment, roleBase }) {
  const navigate = useNavigate();
  const isDirector = roleBase === 'director';
  const query = useEnrollmentStudentDiscounts(enrollment);
  const [revoking, setRevoking] = useState(null);
  const columns = useMemo(() => buildStudentDiscountColumns({ isDirector, onRevoke: setRevoking }), [isDirector]);
  const grantPath = `/${roleBase}/alumnos/${studentId}/descuentos/nuevo?enrollment=${enrollmentKey(enrollment)}`;
  const grantLabel = isDirector ? 'Otorgar descuento' : 'Proponer descuento';

  return (
    <section className="flex flex-col gap-3" aria-labelledby="student-discounts-heading">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <h3 id="student-discounts-heading" className="font-heading text-base font-semibold text-ink">Descuentos</h3>
          <InfoTip>No se acumulan: en cada cuota se aplica el descuento que deja el menor precio. Si uno pierde ese mes, no se corre al siguiente.</InfoTip>
        </div>
        <Button asChild variant="gradient" size="sm">
          <Link to={grantPath}>
            <Plus aria-hidden="true" />
            {grantLabel}
          </Link>
        </Button>
      </div>
      <GlassCard className="overflow-x-auto p-0">
        <DataTable
          columns={columns}
          data={query.data}
          getRowId={(discount) => String(discount.id)}
          isLoading={query.isLoading}
          isError={query.isError}
          onRetry={() => query.refetch()}
          errorMessage="No pudimos cargar los descuentos."
          expandLabel="Ver detalle"
          minWidth={760}
          emptyState={
            <EmptyState
              icon={<Gift className="mx-auto size-6 text-ink-faint" />}
              title="Sin descuentos"
              description="Esta inscripción no tiene descuentos todavía."
            />
          }
          renderExpanded={(row) => <StudentDiscountDetail discount={row.original} />}
        />
      </GlassCard>
      {revoking && (
        <RevokeDiscountDialog
          discount={revoking}
          isDirector={isDirector}
          onClose={() => setRevoking(null)}
          onSeeRequests={() => navigate(inboxPath(roleBase))}
        />
      )}
    </section>
  );
}
