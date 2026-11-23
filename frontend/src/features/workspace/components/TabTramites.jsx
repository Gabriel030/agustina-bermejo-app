import { StudentProceduresSection } from '@/features/graduation';

export function TabTramites({ carreraId, onSeeStudyPlan }) {
  return <StudentProceduresSection carreraId={carreraId} onSeeStudyPlan={onSeeStudyPlan} />;
}
