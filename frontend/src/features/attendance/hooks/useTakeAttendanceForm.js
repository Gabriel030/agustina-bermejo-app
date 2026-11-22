import { useEffect, useMemo, useRef, useState } from 'react';
import { localIsoDate } from '@/lib/calendario';
import { clearAttendanceDraft, loadAttendanceDraft, saveAttendanceDraft } from '@/features/attendance/lib/attendanceDraft';
import { EDITABLE_SAVED_STATUSES, isOfflineError, saveErrorMessage } from '@/features/attendance/lib/attendanceStatuses';
import {
  useRosterDeAsistencia,
  useGuardarAsistencia,
  useCancelarClase,
  useClaseDelDia,
  useHistorialAsistencia,
} from '@/features/attendance/hooks/useDocenteAsistencia';

export function useTakeAttendanceForm(type, sectionId) {
  const rosterQuery = useRosterDeAsistencia(type, sectionId);
  const { data: roster, isLoading: loadingRoster } = rosterQuery;
  const historyQuery = useHistorialAsistencia(type, sectionId);
  const { data: history } = historyQuery;
  const save = useGuardarAsistencia(type, sectionId);
  const cancel = useCancelarClase(type, sectionId);

  const [classDate, setClassDate] = useState(() => localIsoDate());
  const classQuery = useClaseDelDia(type, sectionId, classDate);
  const { data: classOfTheDay } = classQuery;
  const [search, setSearch] = useState('');
  const [statuses, setStatuses] = useState({});
  const [topic, setTopic] = useState('');
  const [observations, setObservations] = useState('');
  const [hasChanges, setHasChanges] = useState(false);
  const [error, setError] = useState(null);
  const [recovered, setRecovered] = useState(false);
  const hasChangesRef = useRef(false);
  const previousKey = useRef(null);
  const draftAppliedFor = useRef(null);
  const draftKey = `${type}:${sectionId}:${classDate}`;

  const students = useMemo(() => roster ?? [], [roster]);

  const savedCells = useMemo(() => {
    if (!history) return {};
    const byStudentId = new Map(history.rows.map((row) => [row.studentId, row.cellsByDate[classDate]]));
    const result = {};
    for (const student of students) {
      const cell = byStudentId.get(student.studentId);
      if (cell) result[student.enrollmentId] = cell;
    }
    return result;
  }, [history, students, classDate]);
  const savedStatuses = useMemo(
    () => Object.fromEntries(Object.entries(savedCells).map(([enrollmentId, cell]) => [enrollmentId, cell.status])),
    [savedCells],
  );
  const hasSavedAttendance = Object.keys(savedStatuses).length > 0;
  const isToday = classDate === localIsoDate();
  const canCorrectSaved = (enrollmentId) => isToday && EDITABLE_SAVED_STATUSES.includes(savedStatuses[enrollmentId]);

  useEffect(() => {
    hasChangesRef.current = hasChanges;
  }, [hasChanges]);

  useEffect(() => {
    const keyChanged = previousKey.current !== draftKey;
    previousKey.current = draftKey;
    if (!keyChanged && hasChangesRef.current) {
      setStatuses((prev) => ({ ...prev, ...savedStatuses }));
      return;
    }
    const draft = loadAttendanceDraft(type, sectionId, classDate);
    const pending = draft ? Object.fromEntries(Object.entries(draft.estados).filter(([id]) => savedStatuses[id] == null)) : {};
    if (draft && Object.keys(pending).length > 0) {
      setStatuses({ ...pending, ...savedStatuses });
      setTopic(draft.topic);
      setObservations(draft.observations);
      setHasChanges(true);
      setRecovered(true);
      draftAppliedFor.current = draftKey;
    } else {
      if (draft) clearAttendanceDraft(type, sectionId, classDate);
      setStatuses(savedStatuses);
      setHasChanges(false);
      setRecovered(false);
      draftAppliedFor.current = null;
    }
    setError(null);
  }, [type, sectionId, classDate, draftKey, savedStatuses]);

  useEffect(() => {
    if (hasChanges && Object.keys(statuses).length > 0) {
      saveAttendanceDraft(type, sectionId, classDate, { estados: statuses, topic, observations });
    }
  }, [hasChanges, statuses, topic, observations, type, sectionId, classDate]);

  useEffect(() => {
    if (classOfTheDay && draftAppliedFor.current !== draftKey) {
      setTopic(classOfTheDay.topic ?? '');
      setObservations(classOfTheDay.observations ?? '');
    }
  }, [classOfTheDay, draftKey]);

  const canceled = classOfTheDay?.status === 'CANCELED';

  const filteredStudents = useMemo(
    () => students.filter((s) => s.studentName.toLowerCase().includes(search.toLowerCase()) || (s.dni ?? '').includes(search)),
    [students, search],
  );

  function changeStatus(enrollmentId, status) {
    setStatuses((prev) => ({ ...prev, [enrollmentId]: status }));
    setHasChanges(true);
  }

  function markAllPresent() {
    setStatuses((prev) => {
      const next = { ...prev };
      for (const student of students) {
        if (!next[student.enrollmentId]) next[student.enrollmentId] = 'PRESENT';
      }
      return next;
    });
    setHasChanges(true);
  }

  async function saveAttendance() {
    setError(null);
    const entries = Object.entries(statuses).map(([enrollmentId, status]) => ({ enrollmentId: Number(enrollmentId), status }));
    try {
      await save.mutateAsync({ classDate, entries, topic: topic || null, observations: observations || null });
      clearAttendanceDraft(type, sectionId, classDate);
      draftAppliedFor.current = null;
      setRecovered(false);
      setHasChanges(false);
    } catch (err) {
      setError({ message: saveErrorMessage(err), offline: isOfflineError(err) });
    }
  }

  function discardChanges() {
    clearAttendanceDraft(type, sectionId, classDate);
    draftAppliedFor.current = null;
    setStatuses(savedStatuses);
    setTopic(classOfTheDay?.topic ?? '');
    setObservations(classOfTheDay?.observations ?? '');
    setHasChanges(false);
    setRecovered(false);
    setError(null);
  }

  async function cancelClass() {
    await cancel.mutateAsync(classDate);
  }

  const values = Object.values(statuses);
  const markedCount = values.length;
  const changedCount = Object.entries(statuses).filter(([id, status]) => savedStatuses[id] !== status).length;

  return {
    rosterQuery,
    historyQuery,
    classQuery,
    loadingRoster,
    classDate,
    setClassDate,
    search,
    setSearch,
    students,
    filteredStudents,
    statuses,
    savedCells,
    savedStatuses,
    topic,
    setTopic,
    observations,
    setObservations,
    hasChanges,
    changedCount,
    error,
    recovered,
    canceled,
    hasSavedAttendance,
    isToday,
    canCorrectSaved,
    saving: save.isPending,
    markedCount,
    presentCount: values.filter((s) => s === 'PRESENT').length,
    lateCount: values.filter((s) => s === 'LATE').length,
    absentCount: values.filter((s) => s === 'ABSENT').length,
    unmarkedCount: students.length - markedCount,
    changeStatus,
    markAllPresent,
    saveAttendance,
    discardChanges,
    cancelClass,
  };
}
