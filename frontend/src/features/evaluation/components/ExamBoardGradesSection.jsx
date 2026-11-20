import { useMemo, useState } from 'react';
import { toast } from 'sonner';
import { ChangesTable } from '@/components/ChangesTable';
import { EmptyState } from '@/components/EmptyState';
import { Notice } from '@/components/Notice';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { ERROR_MESSAGES, describeError } from '@/api/describeError';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { formatearFecha } from '@/lib/comisiones';
import { useUnsavedChangesGuard } from '@/hooks/useUnsavedChangesGuard';
import { useSaveFinalGrades } from '../hooks/useMesasDeExamen';
import {
  boardAccess,
  boardChangeRows,
  boardChanges,
  buildCorrectionPayload,
  buildFinalGradesPayload,
  correctionAccess,
  hasInvalidBoardDraft,
  patchBoardDraft,
  removeBoardDraft,
  summarizeBoard,
  summarizeBoardSave,
} from '../lib/examBoardGrades';
import { formatScore } from '../lib/gradeGrid';
import { ExamBoardCorrectionSheet } from './ExamBoardCorrectionSheet';
import { GradeGridTable } from './GradeGridTable';

const ROW_REJECTED = 'EXAM_BOARD_FINAL_GRADE_ROW_REJECTED';
const UNSAVED_MESSAGE = 'Tenés notas del final sin guardar. Si salís las perdés.';

function approvalRule(rows) {
  const grades = [...new Set(rows.map((row) => row.minApprovalGrade))];
  return grades.length === 1 ? `Aprueba con ${formatScore(grades[0])} o más` : null;
}

function batchFailure(error, rows) {
  const details = error?.details;
  if (error?.code === ROW_REJECTED && details?.registrationId != null) {
    const row = rows.find((candidate) => candidate.id === details.registrationId);
    const cause = ERROR_MESSAGES[details.causeCode] ?? 'La fila fue rechazada.';
    return { registrationId: details.registrationId, student: row?.studentName ?? null, message: `No se guardó ninguna nota: ${cause}` };
  }
  return { registrationId: null, student: null, message: `No se guardó ninguna nota: ${describeError(error).message}` };
}

function ReviewSheet({ open, changes, failure, saving, onClose, onConfirm, onDropFailed }) {
  return (
    <Sheet open={open} onOpenChange={(next) => !next && !saving && onClose()}>
      <SheetContent className="flex flex-col gap-4 overflow-y-auto p-4">
        <SheetHeader className="p-0">
          <SheetTitle>Revisá antes de guardar</SheetTitle>
          <SheetDescription>Se guardan todas las notas juntas. Si alguna falla, no se guarda ninguna.</SheetDescription>
        </SheetHeader>
        {failure && (
          <div role="alert" className="flex flex-col items-start gap-2 rounded-xl bg-danger-bg px-3 py-2.5 text-size-13 text-pill-danger">
            <span className="font-semibold">{failure.message}</span>
            <span>Tus cambios siguen acá.</span>
            {failure.registrationId != null && failure.student && (
              <Button type="button" variant="outline" size="sm" onClick={onDropFailed}>
                Quitar a {failure.student} y revisar el resto
              </Button>
            )}
          </div>
        )}
        <ChangesTable rows={changes} />
        <SheetFooter className="mt-auto flex-row justify-end gap-2 p-0">
          <Button type="button" variant="outline" disabled={saving} onClick={onClose}>
            Volver
          </Button>
          <Button type="button" variant="gradient" loading={saving} disabled={!changes.length} onClick={onConfirm}>
            Guardar las notas
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}

export function ExamBoardGradesSection({ mesa, rows, role, canWithdraw, onWithdraw, withdrawing }) {
  const saveFinalGrades = useSaveFinalGrades(mesa.id);
  const [drafts, setDrafts] = useState({});
  const [reviewing, setReviewing] = useState(false);
  const [failure, setFailure] = useState(null);
  const [discarding, setDiscarding] = useState(false);
  const [correcting, setCorrecting] = useState(null);
  const [correctionFailure, setCorrectionFailure] = useState(null);

  const gradeRows = useMemo(() => rows.filter((row) => row.status !== 'WITHDRAWN'), [rows]);
  const mode = boardAccess({ role, mesa });
  const changes = useMemo(() => boardChanges(gradeRows, drafts), [gradeRows, drafts]);
  const changeRows = useMemo(() => boardChangeRows(gradeRows, drafts), [gradeRows, drafts]);
  const summary = summarizeBoard(gradeRows, drafts);
  const invalid = hasInvalidBoardDraft(drafts);
  const dirty = Object.keys(drafts).length > 0;
  const rule = approvalRule(gradeRows);
  const lockedReason = `La carga de notas se habilita después del examen, el ${formatearFecha(mesa.examDate)}.`;

  useUnsavedChangesGuard(dirty, UNSAVED_MESSAGE);

  function changeDraft(row, patch) {
    setFailure(null);
    setDrafts((current) => patchBoardDraft(current, row, patch));
  }

  function closeReview() {
    setReviewing(false);
    setFailure(null);
  }

  function confirmSave() {
    const saved = changes;
    saveFinalGrades.mutate(buildFinalGradesPayload(gradeRows, drafts), {
      onSuccess: () => {
        setDrafts({});
        closeReview();
        toast.success(summarizeBoardSave(saved));
      },
      onError: (error) => setFailure(batchFailure(error, gradeRows)),
    });
  }

  function closeCorrection() {
    setCorrecting(null);
    setCorrectionFailure(null);
  }

  function confirmCorrection(form) {
    const target = correcting;
    saveFinalGrades.mutate(buildCorrectionPayload(target, form), {
      onSuccess: () => {
        closeCorrection();
        toast.success(form.absent ? `${target.studentName} quedó Ausente. El cambio quedó en el historial.` : `Se corrigió la nota de ${target.studentName}. El cambio quedó en el historial.`);
      },
      onError: (error) => setCorrectionFailure(batchFailure(error, gradeRows).message),
    });
  }

  function dropFailed() {
    const failedId = failure?.registrationId;
    const remaining = removeBoardDraft(drafts, failedId);
    setDrafts(remaining);
    setFailure(null);
    if (!boardChanges(gradeRows, remaining).length) setReviewing(false);
  }

  if (!gradeRows.length) {
    return <EmptyState title="Todavía no hay alumnos anotados" className="py-8" />;
  }

  const rowErrors = failure?.registrationId != null ? { [failure.registrationId]: failure.message } : {};

  return (
    <div className="flex min-w-0 flex-col gap-3">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-size-12-5 text-ink-soft">
        {rule && <b className="rounded-full border border-border px-2.5 py-0.5 text-ink">{rule}</b>}
        <span>Nota de 1 a 10, admite coma (7,5)</span>
        {mode === 'edit' && <span className="hidden md:inline">Enter baja a la fila · A marca Ausente</span>}
      </div>
      <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-size-12-5 text-ink-soft" aria-live="polite">
        <span><b className="text-ink">{summary.total}</b> anotados</span>
        <span><b className="text-ink">{summary.graded}</b> con nota</span>
        <span><b className="text-ink">{summary.absent}</b> ausentes</span>
        <span><b className="text-ink">{summary.pending}</b> pendientes</span>
      </p>
      {mode === 'locked' && (
        <Notice tone="warning" title={lockedReason}>
          Mientras tanto podés ver quiénes están anotados.
        </Notice>
      )}
      {mode === 'read' && mesa.status === 'SCHEDULED' && role !== 'docente' && (
        <Notice title="Solo lectura.">Las notas las carga el tribunal de la mesa.</Notice>
      )}
      {mesa.status === 'CLOSED' && (
        <Notice title="Acta cerrada.">Las notas quedaron firmes y ya no se pueden corregir. Si hay un error, hablá con Dirección.</Notice>
      )}
      <GradeGridTable
        mode="board"
        students={gradeRows}
        board={{
          drafts,
          rowErrors,
          mode,
          lockedReason,
          onChange: changeDraft,
          canWithdraw,
          onWithdraw,
          withdrawing,
          correctionAccess: (row) => correctionAccess({ role, mesa, row }),
          onCorrect: (row) => setCorrecting(row),
        }}
      />
      {mode === 'edit' && dirty && (
        <StickyActionBar
          summary={`${Object.keys(drafts).length} ${Object.keys(drafts).length === 1 ? 'cambio' : 'cambios'} sin guardar`}
          hint={invalid ? 'Hay notas fuera de 1 a 10' : undefined}
        >
          <Button type="button" variant="outline" onClick={() => setDiscarding(true)}>
            Descartar
          </Button>
          <Button type="button" variant="gradient" disabled={invalid || !changes.length} onClick={() => setReviewing(true)}>
            Revisar y guardar
          </Button>
        </StickyActionBar>
      )}
      <ConfirmActionDialog
        open={discarding}
        onOpenChange={setDiscarding}
        title="Descartar los cambios"
        description={`Se pierden los ${Object.keys(drafts).length} cambios sin guardar.`}
        confirmLabel="Descartar"
        onConfirm={() => {
          setDrafts({});
          setDiscarding(false);
        }}
      />
      <ExamBoardCorrectionSheet
        row={correcting}
        saving={saveFinalGrades.isPending}
        failure={correctionFailure}
        onClose={closeCorrection}
        onConfirm={confirmCorrection}
      />
      <ReviewSheet
        open={reviewing}
        changes={changeRows}
        failure={failure}
        saving={saveFinalGrades.isPending}
        onClose={closeReview}
        onConfirm={confirmSave}
        onDropFailed={dropFailed}
      />
    </div>
  );
}
