import { EquivalenceComparisonTable } from '@/features/workspace/components/EquivalenceComparisonTable';

export function EquivalenceReview({ effect }) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-size-13 text-ink">
        <span className="font-semibold">{effect.subject.subjectName}</span> <span className="text-ink-faint">({effect.subject.subjectCode})</span> pasa a estar aprobada por equivalencia.
      </p>
      <div className="overflow-x-auto">
        <EquivalenceComparisonTable effect={effect} />
      </div>
      <section className="flex flex-col gap-2" aria-labelledby="equivalence-unlocked-title">
        <h3 id="equivalence-unlocked-title" className="text-size-11 font-bold tracking-wide text-ink-faint uppercase">
          Materias que se desbloquean
        </h3>
        {effect.unlocked.length > 0 ? (
          <ul className="flex flex-col gap-1 text-size-13 text-ink">
            {effect.unlocked.map((subject) => (
              <li key={subject.programSubjectId}>
                {subject.subjectName} <span className="text-ink-faint">({subject.subjectCode})</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-size-13 text-ink-faint">No desbloquea otras materias.</p>
        )}
      </section>
      <p className="text-size-12 text-ink-faint">Los valores son una estimación: el sistema los recalcula al guardar.</p>
    </div>
  );
}
