import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { MonthPicker } from '@/components/MonthPicker';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/context/AuthContext';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { useDiscountDefinitions } from '../hooks/useDiscountDefinitions';
import { parseEnrollmentKey, useEnrollmentDetail, useGrantPreview, useGrantStudentDiscount } from '../hooks/useStudentDiscounts';
import { useStudentSummary } from '../hooks/useStudentSummary';
import { inboxPath, roleBaseOf } from '../authorization/authorizationRequests';
import { FormField } from './FormField';
import { GrantEffectPanel } from './GrantEffectPanel';
import { OptionSelect } from './OptionSelect';
import { RadioChoice } from './RadioChoice';
import { ViolationList } from './ViolationList';
import { INDIVIDUAL_APPLIES_TO, appliesToLabel, isAwaitingAuthorization } from './discountLabels';
import {
  PREVIEW_DEBOUNCE_MS,
  REASON_MAX_LENGTH,
  applyGrantChange,
  buildGrantPayload,
  currentMonth,
  grantErrorMessage,
  initialGrantValues,
  isMonthly,
  previewReady,
  validateGrantValues,
} from './grantForm';

const TYPE_OPTIONS = [
  { value: 'INDIVIDUAL', label: 'Descuento individual' },
  { value: 'SCHOLARSHIP', label: 'Beca' },
];
const CALC_OPTIONS = [
  { value: 'FIXED', label: 'Monto fijo' },
  { value: 'PERCENT', label: 'Porcentaje' },
];

function PageError({ backTo, message }) {
  return (
    <PageContainer title="Descuento">
      <GlassCard className="flex flex-col items-start gap-3 p-5">
        <p role="alert" className="text-sm text-danger">{message}</p>
        <Button asChild variant="outline">
          <Link to={backTo}>Volver al legajo</Link>
        </Button>
      </GlassCard>
    </PageContainer>
  );
}

export function GrantDiscountPage() {
  const { id: studentId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const isDirector = roleBase === 'director';
  const studentPath = `/${roleBase}/alumnos/${studentId}?view=financiero`;
  const enrollment = useMemo(() => parseEnrollmentKey(searchParams.get('enrollment')), [searchParams]);

  const student = useStudentSummary(studentId);
  const enrollmentQuery = useEnrollmentDetail(enrollment);
  const definitionsQuery = useDiscountDefinitions();
  const mutation = useGrantStudentDiscount(enrollment);

  const [values, setValues] = useState(() => initialGrantValues());
  const [showErrors, setShowErrors] = useState(false);
  const [serverViolations, setServerViolations] = useState([]);

  const errors = useMemo(() => validateGrantValues(values), [values]);
  const ready = useMemo(() => previewReady(values), [values]);
  const payload = useMemo(() => (ready ? buildGrantPayload(values) : null), [ready, values]);
  const debouncedPayload = useDebouncedValue(payload, PREVIEW_DEBOUNCE_MS);
  const previewQuery = useGrantPreview(enrollment, debouncedPayload);
  const settled = payload === debouncedPayload;
  const preview = ready && settled ? previewQuery.data : undefined;
  const previewViolations = preview && !preview.allowed ? preview.violations : [];

  if (!enrollment) {
    return <PageError backTo={studentPath} message="Elegí una inscripción desde el legajo del alumno." />;
  }

  const enrollmentName = enrollmentQuery.data
    ? `${enrollmentQuery.data.programName ?? enrollmentQuery.data.courseName} · ${enrollment.kind === 'program' ? 'carrera' : 'curso'}`
    : '';
  const studentName = student.data ? `${student.data.apellido}, ${student.data.nombre}` : undefined;
  const scholarships = (definitionsQuery.data ?? []).filter((entry) => entry.discountType === 'SCHOLARSHIP' && entry.status === 'ACTIVE');
  const isScholarship = values.type === 'SCHOLARSHIP';
  const monthly = isMonthly(values);
  const annualBlocksScholarship = !!preview?.annualUpfrontCoversRange;
  const minMonth = preview?.nextInstallmentPeriod ?? undefined;
  const appliesOptions = INDIVIDUAL_APPLIES_TO.filter((type) => enrollment.kind === 'program' || type !== 'ENROLLMENT_FEE').map((type) => ({
    value: type,
    label: appliesToLabel(type),
  }));
  const fieldError = (field) => (showErrors ? errors[field] : undefined);
  const noun = isDirector ? 'Otorgar descuento' : 'Proponer descuento';

  function update(field, value) {
    setValues((current) => applyGrantChange(current, field, value));
    setServerViolations([]);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setShowErrors(true);
    setServerViolations([]);
    if (Object.keys(errors).length > 0) {
      toast.error('Revisá los campos marcados.');
      return;
    }
    try {
      const result = await mutation.mutateAsync(buildGrantPayload(values));
      if (isAwaitingAuthorization(result)) {
        toast.success('Enviado al director para aprobación.', {
          action: { label: 'Ver mis pedidos', onClick: () => navigate(inboxPath(roleBase)) },
        });
      } else {
        toast.success('Descuento otorgado.');
      }
      navigate(studentPath);
    } catch (error) {
      if (error?.violations?.length) {
        setServerViolations(error.violations);
        toast.error('Revisá los problemas marcados.');
        return;
      }
      toast.error(grantErrorMessage(error));
    }
  }

  return (
    <PageContainer title={noun} description={[studentName, enrollmentName].filter(Boolean).join(' · ') || undefined}>
      <Button asChild variant="ghost" size="sm" className="-mt-3 self-start text-ink-soft">
        <Link to={studentPath}>
          <ArrowLeft />
          Volver al legajo
        </Link>
      </Button>
      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <GlassCard className="p-5">
          <form id="grant-discount-form" onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
            <FormField id="grant-enrollment" label="Inscripción">
              {enrollmentQuery.isLoading ? (
                <Skeleton className="h-10 w-full rounded-lg" />
              ) : (
                <Input id="grant-enrollment" value={enrollmentName} readOnly className="cursor-default" />
              )}
            </FormField>

            <FormField
              id="grant-type"
              label="Tipo"
              hint={
                annualBlocksScholarship
                  ? 'Con el pago anual confirmado no se puede otorgar una beca este año lectivo.'
                  : isScholarship
                    ? 'Solo cuotas mensuales, por un rango de meses.'
                    : 'Para cualquier factura salvo recargos.'
              }
            >
              <RadioChoice
                name="grant-type"
                ariaLabel="Tipo"
                value={values.type}
                onChange={(value) => update('type', value)}
                options={TYPE_OPTIONS.map((option) => ({ ...option, disabled: option.value === 'SCHOLARSHIP' && annualBlocksScholarship }))}
              />
            </FormField>

            <div className="grid gap-4 sm:grid-cols-2">
              {isScholarship && (
                <FormField id="grant-definition" label="Beca del catálogo" error={fieldError('definitionId')}>
                  <OptionSelect
                    id="grant-definition"
                    value={values.definitionId}
                    onChange={(value) => update('definitionId', value)}
                    options={scholarships.map((entry) => ({ value: String(entry.id), label: entry.name }))}
                    placeholder="Elegí la beca"
                    invalid={!!fieldError('definitionId')}
                  />
                </FormField>
              )}
              <FormField id="grant-applies" label="Aplica a">
                <OptionSelect
                  id="grant-applies"
                  value={values.invoiceType}
                  onChange={(value) => update('invoiceType', value)}
                  options={appliesOptions}
                  disabled={isScholarship}
                />
              </FormField>
              <FormField id="grant-calc" label="Cálculo">
                <RadioChoice name="grant-calc" ariaLabel="Cálculo" value={values.calcKind} onChange={(value) => update('calcKind', value)} options={CALC_OPTIONS} />
              </FormField>
              <FormField id="grant-value" label={values.calcKind === 'PERCENT' ? 'Porcentaje' : 'Monto'} error={fieldError('value')}>
                <Input
                  id="grant-value"
                  type="number"
                  min="0"
                  step="any"
                  inputMode="decimal"
                  value={values.value}
                  onChange={(event) => update('value', event.target.value)}
                  aria-invalid={!!fieldError('value')}
                  aria-describedby={fieldError('value') ? 'grant-value-error' : undefined}
                />
              </FormField>
              <FormField
                id="grant-from"
                label={monthly ? 'Desde la cuota' : 'Período'}
                error={fieldError('fromPeriod')}
                hint={monthly ? 'Empieza en la próxima cuota a emitir: no hay meses anteriores.' : 'Mes de la factura a la que se aplica.'}
              >
                <MonthPicker value={values.fromPeriod} onChange={(month) => update('fromPeriod', month)} minMonth={monthly ? minMonth : undefined} currentMonth={currentMonth()} />
              </FormField>
              {monthly && (
                <FormField id="grant-to" label="Hasta la cuota" error={fieldError('toPeriod')}>
                  <MonthPicker value={values.toPeriod} onChange={(month) => update('toPeriod', month)} minMonth={values.fromPeriod} currentMonth={currentMonth()} />
                </FormField>
              )}
              <FormField id="grant-reason" label="Motivo" error={fieldError('reason')} className="sm:col-span-2">
                <Textarea
                  id="grant-reason"
                  rows={3}
                  maxLength={REASON_MAX_LENGTH}
                  placeholder="Por qué se otorga"
                  value={values.reason}
                  onChange={(event) => update('reason', event.target.value)}
                  aria-invalid={!!fieldError('reason')}
                  aria-describedby={fieldError('reason') ? 'grant-reason-error' : undefined}
                />
              </FormField>
            </div>

            <ViolationList violations={serverViolations.length ? serverViolations : previewViolations} title="No se puede otorgar así" />

            <div className="flex flex-wrap justify-end gap-2">
              <Button asChild variant="outline">
                <Link to={studentPath}>Cancelar</Link>
              </Button>
              <Button type="submit" variant="gradient" loading={mutation.isPending}>
                {isDirector ? 'Otorgar descuento' : 'Enviar al director'}
              </Button>
            </div>
          </form>
        </GlassCard>

        <aside className="flex flex-col gap-4">
          <GrantEffectPanel preview={preview} values={values} isLoading={previewQuery.isFetching || !settled} isReady={ready} />
          {!isDirector && (
            <p className="rounded-xl bg-neutral-bg px-4 py-3 text-sm text-ink-soft">
              Queda pendiente hasta que el director lo apruebe. Si nadie decide antes del primer día del período de inicio, la propuesta vence.
            </p>
          )}
        </aside>
      </div>
    </PageContainer>
  );
}
