import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/context/AuthContext';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { useProgramCohorts } from '@/features/academic-structure';
import { useCreateDiscountDefinition, useDiscountDefinitionValidation } from '../hooks/useDiscountDefinitions';
import { useOfferNames } from '../hooks/useOfferNames';
import { inboxPath, roleBaseOf } from '../authorization/authorizationRequests';
import { ExamplePanel, ValidationPanel } from './DefinitionSidePanels';
import { FormField } from './FormField';
import { OptionSelect } from './OptionSelect';
import { RadioChoice } from './RadioChoice';
import { ViolationList } from './ViolationList';
import {
  ANNUAL_TYPE_HINT,
  NAME_MAX_LENGTH,
  SCHOLARSHIP_TYPE_HINT,
  VALIDATION_DEBOUNCE_MS,
  appliesOptionsFor,
  applyFieldChange,
  buildDefinitionPayload,
  definitionErrorMessage,
  hasCalculation,
  initialFormValues,
  isPromotion,
  supportsInstallments,
  validateDefinitionForm,
} from './discountDefinitionForm';
import { PROMOTION_APPLIES_TO, appliesToLabel, isAwaitingAuthorization } from './discountLabels';

const ALL_COHORTS = 'ALL';
const TYPE_OPTIONS = [
  { value: 'SCHOLARSHIP', label: 'Beca' },
  { value: 'ANNUAL_UPFRONT', label: 'Pago anual' },
];
const CALC_OPTIONS = [
  { value: 'PERCENT', label: 'Porcentaje' },
  { value: 'FIXED', label: 'Monto fijo' },
];
const SCOPE_OPTIONS = [
  { value: 'PROGRAM', label: 'Carrera' },
  { value: 'COURSE', label: 'Curso' },
];

function validationState({ clientValid, settled, query, cross }) {
  if (!clientValid) return { kind: 'idle', messages: cross };
  if (!settled || query.isFetching || (!query.data && !query.isError)) return { kind: 'loading', messages: [] };
  if (query.isError) return { kind: 'error', messages: cross };
  const serverMessages = (query.data?.violations ?? []).map((violation) => violation.message);
  return { kind: 'ready', messages: [...cross, ...serverMessages] };
}

function SectionTitle({ children }) {
  return <h3 className="border-t border-border pt-4 font-heading text-sm font-semibold text-ink">{children}</h3>;
}

export function DiscountDefinitionFormPage({ mode }) {
  const promotionMode = mode === 'promotion';
  const navigate = useNavigate();
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const isDirector = roleBase === 'director';
  const listPath = `/${roleBase}/descuentos${promotionMode ? '?tab=promotions' : ''}`;

  const [values, setValues] = useState(() => initialFormValues(mode));
  const [showErrors, setShowErrors] = useState(false);
  const [serverViolations, setServerViolations] = useState([]);
  const mutation = useCreateDiscountDefinition();
  const names = useOfferNames();
  const cohortsQuery = useProgramCohorts(promotionMode && values.scopeKind === 'PROGRAM' ? values.scopeId : null);

  const { errors, cross } = useMemo(() => validateDefinitionForm(values), [values]);
  const clientValid = Object.keys(errors).length === 0;
  const payload = useMemo(() => (clientValid ? buildDefinitionPayload(values) : null), [clientValid, values]);
  const debouncedPayload = useDebouncedValue(payload, VALIDATION_DEBOUNCE_MS);
  const validationQuery = useDiscountDefinitionValidation(debouncedPayload);
  const panelState = validationState({ clientValid, settled: payload === debouncedPayload, query: validationQuery, cross });

  const promotion = isPromotion(values);
  const appliesOptions = appliesOptionsFor(values.discountType, PROMOTION_APPLIES_TO).map((type) => ({ value: type, label: appliesToLabel(type) }));
  const fieldError = (field) => (showErrors ? errors[field] : undefined);

  const programOptions = names.programs.map((program) => ({ value: String(program.id), label: program.name }));
  const courseOptions = names.courses.map((course) => ({ value: String(course.id), label: course.name }));
  const cohortOptions = [
    { value: ALL_COHORTS, label: 'Todas las cohortes' },
    ...(cohortsQuery.data ?? []).map((cohort) => ({ value: String(cohort.id), label: cohort.label ?? `Cohorte ${cohort.id}` })),
  ];

  const typeHint = values.discountType === 'SCHOLARSHIP' ? SCHOLARSHIP_TYPE_HINT : ANNUAL_TYPE_HINT;
  const noun = promotionMode ? 'promoción' : 'descuento';
  const title = promotionMode ? 'Nueva promoción de cohorte' : 'Nuevo descuento';
  const submitLabel = isDirector ? `Crear ${noun}` : 'Enviar al director';

  function update(field, value) {
    setValues((current) => applyFieldChange(current, field, value));
    setServerViolations([]);
  }

  function bind(field) {
    return { value: values[field], onChange: (event) => update(field, event.target.value) };
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setShowErrors(true);
    setServerViolations([]);
    if (!clientValid) {
      toast.error('Revisá los campos marcados.');
      return;
    }
    try {
      const result = await mutation.mutateAsync(buildDefinitionPayload(values));
      if (isAwaitingAuthorization(result)) {
        toast.success('Enviado al director para aprobación.', {
          action: { label: 'Ver mis pedidos', onClick: () => navigate(inboxPath(roleBase)) },
        });
      } else {
        toast.success(promotionMode ? 'Promoción creada.' : 'Descuento creado.');
      }
      navigate(listPath);
    } catch (error) {
      if (error?.violations?.length) {
        setServerViolations(error.violations);
        toast.error('Revisá las reglas del catálogo.');
        return;
      }
      toast.error(definitionErrorMessage(error));
    }
  }

  return (
    <PageContainer title={title}>
      <Button asChild variant="ghost" size="sm" className="-mt-3 self-start text-ink-soft">
        <Link to={listPath}>
          <ArrowLeft />
          Volver a Descuentos y promociones
        </Link>
      </Button>
      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <GlassCard className="p-5">
          <form id="discount-definition-form" onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField id="definition-name" label="Nombre" error={fieldError('name')} className="sm:col-span-2">
                <Input
                  id="definition-name"
                  maxLength={NAME_MAX_LENGTH}
                  autoComplete="off"
                  aria-invalid={!!fieldError('name')}
                  aria-describedby={fieldError('name') ? 'definition-name-error' : undefined}
                  {...bind('name')}
                />
              </FormField>

              {!promotionMode && (
                <FormField id="definition-type" label="Tipo" hint={typeHint}>
                  <OptionSelect id="definition-type" value={values.discountType} onChange={(value) => update('discountType', value)} options={TYPE_OPTIONS} />
                </FormField>
              )}

              <FormField
                id="definition-applies"
                label="Aplica a"
                hint={
                  appliesOptions.length === 1
                    ? 'Fijo para este tipo.'
                    : 'Una promoción cubre un solo tipo de factura. Para otro cargo, creá otra promoción.'
                }
              >
                <OptionSelect
                  id="definition-applies"
                  value={values.applicableInvoiceType}
                  onChange={(value) => update('applicableInvoiceType', value)}
                  options={appliesOptions}
                  disabled={appliesOptions.length === 1}
                />
              </FormField>

              {hasCalculation(values) ? (
                <>
                  <FormField id="definition-calc" label="Cálculo">
                    <RadioChoice name="definition-calc" ariaLabel="Cálculo" value={values.calcKind} onChange={(value) => update('calcKind', value)} options={CALC_OPTIONS} />
                  </FormField>
                  <FormField id="definition-value" label={values.calcKind === 'PERCENT' ? 'Porcentaje' : 'Monto'} error={fieldError('value')}>
                    <Input
                      id="definition-value"
                      type="number"
                      min="0"
                      step="any"
                      inputMode="decimal"
                      aria-invalid={!!fieldError('value')}
                      aria-describedby={fieldError('value') ? 'definition-value-error' : undefined}
                      {...bind('value')}
                    />
                  </FormField>
                </>
              ) : (
                <FormField id="definition-calc-fixed" label="Cálculo" hint="Las becas no llevan valor propio.">
                  <Input id="definition-calc-fixed" value="Lo fija cada otorgamiento" readOnly className="cursor-default" />
                </FormField>
              )}

              {supportsInstallments(values) && (
                <FormField id="definition-installments" label="Cantidad de cuotas" error={fieldError('installmentsTotal')} hint="Vacío: sin límite de cuotas.">
                  <Input
                    id="definition-installments"
                    type="number"
                    min="1"
                    placeholder="Todas"
                    aria-invalid={!!fieldError('installmentsTotal')}
                    aria-describedby={fieldError('installmentsTotal') ? 'definition-installments-error' : undefined}
                    {...bind('installmentsTotal')}
                  />
                </FormField>
              )}

              <FormField id="definition-from" label="Disponible desde" error={fieldError('availableFrom')}>
                <Input id="definition-from" type="date" aria-invalid={!!fieldError('availableFrom')} {...bind('availableFrom')} />
              </FormField>
              <FormField id="definition-until" label="Disponible hasta" error={fieldError('availableUntil')} hint="Las cuotas sin usar vencen ese día.">
                <Input id="definition-until" type="date" aria-invalid={!!fieldError('availableUntil')} {...bind('availableUntil')} />
              </FormField>
              <FormField
                id="definition-priority"
                label="Prioridad"
                error={fieldError('priority')}
                hint={`Desempata cuando dos ${promotionMode ? 'promociones' : 'descuentos'} ahorran lo mismo.`}
              >
                <Input id="definition-priority" type="number" min="1" aria-invalid={!!fieldError('priority')} {...bind('priority')} />
              </FormField>
              <FormField id="definition-cap" label="Tope por factura (opcional)" error={fieldError('capPerInvoice')}>
                <Input
                  id="definition-cap"
                  type="number"
                  min="0"
                  step="any"
                  placeholder="Sin tope"
                  aria-invalid={!!fieldError('capPerInvoice')}
                  {...bind('capPerInvoice')}
                />
              </FormField>
            </div>

            {promotion && (
              <>
                <SectionTitle>Quién puede obtenerlo</SectionTitle>
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField id="definition-scope-kind" label="Alcance">
                    <RadioChoice name="definition-scope" ariaLabel="Alcance" value={values.scopeKind} onChange={(value) => update('scopeKind', value)} options={SCOPE_OPTIONS} />
                  </FormField>
                  <FormField id="definition-scope" label={values.scopeKind === 'PROGRAM' ? 'Carrera' : 'Curso'} error={fieldError('scopeId')}>
                    <OptionSelect
                      id="definition-scope"
                      value={values.scopeId}
                      onChange={(value) => update('scopeId', value)}
                      options={values.scopeKind === 'PROGRAM' ? programOptions : courseOptions}
                      placeholder={values.scopeKind === 'PROGRAM' ? 'Elegí la carrera' : 'Elegí el curso'}
                      invalid={!!fieldError('scopeId')}
                    />
                  </FormField>
                  {values.scopeKind === 'PROGRAM' && (
                    <FormField id="definition-cohort" label="Cohorte (opcional)" hint="Solo muestra cohortes de la carrera elegida.">
                      <OptionSelect
                        id="definition-cohort"
                        value={values.cohortId || ALL_COHORTS}
                        onChange={(value) => update('cohortId', value === ALL_COHORTS ? '' : value)}
                        options={cohortOptions}
                        disabled={!values.scopeId}
                      />
                    </FormField>
                  )}
                  <FormField id="definition-quota" label="Cupo" error={fieldError('quota')} hint="Cuenta a todos los alumnos que la obtuvieron, aunque ya la hayan usado.">
                    <Input id="definition-quota" type="number" min="1" aria-invalid={!!fieldError('quota')} {...bind('quota')} />
                  </FormField>
                  <FormField id="definition-window-from" label="Inscripción desde" error={fieldError('enrollmentWindowFrom')}>
                    <Input id="definition-window-from" type="date" aria-invalid={!!fieldError('enrollmentWindowFrom')} {...bind('enrollmentWindowFrom')} />
                  </FormField>
                  <FormField id="definition-window-to" label="Inscripción hasta" error={fieldError('enrollmentWindowTo')}>
                    <Input id="definition-window-to" type="date" aria-invalid={!!fieldError('enrollmentWindowTo')} {...bind('enrollmentWindowTo')} />
                  </FormField>
                </div>
              </>
            )}

            <ViolationList violations={serverViolations} title="El catálogo no aceptó los datos" />

            <div className="flex flex-wrap justify-end gap-2">
              <Button asChild variant="outline">
                <Link to={listPath}>Cancelar</Link>
              </Button>
              <Button type="submit" variant="gradient" loading={mutation.isPending}>
                {submitLabel}
              </Button>
            </div>
          </form>
        </GlassCard>

        <aside className="flex flex-col gap-4">
          <ValidationPanel state={panelState} />
          <ExamplePanel values={values} />
          {!isDirector && (
            <p className="rounded-xl bg-neutral-bg px-4 py-3 text-sm text-ink-soft">
              Tu propuesta queda pendiente hasta que el director la apruebe. No se publica antes.
            </p>
          )}
        </aside>
      </div>
    </PageContainer>
  );
}
