import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Skeleton } from '@/components/Skeleton';
import { useProgram, useUpdateProgram } from '@/features/academic-structure/hooks/usePrograms';

const LEVEL_OPTIONS = [
  { value: '', label: 'Sin especificar' },
  { value: 'UNDERGRADUATE', label: 'Pregrado' },
  { value: 'GRADUATE', label: 'Posgrado' },
  { value: 'EXTENSION', label: 'Extensión' },
  { value: 'DIPLOMA', label: 'Diplomatura' },
];

const TEACHING_MODE_OPTIONS = [
  { value: '', label: 'Sin especificar' },
  { value: 'IN_PERSON', label: 'Presencial' },
  { value: 'ONLINE', label: 'Virtual' },
  { value: 'HYBRID', label: 'Híbrido' },
];

function CampoSelect({ label, value, onChange, options }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">{label}</label>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-xl border border-surface-border bg-surface/50 px-3 py-2 text-[13.5px] text-ink outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}

export function CarreraEditarDatos() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: program, isLoading } = useProgram(id);
  const updateProgram = useUpdateProgram(id);

  const [name, setName] = useState('');
  const [totalYears, setTotalYears] = useState('');
  const [level, setLevel] = useState('');
  const [teachingMode, setTeachingMode] = useState('');
  const [totalCreditsRequired, setTotalCreditsRequired] = useState('');
  const [totalHoursRequired, setTotalHoursRequired] = useState('');
  const [graduationProfile, setGraduationProfile] = useState('');
  const [admissionRequirements, setAdmissionRequirements] = useState('');
  const [error, setError] = useState(null);

  useEffect(() => {
    if (program) {
      setName(program.name ?? '');
      setTotalYears(program.totalYears?.toString() ?? '');
      setLevel(program.level ?? '');
      setTeachingMode(program.teachingMode ?? '');
      setTotalCreditsRequired(program.totalCreditsRequired?.toString() ?? '');
      setTotalHoursRequired(program.totalHoursRequired?.toString() ?? '');
      setGraduationProfile(program.graduationProfile ?? '');
      setAdmissionRequirements(program.admissionRequirements ?? '');
    }
  }, [program]);

  async function handleGuardar() {
    if (!name.trim()) {
      setError('El nombre es obligatorio.');
      return;
    }
    if (!totalYears || parseInt(totalYears) < 1) {
      setError('La duración debe ser al menos 1 año.');
      return;
    }
    setError(null);
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
      setError(
        err?.status === 400
          ? 'Revisá los datos: la duración tiene que ser al menos 1 año y los créditos no pueden ser negativos.'
          : 'No se pudieron guardar los cambios. Intentá de nuevo.',
      );
    }
  }

  if (isLoading) {
    return (
      <PageContainer>
        <Skeleton className="h-8 w-64 rounded-xl" />
      </PageContainer>
    );
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
            {program?.name ?? 'Carrera'}
          </Link>
          <h1 className="font-heading text-xl font-bold text-ink">Editar datos</h1>
        </div>

        <GlassCard className="flex flex-col gap-5 p-6">
          <div className="flex flex-col gap-1.5">
            <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
              Nombre <span className="text-danger">*</span>
            </label>
            <Input value={name} onChange={(e) => setName(e.target.value)} />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-1.5">
              <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
                Duración (años) <span className="text-danger">*</span>
              </label>
              <Input
                type="number"
                min={1}
                max={10}
                value={totalYears}
                onChange={(e) => setTotalYears(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
                Créditos requeridos
              </label>
              <Input
                type="number"
                min={0}
                value={totalCreditsRequired}
                onChange={(e) => setTotalCreditsRequired(e.target.value)}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <CampoSelect label="Nivel" value={level} onChange={setLevel} options={LEVEL_OPTIONS} />
            <CampoSelect
              label="Modalidad"
              value={teachingMode}
              onChange={setTeachingMode}
              options={TEACHING_MODE_OPTIONS}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
              Horas requeridas
            </label>
            <Input
              type="number"
              min={0}
              value={totalHoursRequired}
              onChange={(e) => setTotalHoursRequired(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
              Perfil del egresado
            </label>
            <Textarea
              rows={4}
              value={graduationProfile}
              onChange={(e) => setGraduationProfile(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
              Requisitos de admisión
            </label>
            <Textarea
              rows={3}
              value={admissionRequirements}
              onChange={(e) => setAdmissionRequirements(e.target.value)}
            />
          </div>

          {error && <p className="text-[13px] text-danger">{error}</p>}

          <div className="flex items-center justify-between">
            <Button
              variant="outline"
              onClick={() => navigate(`/administrativo/gestion-academica/carreras/${id}`)}
            >
              Cancelar
            </Button>
            <Button variant="gradient" loading={updateProgram.isPending} onClick={handleGuardar}>
              Guardar cambios
            </Button>
          </div>
        </GlassCard>
      </div>
    </PageContainer>
  );
}
