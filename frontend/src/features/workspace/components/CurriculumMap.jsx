import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ListFilter } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { ChoiceChip } from '@/components/ChoiceChip';
import { Button } from '@/components/ui/button';
import { CurriculumYearAccordion } from '@/features/workspace/components/CurriculumYearAccordion';
import { MapSubjectCard } from '@/features/workspace/components/MapSubjectCard';
import { SubjectDetailSheet } from '@/features/workspace/components/SubjectDetailSheet';
import { PLAN_STATUS_PILL } from '@/features/workspace/components/planStatusPills';
import {
  ELECTIVES_SECTION_KEY,
  buildCurriculumSections,
  buildRelationIndex,
  getDefaultOpenYear,
  countSubjectsByStatus,
  filterSubjectsByStatus,
  getHighlightedIds,
  getPrerequisitesOf,
} from '@/features/workspace/lib/curriculumMap';
import { PLAN_STATUS_FILTER_OPTIONS, STATUS_FILTER_TO_PLAN_STATUS } from '@/features/workspace/lib/curriculumPlan';
import { useMediaQuery } from '@/hooks/useMediaQuery';
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

function MapSection({ section, creditsDefined, selectedId, highlight, onSelect, registerCard, compact, open, onToggle }) {
  const title = section.key === ELECTIVES_SECTION_KEY ? 'Electivas' : `${section.year}° año`;
  return (
    <CurriculumYearAccordion
      sectionKey={section.key}
      title={title}
      compact={compact}
      open={open}
      onToggle={onToggle}
    >
      <ul className="flex flex-col gap-2.5">
        {section.subjects.map((subject) => (
          <li key={subject.programSubjectId}>
            <MapSubjectCard
              subject={subject}
              creditsDefined={creditsDefined}
              selected={selectedId === subject.programSubjectId}
              relation={highlight.relations.get(subject.programSubjectId) ?? null}
              dimmed={selectedId != null && selectedId !== subject.programSubjectId && !highlight.relations.has(subject.programSubjectId)}
              onSelect={onSelect}
              cardRef={registerCard(subject.programSubjectId)}
            />
          </li>
        ))}
      </ul>
    </CurriculumYearAccordion>
  );
}

export function CurriculumMap({
  subjects,
  statusFilter,
  onStatusFilterChange,
  creditsDefined = true,
  audience = 'student',
}) {
  const compact = useMediaQuery('(max-width: 767px)');
  const [openOverrides, setOpenOverrides] = useState({});
  const [searchParams, setSearchParams] = useSearchParams();
  const [panelOpen, setPanelOpen] = useState(false);
  const cardRefs = useRef(new Map());
  const panelOpenRef = useRef(false);
  useEffect(() => {
    panelOpenRef.current = panelOpen;
  }, [panelOpen]);
  const relationIndex = useMemo(() => buildRelationIndex(subjects), [subjects]);
  const requestedId = Number(searchParams.get('materia'));
  const selectedSubject = useMemo(
    () => (subjects ?? []).find((subject) => subject.programSubjectId === requestedId) ?? null,
    [subjects, requestedId],
  );
  const selectedId = selectedSubject?.programSubjectId ?? null;
  const highlight = useMemo(() => {
    const relations = new Map();
    if (selectedId == null) return { relations };
    const prerequisiteIds = new Set(getPrerequisitesOf(relationIndex, selectedId).map((item) => item.programSubjectId));
    for (const id of getHighlightedIds(relationIndex, selectedId)) {
      relations.set(id, prerequisiteIds.has(id) ? 'prerequisite' : 'dependent');
    }
    return { relations };
  }, [relationIndex, selectedId]);
  const visibleSubjects = useMemo(() => filterSubjectsByStatus(subjects, statusFilter), [subjects, statusFilter]);
  const sections = useMemo(() => buildCurriculumSections(visibleSubjects), [visibleSubjects]);
  const yearSections = sections.filter((section) => section.key !== ELECTIVES_SECTION_KEY);
  const electivesSection = sections.find((section) => section.key === ELECTIVES_SECTION_KEY);
  const defaultOpenYear = getDefaultOpenYear(sections);
  const scrolls = yearSections.length > SCROLLING_YEARS_THRESHOLD;

  const registerCard = useCallback(
    (programSubjectId) => (node) => {
      if (node) cardRefs.current.set(programSubjectId, node);
      else cardRefs.current.delete(programSubjectId);
    },
    [],
  );

  const focusSelectedCard = useCallback(() => {
    cardRefs.current.get(selectedId)?.focus();
  }, [selectedId]);

  const writeSelection = useCallback(
    (programSubjectId) => {
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current);
          if (programSubjectId == null) next.delete('materia');
          else next.set('materia', String(programSubjectId));
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  function selectSubject(programSubjectId) {
    writeSelection(programSubjectId);
    setPanelOpen(true);
  }

  function isSectionOpen(section) {
    return openOverrides[section.key] ?? (section.year === defaultOpenYear || section.subjects.some((subject) => subject.programSubjectId === selectedId));
  }

  function toggleSection(section) {
    setOpenOverrides((current) => ({ ...current, [section.key]: !isSectionOpen(section) }));
  }

  function clearSelection() {
    focusSelectedCard();
    writeSelection(null);
    setPanelOpen(false);
  }

  useEffect(() => {
    if (selectedId == null) return undefined;
    function clearOnEscape(event) {
      if (event.key !== 'Escape' || panelOpenRef.current) return;
      cardRefs.current.get(selectedId)?.focus();
      writeSelection(null);
    }
    document.addEventListener('keydown', clearOnEscape, true);
    return () => document.removeEventListener('keydown', clearOnEscape, true);
  }, [selectedId, writeSelection]);

  return (
    <div className="flex flex-col gap-4" data-audience={audience}>
      <MapStatusChips subjects={subjects} value={statusFilter} onChange={onStatusFilterChange} />
      {selectedSubject && !panelOpen && (
        <div
          role="status"
          className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-xl border border-primary bg-accent-soft px-3 py-2 text-size-13 text-ink"
        >
          <span className="min-w-0 font-semibold">Resaltando correlativas de {selectedSubject.subjectName}</span>
          <Button variant="outline" className="h-11" onClick={() => setPanelOpen(true)}>
            Ver detalle
          </Button>
          <Button variant="outline" className="h-11" onClick={clearSelection}>
            Quitar
          </Button>
        </div>
      )}
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
                    highlight={highlight}
                    onSelect={selectSubject}
                    registerCard={registerCard}
                    compact={compact}
                    open={isSectionOpen(section)}
                    onToggle={() => toggleSection(section)}
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
                highlight={highlight}
                onSelect={selectSubject}
                registerCard={registerCard}
                compact={compact}
                open={isSectionOpen(electivesSection)}
                onToggle={() => toggleSection(electivesSection)}
              />
            </div>
          )}
        </>
      )}
      <SubjectDetailSheet
        subject={selectedSubject}
        relationIndex={relationIndex}
        creditsDefined={creditsDefined}
        open={panelOpen && selectedSubject != null}
        onOpenChange={setPanelOpen}
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          focusSelectedCard();
        }}
      />
    </div>
  );
}
