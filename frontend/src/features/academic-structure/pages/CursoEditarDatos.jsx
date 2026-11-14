import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { moneyValue } from '@/lib/utils';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { useCourse, useUpdateCourse } from '@/features/academic-structure/hooks/useCourses';
import { FormField, SelectField } from '@/features/academic-structure/components/FormField';
import {
  countErrors,
  describeSaveError,
  fieldProps,
  invalidFieldsSummary,
  validateCourseBasics,
  validateCourseDetail,
} from '@/features/academic-structure/lib/offeringForm';
import { TEACHING_MODE_OPTIONS } from '@/lib/teachingMode';
import { useUnsavedChangesGuard } from '@/features/academic-structure/components/UnsavedChangesGuard';

function isoToDatetimeLocal(iso) {
  if (!iso) return '';
  return iso.slice(0, 16);
}

export function CursoEditarDatos() {
  const { id } = useParams();
  const { data: course, isError, error, refetch } = useCourse(id);

  if (course) return <CursoEditarDatosForm key={course.id} id={id} course={course} />;

  if (isError) {
    return (
      <PageContainer>
        <LoadErrorState
          error={error}
          onRetry={refetch}
          resource="el curso"
          backTo="/administrativo/gestion-academica?section=cursos"
          backLabel="cursos"
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <Skeleton className="h-8 w-64 rounded-xl" />
    </PageContainer>
  );
}

function CursoEditarDatosForm({ id, course }) {
  const navigate = useNavigate();
  const updateCourse = useUpdateCourse(id);

  const [name, setName] = useState(course.name ?? '');
  const [capacity, setCapacity] = useState(course.capacity?.toString() ?? '');
  const [price, setPrice] = useState(course.price ? String(moneyValue(course.price)) : '');
  const [teachingMode, setTeachingMode] = useState(course.teachingMode ?? '');
  const [schedule, setSchedule] = useState(course.schedule ?? '');
  const [durationMonths, setDurationMonths] = useState(course.durationMonths?.toString() ?? '');
  const [registrationStart, setRegistrationStart] = useState(isoToDatetimeLocal(course.registrationStart));
  const [registrationEnd, setRegistrationEnd] = useState(isoToDatetimeLocal(course.registrationEnd));
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);

  const dirty =
    name !== (course.name ?? '') ||
    capacity !== (course.capacity?.toString() ?? '') ||
    price !== (course.price ? String(moneyValue(course.price)) : '') ||
    teachingMode !== (course.teachingMode ?? '') ||
    schedule !== (course.schedule ?? '') ||
    durationMonths !== (course.durationMonths?.toString() ?? '') ||
    registrationStart !== isoToDatetimeLocal(course.registrationStart) ||
    registrationEnd !== isoToDatetimeLocal(course.registrationEnd);
  const { requestNavigation, guardDialog } = useUnsavedChangesGuard(dirty);

  async function handleGuardar() {
    const found = {
      ...validateCourseBasics({ name, capacity, price }),
      ...validateCourseDetail({ durationMonths, registrationStart, registrationEnd }),
    };
    if (course.teachingMode && !teachingMode) {
      found.teachingMode = 'No se puede quitar la modalidad una vez definida: elegí otra.';
    }
    setErrors(found);
    setSubmitError(null);
    if (countErrors(found) > 0) return;
    try {
      await updateCourse.mutateAsync({
        name: name.trim(),
        capacity: parseInt(capacity),
        ...(price && { price: { amount: String(parseFloat(price)), currency: course.price?.currency ?? 'ARS' } }),
        ...(teachingMode && { teachingMode }),
        schedule: schedule.trim() || null,
        durationMonths: parseInt(durationMonths),
        registrationStart: registrationStart || null,
        registrationEnd: registrationEnd || null,
      });
      toast.success('Datos actualizados');
      navigate(`/administrativo/gestion-academica/cursos/${id}`);
    } catch (err) {
      const failure = describeSaveError(err, 'No se pudo guardar los cambios. Intentá de nuevo.');
      if (failure.field === 'registrationEnd') setErrors({ registrationEnd: failure.message });
      else setSubmitError(failure.message);
    }
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div>
          <Link
            to={`/administrativo/gestion-academica/cursos/${id}`}
            className="mb-1 flex items-center gap-1 text-size-12 text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            {course.name ?? 'Curso'}
          </Link>
          <h1 className="font-heading text-xl font-bold text-ink">Editar datos</h1>
        </div>

        <GlassCard className="flex flex-col gap-5 p-6">
          <FormField id="course-name" label="Nombre" required error={errors.name}>
            <Input
              {...fieldProps('course-name', errors.name, true)}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </FormField>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormField id="course-capacity" label="Cupo" required error={errors.capacity}>
              <Input
                {...fieldProps('course-capacity', errors.capacity, true)}
                type="number"
                min={1}
                value={capacity}
                onChange={(e) => setCapacity(e.target.value)}
              />
            </FormField>
            <FormField id="course-price" label="Cuota mensual" required error={errors.price}>
              <Input
                {...fieldProps('course-price', errors.price, true)}
                type="number"
                min={0}
                step={0.01}
                value={price}
                onChange={(e) => setPrice(e.target.value)}
              />
            </FormField>
          </div>

          <SelectField
            id="course-teaching-mode"
            label="Modalidad"
            error={errors.teachingMode}
            value={teachingMode}
            onChange={setTeachingMode}
            options={TEACHING_MODE_OPTIONS}
          />

          <FormField id="course-schedule" label="Horario">
            <Input id="course-schedule" value={schedule} onChange={(e) => setSchedule(e.target.value)} />
          </FormField>

          <FormField id="course-duration" label="Duración (meses)" required error={errors.durationMonths}>
            <Input
              {...fieldProps('course-duration', errors.durationMonths, true)}
              type="number"
              min={1}
              value={durationMonths}
              onChange={(e) => setDurationMonths(e.target.value)}
            />
          </FormField>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormField id="course-registration-start" label="Inicio de inscripción">
              <Input
                id="course-registration-start"
                type="datetime-local"
                value={registrationStart}
                onChange={(e) => setRegistrationStart(e.target.value)}
              />
            </FormField>
            <FormField id="course-registration-end" label="Cierre de inscripción" error={errors.registrationEnd}>
              <Input
                {...fieldProps('course-registration-end', errors.registrationEnd)}
                type="datetime-local"
                value={registrationEnd}
                onChange={(e) => setRegistrationEnd(e.target.value)}
              />
            </FormField>
          </div>

          {countErrors(errors) > 0 && (
            <p role="alert" className="text-size-13 text-danger">
              {invalidFieldsSummary(errors)}
            </p>
          )}
          {submitError && (
            <p role="alert" className="text-size-13 text-danger">
              {submitError}
            </p>
          )}

          <div className="flex items-center justify-between">
            <Button
              variant="outline"
              onClick={() => requestNavigation(`/administrativo/gestion-academica/cursos/${id}`)}
            >
              Cancelar
            </Button>
            <Button variant="gradient" loading={updateCourse.isPending} onClick={handleGuardar}>
              Guardar cambios
            </Button>
          </div>
        </GlassCard>
        {guardDialog}
      </div>
    </PageContainer>
  );
}
