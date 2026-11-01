import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { TermDiffTable } from '@/features/academic-structure/components/TermDiffTable';
import { useAllTerms, useCreateTerm, useUpdateTerm } from '@/features/academic-structure/hooks/useTerms';
import {
  TERM_TYPE_OPTIONS,
  buildTermDiff,
  buildTermPayload,
  emptyTermForm,
  termErrorMessage,
  termToForm,
  termTypeLabel,
  validateTermForm,
} from '@/features/academic-structure/lib/terms';

const BACK_PATH = '/preceptor/periodos';

function Field({ id, label, error, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id} className="text-xs font-semibold uppercase tracking-wide text-ink-faint">
        {label}
      </Label>
      {children}
      {error && (
        <p role="alert" className="text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

function BackLink() {
  return (
    <Link to={BACK_PATH} className="-mt-4 flex items-center gap-1 text-xs text-ink-faint hover:text-ink">
      <ChevronLeft className="h-3.5 w-3.5" />
      Volver a Períodos
    </Link>
  );
}

function SubmitError({ message }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger">
      {message}
    </p>
  );
}

function TermForm({ initial, term }) {
  const navigate = useNavigate();
  const editing = Boolean(term);
  const termId = term?.id;
  const createTerm = useCreateTerm();
  const updateTerm = useUpdateTerm();
  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [reviewing, setReviewing] = useState(false);
  const diffRows = editing ? buildTermDiff(termToForm(term), form) : [];
  const isPending = createTerm.isPending || updateTerm.isPending;

  function setField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
    setSubmitError(null);
  }

  function handleSubmit(event) {
    event.preventDefault();
    const found = validateTermForm(form, { editing });
    setErrors(found);
    if (Object.keys(found).length) return;
    if (editing) {
      setReviewing(true);
      return;
    }
    createTerm.mutate(buildTermPayload(form, { editing }), {
      onSuccess: () => {
        toast.success('Período creado. Queda activo.');
        navigate(BACK_PATH);
      },
      onError: (error) => setSubmitError(termErrorMessage(error, 'No se pudo crear el período. Probá de nuevo.')),
    });
  }

  function handleConfirm() {
    updateTerm.mutate(
      { termId, ...buildTermPayload(form, { editing }) },
      {
        onSuccess: () => {
          toast.success('Período actualizado.');
          navigate(BACK_PATH);
        },
        onError: (error) => setSubmitError(termErrorMessage(error, 'No se pudo actualizar el período. Probá de nuevo.')),
      },
    );
  }

  if (reviewing) {
    return (
      <GlassCard className="flex flex-col gap-4 p-6">
        <h2 className="font-heading text-[15px] font-semibold text-ink">Confirmá los cambios</h2>
        {diffRows.length ? (
          <TermDiffTable rows={diffRows} />
        ) : (
          <EmptyState title="No cambiaste nada" description="Volvé y modificá algún dato." />
        )}
        <SubmitError message={submitError} />
        <div className="flex flex-wrap justify-between gap-2">
          <Button type="button" variant="outline" onClick={() => { setReviewing(false); setSubmitError(null); }}>
            Volver
          </Button>
          <Button type="button" variant="gradient" disabled={!diffRows.length || isPending} onClick={handleConfirm}>
            Confirmar cambios
          </Button>
        </div>
      </GlassCard>
    );
  }

  return (
    <GlassCard className="p-6">
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="term-year" label="Año" error={errors.year}>
            <Input
              id="term-year"
              type="number"
              value={form.year}
              readOnly={editing}
              onChange={(event) => setField('year', event.target.value)}
              aria-invalid={Boolean(errors.year) || undefined}
            />
          </Field>
          <Field id="term-type" label="Tipo" error={errors.type}>
            {editing ? (
              <Input id="term-type" readOnly value={termTypeLabel(form.type)} />
            ) : (
              <Select value={form.type} onValueChange={(value) => setField('type', value)}>
                <SelectTrigger id="term-type" aria-invalid={Boolean(errors.type) || undefined} className="h-8 w-full">
                  <SelectValue placeholder="Elegí un tipo" />
                </SelectTrigger>
                <SelectContent>
                  {TERM_TYPE_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </Field>
        </div>
        <Field id="term-description" label="Descripción" error={errors.description}>
          <Input
            id="term-description"
            value={form.description}
            placeholder="Ej.: Primer cuatrimestre"
            onChange={(event) => setField('description', event.target.value)}
            aria-invalid={Boolean(errors.description) || undefined}
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="term-start" label="Inicio de cursada" error={errors.startDate}>
            <Input id="term-start" type="date" value={form.startDate} onChange={(event) => setField('startDate', event.target.value)} />
          </Field>
          <Field id="term-end" label="Fin de cursada" error={errors.endDate}>
            <Input id="term-end" type="date" value={form.endDate} onChange={(event) => setField('endDate', event.target.value)} />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="term-enrollment-start" label="Inicio de inscripción" error={errors.enrollmentStart}>
            <Input id="term-enrollment-start" type="date" value={form.enrollmentStart} onChange={(event) => setField('enrollmentStart', event.target.value)} />
          </Field>
          <Field id="term-enrollment-end" label="Fin de inscripción" error={errors.enrollmentEnd}>
            <Input id="term-enrollment-end" type="date" value={form.enrollmentEnd} onChange={(event) => setField('enrollmentEnd', event.target.value)} />
          </Field>
        </div>
        <SubmitError message={submitError} />
        <div className="flex flex-wrap justify-between gap-2">
          <Button type="button" variant="outline" onClick={() => navigate(BACK_PATH)}>
            Cancelar
          </Button>
          <Button type="submit" variant="gradient" disabled={isPending}>
            {editing ? 'Revisar cambios' : 'Crear período'}
          </Button>
        </div>
      </form>
    </GlassCard>
  );
}

export function PreceptorPeriodoForm() {
  const { termId } = useParams();
  const editing = termId != null;
  const { data: terms, isLoading, isError } = useAllTerms();
  const term = editing ? (terms ?? []).find((candidate) => String(candidate.id) === termId) : null;

  let body;
  if (editing && isLoading) body = <Skeleton className="h-64 w-full rounded-2xl" />;
  else if (editing && (isError || !term)) body = <EmptyState title="No encontramos este período" />;
  else if (editing) body = <TermForm key={term.id} initial={termToForm(term)} term={term} />;
  else body = <TermForm initial={emptyTermForm(new Date().getFullYear())} term={null} />;

  return (
    <PageContainer
      title={editing ? 'Editar período' : 'Nuevo período'}
      description={
        editing
          ? term && `${term.description} ${term.year}`
          : 'Definí el calendario de cursada y, la ventana de inscripción'
      }
    >
      <BackLink />
      {body}
    </PageContainer>
  );
}
