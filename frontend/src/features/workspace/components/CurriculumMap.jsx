import { useMemo, useState } from 'react';
import { ListFilter } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { ChoiceChip } from '@/components/ChoiceChip';
import { Button } from '@/components/ui/button';
import { MapSubjectCard } from '@/features/workspace/components/MapSubjectCard';
import { PLAN_STATUS_PILL } from '@/features/workspace/components/planStatusPills';
import {
  ELECTIVES_SECTION_KEY,
  buildCurriculumSections,
  countSubjectsByStatus,
  filterSubjectsByStatus,
} from '@/features/workspace/lib/curriculumMap';
import { PLAN_STATUS_FILTER_OPTIONS, STATUS_FILTER_TO_PLAN_STATUS } from '@/features/workspace/lib/curriculumPlan';
import { cn } from '@/lib/utils';

const SCROLLING_YEARS_THRESHOLD = 3;

function MapStatusChips({ subjects, value, onChange }) {
  const counts = useMemo(() => countSubjectsByStatus(subjects), [subjects]);
  return (
    <div role="group" aria-label="Filtrar por estado" className="flex flex-wrap gap-2">
      {PLAN_STATUS_FILTER_OPTIONS.map((option) => {
        const status = STATUS_FILTER_TO_PLAN_STATUS[option.id];
        const Icon = status ? PLAN_STATUS_PILL[status].Icon : ListFilter;
        const count = status ? counts[status] : subjects.length;
        return (
          <ChoiceChip
            key={option.id}
            selected={value === option.id}
            onClick={() => onChange(option.id)}
            size="md"
            className="inline-flex min-h-11 min-w-11 items-center gap-1.5"
          >
            <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            {option.label}
            <span className="tabular-nums">{count}</span>
          </ChoiceChip>
        );
      })}
    </div>
  );
}

function MapSection({ section, creditsDefined, selectedId, onSelect }) {
  const headingId = `curriculum-map-${section.key}`;
  const title = section.key === ELECTIVES_SECTION_KEY ? 'Electivas' : `${section.year}° año`;
  return (
    <section aria-labelledby={headingId} className="flex min-w-0 flex-col gap-2.5">
      <h2 id={headingId} className="text-size-11 font-bold tracking-wide text-ink-faint uppercase">
        {title}
      </h2>
      <ul className="flex flex-col gap-2.5">
        {section.subjects.map((subject) => (
          <li key={subject.programSubjectId}>
            <MapSubjectCard
              subject={subject}
              creditsDefined={creditsDefined}
              selected={selectedId === subject.programSubjectId}
              onSelect={onSelect}
            />
          </li>
        ))}
      </ul>
    </section>
  );
}

export function CurriculumMap({
  subjects,
  statusFilter,
  onStatusFilterChange,
  creditsDefined = true,
  audience = 'student',
}) {
  const [selectedId, setSelectedId] = useState(null);
  const visibleSubjects = useMemo(() => filterSubjectsByStatus(subjects, statusFilter), [subjects, statusFilter]);
  const sections = useMemo(() => buildCurriculumSections(visibleSubjects), [visibleSubjects]);
  const yearSections = sections.filter((section) => section.key !== ELECTIVES_SECTION_KEY);
  const electivesSection = sections.find((section) => section.key === ELECTIVES_SECTION_KEY);
  const scrolls = yearSections.length > SCROLLING_YEARS_THRESHOLD;

  function toggleSelection(programSubjectId) {
    setSelectedId((current) => (current === programSubjectId ? null : programSubjectId));
  }

  return (
    <div className="flex flex-col gap-4" data-audience={audience}>
      <MapStatusChips subjects={subjects} value={statusFilter} onChange={onStatusFilterChange} />
      {visibleSubjects.length === 0 ? (
        <EmptyState
          title="Ninguna materia coincide"
          description="Probá con otro estado."
          action={
            <Button variant="outline" className="h-11" onClick={() => onStatusFilterChange('todas')}>
              <ListFilter className="h-4 w-4" aria-hidden="true" />
              Quitar filtro
            </Button>
          }
        />
      ) : (
        <>
          {yearSections.length > 0 && (
            <div
              role="region"
              aria-label="Mapa del plan por año"
              tabIndex={scrolls ? 0 : undefined}
              className={cn('md:overflow-x-auto', scrolls && 'rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring')}
            >
              <div
                style={{ '--years': yearSections.length }}
                className={cn(
                  'grid grid-cols-1 gap-4',
                  scrolls
                    ? 'md:grid-cols-[repeat(var(--years),minmax(16rem,1fr))]'
                    : 'md:grid-cols-[repeat(var(--years),minmax(0,1fr))]',
                )}
              >
                {yearSections.map((section) => (
                  <MapSection
                    key={section.key}
                    section={section}
                    creditsDefined={creditsDefined}
                    selectedId={selectedId}
                    onSelect={toggleSelection}
                  />
                ))}
              </div>
            </div>
          )}
          {electivesSection && (
            <div className="grid grid-cols-1 md:grid-cols-3">
              <MapSection
                section={electivesSection}
                creditsDefined={creditsDefined}
                selectedId={selectedId}
                onSelect={toggleSelection}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
}
