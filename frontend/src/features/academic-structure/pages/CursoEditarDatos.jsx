import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { moneyValue } from '@/lib/utils';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/Skeleton';
import { useCourse, useUpdateCourse } from '@/features/academic-structure/hooks/useCourses';

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

function isoToDatetimeLocal(iso) {
  if (!iso) return '';
  return iso.slice(0, 16);
}

export function CursoEditarDatos() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: course, isLoading } = useCourse(id);
  const updateCourse = useUpdateCourse(id);

  const [name, setName] = useState('');
  const [capacity, setCapacity] = useState('');
  const [price, setPrice] = useState('');
  const [teachingMode, setTeachingMode] = useState('');
  const [schedule, setSchedule] = useState('');
  const [durationMonths, setDurationMonths] = useState('');
  const [registrationStart, setRegistrationStart] = useState('');
  const [registrationEnd, setRegistrationEnd] = useState('');
  const [error, setError] = useState(null);

  useEffect(() => {
    if (course) {
      setName(course.name ?? '');
      setCapacity(course.capacity?.toString() ?? '');
      setPrice(course.price ? String(moneyValue(course.price)) : '');
      setTeachingMode(course.teachingMode ?? '');
      setSchedule(course.schedule ?? '');
      setDurationMonths(course.durationMonths?.toString() ?? '');
      setRegistrationStart(isoToDatetimeLocal(course.registrationStart));
      setRegistrationEnd(isoToDatetimeLocal(course.registrationEnd));
    }
  }, [course]);

  async function handleGuardar() {
    if (!name.trim()) {
      setError('El nombre es obligatorio.');
      return;
    }
    if (!capacity || parseInt(capacity) < 1) {
      setError('El cupo debe ser al menos 1.');
      return;
    }
    if (!durationMonths || parseInt(durationMonths) < 1) {
      setError('La duración en meses es obligatoria y tiene que ser al menos 1.');
      return;
    }
    if (registrationStart && registrationEnd && registrationEnd < registrationStart) {
      setError('El cierre de inscripción no puede ser anterior a la apertura.');
      return;
    }
    setError(null);
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
    } catch {
      setError('No se pudo guardar los cambios. Intentá de nuevo.');
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
            to={`/administrativo/gestion-academica/cursos/${id}`}
            className="mb-1 flex items-center gap-1 text-[12px] text-ink-faint hover:text-ink"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            {course?.name ?? 'Curso'}
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
                Cupo <span className="text-danger">*</span>
              </label>
              <Input
                type="number"
                min={1}
                value={capacity}
                onChange={(e) => setCapacity(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">Precio</label>
              <Input
                type="number"
                min={0}
                step={0.01}
                value={price}
                onChange={(e) => setPrice(e.target.value)}
              />
            </div>
          </div>

          <CampoSelect
            label="Modalidad"
            value={teachingMode}
            onChange={setTeachingMode}
            options={TEACHING_MODE_OPTIONS}
          />

          <div className="flex flex-col gap-1.5">
            <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">Horario</label>
            <Input value={schedule} onChange={(e) => setSchedule(e.target.value)} />
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
              Duración (meses)
            </label>
            <Input
              type="number"
              min={1}
              value={durationMonths}
              onChange={(e) => setDurationMonths(e.target.value)}
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-1.5">
              <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
                Inicio de inscripción
              </label>
              <Input
                type="datetime-local"
                value={registrationStart}
                onChange={(e) => setRegistrationStart(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[12px] font-medium uppercase tracking-wide text-ink-faint">
                Cierre de inscripción
              </label>
              <Input
                type="datetime-local"
                value={registrationEnd}
                onChange={(e) => setRegistrationEnd(e.target.value)}
              />
            </div>
          </div>

          {error && <p className="text-[13px] text-danger">{error}</p>}

          <div className="flex items-center justify-between">
            <Button
              variant="outline"
              onClick={() => navigate(`/administrativo/gestion-academica/cursos/${id}`)}
            >
              Cancelar
            </Button>
            <Button variant="gradient" loading={updateCourse.isPending} onClick={handleGuardar}>
              Guardar cambios
            </Button>
          </div>
        </GlassCard>
      </div>
    </PageContainer>
  );
}
