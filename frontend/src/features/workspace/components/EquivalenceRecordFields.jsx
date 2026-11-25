import { Input } from '@/components/ui/input';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { Textarea } from '@/components/ui/textarea';
import { EquivalenceDocumentPicker } from '@/features/workspace/components/EquivalenceDocumentPicker';
import { EquivalenceFormField } from '@/features/workspace/components/EquivalenceFormField';
import { REASON_MAX_LENGTH, REASON_MIN_LENGTH, todayIso } from '@/features/workspace/lib/equivalenceForm';

const CONTROL = 'h-11';

export function EquivalenceRecordFields({ register, errors, eligibleSubjects, studentId, documentValue, onDocumentChange, reasonLength }) {
  const message = (name) => errors[name]?.message;
  return (
    <div className="flex flex-col gap-4">
      <EquivalenceFormField name="programSubjectId" label="Materia del plan" error={message('programSubjectId')}>
        {(control) => (
          <NativeSelect className="[&_select]:h-11" {...control} {...register('programSubjectId')}>
            <NativeSelectOption value="">Elegí una materia</NativeSelectOption>
            {eligibleSubjects.map((subject) => (
              <NativeSelectOption key={subject.programSubjectId} value={String(subject.programSubjectId)}>
                {subject.subjectName} ({subject.subjectCode})
              </NativeSelectOption>
            ))}
          </NativeSelect>
        )}
      </EquivalenceFormField>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <EquivalenceFormField name="originInstitution" label="Institución de origen" error={message('originInstitution')}>
          {(control) => <Input className={CONTROL} autoComplete="off" {...control} {...register('originInstitution')} />}
        </EquivalenceFormField>
        <EquivalenceFormField name="originSubjectName" label="Materia de origen" error={message('originSubjectName')}>
          {(control) => <Input className={CONTROL} autoComplete="off" {...control} {...register('originSubjectName')} />}
        </EquivalenceFormField>
        <EquivalenceFormField name="originScore" label="Nota de origen" optional hint="De 1 a 10" error={message('originScore')}>
          {(control) => <Input className={CONTROL} inputMode="decimal" autoComplete="off" {...control} {...register('originScore')} />}
        </EquivalenceFormField>
        <EquivalenceFormField name="recognizedOn" label="Fecha de reconocimiento" error={message('recognizedOn')}>
          {(control) => <Input className={CONTROL} type="date" max={todayIso()} {...control} {...register('recognizedOn')} />}
        </EquivalenceFormField>
      </div>
      <EquivalenceFormField name="resolutionNumber" label="Resolución" error={message('resolutionNumber')}>
        {(control) => <Input className={CONTROL} autoComplete="off" {...control} {...register('resolutionNumber')} />}
      </EquivalenceFormField>
      <EquivalenceFormField name="studentDocumentId" label="Documento del legajo" optional error={message('studentDocumentId')}>
        {(control) => <EquivalenceDocumentPicker studentId={studentId} value={documentValue} onChange={onDocumentChange} invalid={Boolean(control['aria-invalid'])} describedBy={control['aria-describedby']} id={control.id} />}
      </EquivalenceFormField>
      <EquivalenceFormField
        name="reason"
        label="Motivo"
        hint={`${reasonLength} de ${REASON_MAX_LENGTH} caracteres · mínimo ${REASON_MIN_LENGTH}`}
        error={message('reason')}
      >
        {(control) => <Textarea rows={3} {...control} {...register('reason')} />}
      </EquivalenceFormField>
    </div>
  );
}
