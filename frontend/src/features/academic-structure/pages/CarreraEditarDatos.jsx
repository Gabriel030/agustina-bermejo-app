import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { useProgram, useUpdateProgram } from '@/features/academic-structure/hooks/usePrograms';
import { FormField, SelectField } from '@/features/academic-structure/components/FormField';
import {
  countErrors,
  describeSaveError,
  fieldProps,
  invalidFieldsSummary,
  validateProgramFields,
} from '@/features/academic-structure/lib/offeringForm';
import { TEACHING_MODE_OPTIONS } from '@/lib/teachingMode';
import { useUnsavedChangesGuard } from '@/features/academic-structure/components/UnsavedChangesGuard';

const LEVEL_OPTIONS = [
  { value: '', label: 'Sin especificar' },
  { value: 'UNDERGRADUATE', label: 'Pregrado' },
  { value: 'GRADUATE', label: 'Posgrado' },
  { value: 'EXTENSION', label: 'Extensión' },
  { value: 'DIPLOMA', label: 'Diplomatura' },
];

export function CarreraEditarDatos() {
  const { id } = useParams();
  const { data: program, isError, error, refetch } = useProgram(id);

  if (program) return <CarreraEditarDatosForm key={program.id} id={id} program={program} />;

  if (isError) {
    return (
      <PageContainer>
        <LoadErrorState
          error={error}
          onRetry={refetch}
          resource="la carrera"
          backTo="/administrativo/gestion-academica?section=carreras"
          backLabel="carreras"
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

function CarreraEditarDatosForm({ id, program }) {
  const navigate = useNavigate();
  const updateProgram = useUpdateProgram(id);

  const [name, setName] = useState(program.name ?? '');
  const [totalYears, setTotalYears] = useState(program.totalYears?.toString() ?? '');
  const [level, setLevel] = useState(program.level ?? '');
  const [teachingMode, setTeachingMode] = useState(program.teachingMode ?? '');
  const [totalCreditsRequired, setTotalCreditsRequired] = useState(program.totalCreditsRequired?.toString() ?? '');
  const [totalHoursRequired, setTotalHoursRequired] = useState(program.totalHoursRequired?.toString() ?? '');
  const [graduationProfile, setGraduationProfile] = useState(program.graduationProfile ?? '');
  const [admissionRequirements, setAdmissionRequirements] = useState(program.admissionRequirements ?? '');
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);

  const dirty =
    name !== (program.name ?? '') ||
    totalYears !== (program.totalYears?.toString() ?? '') ||
    level !== (program.level ?? '') ||
    teachingMode !== (program.teachingMode ?? '') ||
    totalCreditsRequired !== (program.totalCreditsRequired?.toString() ?? '') ||
    totalHoursRequired !== (program.totalHoursRequired?.toString() ?? '') ||
    graduationProfile !== (program.graduationProfile ?? '') ||
    admissionRequirements !== (program.admissionRequirements ?? '');
  const { requestNavigation, guardDialog } = useUnsavedChangesGuard(dirty);

  async function handleGuardar() {
    const found = validateProgramFields(
      { name, totalYears, totalCreditsRequired, totalHoursRequired },
      { requireCredits: false },
    );
    setErrors(found);
    setSubmitError(null);
    if (countErrors(found) > 0) return;
    try {
      await updateProgram.mutateAsync({
        name: name.trim(),
        totalYears: parseInt(totalYears),
        level: level || null,
        teachingMode: teachingMode || null,
        ...(totalCreditsRequired && { totalCreditsRequired: parseInt(totalCreditsRequired) }),
        totalHoursRequired: totalHoursRequired ? parseInt(totalHoursRequired) : null,
        graduationProfile: graduationProfile.trim() || null,
        admissionRequirements: admissionRequirements.trim() || null,
      });
      toast.success('Datos actualizados');
      navigate(`/administrativo/gestion-academica/carreras/${id}`);
    } catch (err) {
      setSubmitError(describeSaveError(err, 'No se pudieron guardar los cambios. Intentá de nuevo.').message);
    }
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <div>
          <Link
            to={`/administrativo/gestion-academica/carreras/${id}`}
            className="mb-1 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            {program.name ?? 'Carrera'}
          </Link>
          <h1 className="font-heading text-xl font-bold text-ink">Editar datos</h1>
        </div>

        <GlassCard className="flex flex-col gap-5 p-6">
          <FormField id="program-name" label="Nombre" required error={errors.name}>
            <Input
              {...fieldProps('program-name', errors.name, true)}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </FormField>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormField id="program-years" label="Duración (años)" required error={errors.totalYears}>
              <Input
                {...fieldProps('program-years', errors.totalYears, true)}
                type="number"
                min={1}
                max={10}
                value={totalYears}
                onChange={(e) => setTotalYears(e.target.value)}
              />
            </FormField>
            <FormField id="program-credits" label="Créditos requeridos" error={errors.totalCreditsRequired}>
              <Input
                {...fieldProps('program-credits', errors.totalCreditsRequired)}
                type="number"
                min={0}
                value={totalCreditsRequired}
                onChange={(e) => setTotalCreditsRequired(e.target.value)}
              />
            </FormField>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <SelectField id="program-level" label="Nivel" value={level} onChange={setLevel} options={LEVEL_OPTIONS} />
            <SelectField
              id="program-teaching-mode"
              label="Modalidad"
              value={teachingMode}
              onChange={setTeachingMode}
              options={TEACHING_MODE_OPTIONS}
            />
          </div>

          <FormField id="program-hours" label="Horas requeridas" error={errors.totalHoursRequired}>
            <Input
              {...fieldProps('program-hours', errors.totalHoursRequired)}
              type="number"
              min={0}
              value={totalHoursRequired}
              onChange={(e) => setTotalHoursRequired(e.target.value)}
            />
          </FormField>

          <FormField id="program-profile" label="Perfil del egresado">
            <Textarea
              id="program-profile"
              rows={4}
              value={graduationProfile}
              onChange={(e) => setGraduationProfile(e.target.value)}
            />
          </FormField>

          <FormField id="program-admission" label="Requisitos de admisión">
            <Textarea
              id="program-admission"
              rows={3}
              value={admissionRequirements}
              onChange={(e) => setAdmissionRequirements(e.target.value)}
            />
          </FormField>

          {countErrors(errors) > 0 && (
            <p role="alert" className="text-[13px] text-danger">
              {invalidFieldsSummary(errors)}
            </p>
          )}
          {submitError && (
            <p role="alert" className="text-[13px] text-danger">
              {submitError}
            </p>
          )}

          <div className="flex items-center justify-between">
            <Button
              variant="outline"
              onClick={() => requestNavigation(`/administrativo/gestion-academica/carreras/${id}`)}
            >
              Cancelar
            </Button>
            <Button variant="gradient" loading={updateProgram.isPending} onClick={handleGuardar}>
              Guardar cambios
            </Button>
          </div>
        </GlassCard>
        {guardDialog}
      </div>
    </PageContainer>
  );
}
