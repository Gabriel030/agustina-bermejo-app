import { useState } from 'react';
import { ArrowLeft, CircleCheck, Download } from 'lucide-react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { describeError } from '@/api/describeError';
import { useMyModules } from '@/hooks/useMyModules';
import { saveBlob } from '../../certificates/lib/saveBlob';
import { useGenerateProcedureDocument, useProcedureEligibility } from '../hooks/useStudentProcedures';
import { buildDocumentBody, generateErrorMessage, isInformativeReason, reasonActionOf } from '../lib/procedures';
import { ExamPicker } from './ExamPicker';
import { RecipientField } from './RecipientField';

function BlockingReasons({ reasons, carreraId }) {
  const { billingEnabled } = useMyModules();
  return (
    <div role="status" className="flex flex-col gap-1.5 rounded-xl bg-warning-bg px-3 py-2.5 text-sm text-warning">
      {reasons.map((reason) => {
        const action = reasonActionOf(reason, { carreraId, billingEnabled });
        return (
          <span key={reason.code} className={isInformativeReason(reason) ? '' : 'font-semibold'}>
            {reason.message}{' '}
            {action?.to && (
              <Link to={action.to} className="font-semibold underline">
                {action.label}
              </Link>
            )}
          </span>
        );
      })}
    </div>
  );
}

export function ProcedureDetail({ procedure, carreraId, onBack }) {
  const [recipientType, setRecipientType] = useState(null);
  const [recipientText, setRecipientText] = useState('');
  const [registrationId, setRegistrationId] = useState(null);
  const [result, setResult] = useState(null);
  const generate = useGenerateProcedureDocument();
  const eligibility = useProcedureEligibility(procedure.type, {
    enrollmentId: carreraId,
    registrationId: procedure.hasExamPicker ? registrationId : null,
  });
  const needsExam = procedure.hasExamPicker && !registrationId;
  const blocked = eligibility.data && !eligibility.data.eligible && !needsExam;
  const canGenerate = !needsExam && !blocked && !generate.isPending;

  async function submit() {
    try {
      const { file } = await generate.mutateAsync({
        type: procedure.type,
        body: buildDocumentBody({ carreraId, recipientType, recipientText, registrationId }),
      });
      const filename = procedure.filename;
      saveBlob(file.blob, filename);
      setResult({ blob: file.blob, filename });
    } catch (error) {
      toast.error(generateErrorMessage(error) ?? describeError(error).message);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <Button variant="subtle" className="self-start" onClick={onBack}>
        <ArrowLeft aria-hidden="true" />
        Trámites
      </Button>
      <GlassCard className="flex flex-col gap-4 p-4">
        <div className="flex flex-col gap-1">
          <h2 className="font-heading text-base font-semibold text-ink">{procedure.title}</h2>
          <p className="text-sm text-ink-soft">{procedure.description}</p>
        </div>
        {result ? (
          <div className="flex flex-col gap-3" aria-live="polite">
            <div role="status" className="flex items-start gap-2.5 rounded-xl bg-success-bg px-3 py-2.5 text-sm text-success">
              <CircleCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              <span>
                <b>Tu constancia está lista.</b> Se descargó. La encontrás en Trámites aprobados.
              </span>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button variant="primary" className="w-full sm:w-auto" onClick={() => saveBlob(result.blob, result.filename)}>
                <Download aria-hidden="true" />
                Volver a descargar
              </Button>
              <Button variant="outline" className="w-full sm:w-auto" onClick={onBack}>
                Volver a Trámites
              </Button>
            </div>
          </div>
        ) : (
          <>
            <div>
              <div className="mb-1.5 text-size-13 font-semibold text-ink-soft">La constancia va a incluir</div>
              <ul className="flex list-disc flex-col gap-1 pl-5 text-sm text-ink-soft">
                {procedure.includes.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
            {procedure.hasExamPicker && (
              <ExamPicker carreraId={carreraId} value={registrationId} onChange={setRegistrationId} disabled={generate.isPending} />
            )}
            {procedure.hasRecipient && (
              <RecipientField
                recipientType={recipientType}
                recipientText={recipientText}
                onTypeChange={setRecipientType}
                onTextChange={setRecipientText}
                disabled={generate.isPending}
              />
            )}
            {blocked && <BlockingReasons reasons={eligibility.data.reasons} carreraId={carreraId} />}
            <p className="text-size-12 text-ink-faint">{procedure.validity} Queda registrada.</p>
            <div className="flex flex-col gap-2 sm:flex-row">
              <Button variant="primary" className="w-full sm:w-auto" loading={generate.isPending} disabled={!canGenerate && !generate.isPending} onClick={submit}>
                {!generate.isPending && <Download aria-hidden="true" />}
                {generate.isPending ? 'Generando…' : 'Generar y descargar'}
              </Button>
              <Button variant="outline" className="w-full sm:w-auto" disabled={generate.isPending} onClick={onBack}>
                Cancelar
              </Button>
            </div>
          </>
        )}
      </GlassCard>
    </div>
  );
}
