import { useMemo, useState } from 'react';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { exportarAsistencia } from '@/features/attendance/hooks/useDocenteAsistencia';

export function groupDatesByMonth(dates) {
  const groups = new Map();
  for (const date of dates) {
    const month = date.slice(0, 7);
    if (!groups.has(month)) groups.set(month, []);
    groups.get(month).push(date);
  }
  const label = new Intl.DateTimeFormat('es-AR', { month: 'long', year: 'numeric' });
  return [...groups.entries()].map(([month, monthDates]) => ({
    month,
    label: label.format(new Date(`${month}-02T00:00:00`)),
    dates: monthDates,
  }));
}

export function useAttendanceExport({ type, sectionId, classDates, scope, onDone }) {
  const [format, setFormat] = useState('pdf');
  const [yearChoice, setYearChoice] = useState(null);
  const [dateChoice, setDateChoice] = useState(null);
  const [pending, setPending] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  const [seenScope, setSeenScope] = useState(scope);
  if (scope !== seenScope) {
    setSeenScope(scope);
    if (scope) {
      setFormat('pdf');
      setYearChoice(null);
      setDateChoice(null);
      setErrorMessage(null);
    }
  }

  const years = useMemo(() => [...new Set(classDates.map((date) => date.slice(0, 4)))].sort(), [classDates]);
  const year = yearChoice && years.includes(yearChoice) ? yearChoice : years[years.length - 1] ?? '';
  const datesOfYear = useMemo(() => classDates.filter((date) => date.startsWith(year)), [classDates, year]);
  const classDate = dateChoice && datesOfYear.includes(dateChoice) ? dateChoice : datesOfYear[datesOfYear.length - 1] ?? '';
  const monthGroups = useMemo(() => groupDatesByMonth(datesOfYear), [datesOfYear]);
  const needsClassDate = scope === 'class';

  async function submit() {
    setPending(true);
    setErrorMessage(null);
    try {
      await exportarAsistencia(type, sectionId, { format, scope, classDate: needsClassDate ? classDate : undefined });
      toast.success('Exportación lista: se descargó el archivo.');
      onDone();
    } catch (error) {
      setErrorMessage(describeError(error, { fallback: 'No pudimos generar el archivo. Probá de nuevo.' }).message);
    } finally {
      setPending(false);
    }
  }

  return {
    format,
    setFormat,
    years,
    year,
    setYear: (value) => {
      setYearChoice(value);
      setDateChoice(null);
    },
    classDate,
    setClassDate: setDateChoice,
    monthGroups,
    needsClassDate,
    pending,
    errorMessage,
    canSubmit: !needsClassDate || Boolean(classDate),
    submit,
  };
}
