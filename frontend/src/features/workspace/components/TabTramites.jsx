import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { CertificateActions } from '@/features/workspace/components/CertificateActions';
import { GraduationTab, RegularCertificateCard } from '@/features/graduation';

export function TabTramites({ carreraId, onSeeStudyPlan }) {
  return (
    <div className="flex flex-col gap-4">
      <section aria-label="Constancias" className="flex flex-col gap-4">
        <RegularCertificateCard carreraId={carreraId} />
        <GlassCard className="flex flex-wrap items-center justify-between gap-2.5 p-4">
          <div className="flex flex-wrap items-center gap-2.5">
            <h2 className="font-heading text-base font-semibold text-ink">Materias aprobadas</h2>
            <StatusPill tone="neutral">Sin código de verificación</StatusPill>
          </div>
          <CertificateActions carreraId={carreraId} />
        </GlassCard>
      </section>
      <GraduationTab carreraId={carreraId} onSeeStudyPlan={onSeeStudyPlan} />
    </div>
  );
}
