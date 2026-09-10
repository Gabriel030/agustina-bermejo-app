import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { Stepper } from '@/components/Stepper/Stepper';
import { DatosBasicosStep } from '@/features/gestion-personas/steps/DatosBasicosStep';
import { DocumentosStep } from '@/features/gestion-personas/steps/DocumentosStep';
import { DatosConfirmacionStep } from '@/features/gestion-personas/steps/DatosConfirmacionStep';
import { ResumenStep } from '@/features/gestion-personas/steps/ResumenStep';
import { useCrearProspecto, useGuardarBorradorProspecto, useConfirmarAlta } from '@/features/gestion-personas/hooks/useProspectos';

const PASOS = ['Datos básicos', 'Documentos', 'Confirmación', 'Resumen'];

const schema = z.object({
  nombre: z.string().min(1, 'Requerido'),
  apellido: z.string().min(1, 'Requerido'),
  email: z.string().email('Email inválido'),
  telefono: z.string().optional(),
  dni: z.string().optional(),
  street: z.string().optional(),
  number: z.string().optional(),
  floor: z.string().optional(),
  city: z.string().optional(),
  state: z.string().optional(),
  postalCode: z.string().optional(),
  country: z.string().optional(),
});

const CAMPOS_POR_PASO = {
  0: ['nombre', 'apellido', 'email'],
  1: [],
  2: [],
};

export function NuevoAlumno() {
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = useState(0);
  const [studentId, setStudentId] = useState(null);
  const [documentos, setDocumentos] = useState({});

  const crearProspecto = useCrearProspecto();
  const guardarBorrador = useGuardarBorradorProspecto();
  const confirmarAlta = useConfirmarAlta();

  const {
    register,
    trigger,
    getValues,
    formState: { errors },
  } = useForm({ resolver: zodResolver(schema), mode: 'onTouched' });

  const isMutating = crearProspecto.isPending || guardarBorrador.isPending || confirmarAlta.isPending;

  async function avanzar() {
    const camposActuales = CAMPOS_POR_PASO[currentStep] ?? [];
    if (camposActuales.length > 0) {
      const valid = await trigger(camposActuales);
      if (!valid) return;
    }

    if (currentStep === 0) {
      const values = getValues();
      crearProspecto.mutate(
        { firstName: values.nombre, lastName: values.apellido, email: values.email, phone: values.telefono || undefined },
        {
          onSuccess: (data) => {
            setStudentId(data.id);
            setCurrentStep(1);
          },
        }
      );
      return;
    }

    if (currentStep === 2) {
      const values = getValues();
      const borrador = {
        dni: values.dni || null,
        street: values.street || null,
        number: values.number || null,
        floor: values.floor || null,
        city: values.city || null,
        state: values.state || null,
        postalCode: values.postalCode || null,
        country: values.country || null,
      };
      guardarBorrador.mutate(
        { studentId, datos: borrador },
        {
          onSuccess: () => {
            confirmarAlta.mutate(
              { studentId },
              { onSuccess: () => setCurrentStep(3) }
            );
          },
        }
      );
      return;
    }

    setCurrentStep((s) => s + 1);
  }

  function retroceder() {
    if (currentStep === 0) {
      navigate(-1);
    } else {
      setCurrentStep((s) => s - 1);
    }
  }

  const errorMutation = crearProspecto.error || guardarBorrador.error || confirmarAlta.error;

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Alta de alumno</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Registrá un nuevo alumno en el sistema</p>
      </div>

      <GlassCard className="p-6 flex flex-col gap-6 max-w-xl">
        <Stepper steps={PASOS} currentStep={currentStep} />

        <form onSubmit={(e) => e.preventDefault()}>
          {currentStep === 0 && <DatosBasicosStep register={register} errors={errors} isLoading={isMutating} />}
          {currentStep === 1 && (
            <DocumentosStep
              studentId={studentId}
              documentosSubidos={documentos}
              onDocumentoSubido={(type, doc) => setDocumentos((prev) => ({ ...prev, [type]: doc }))}
            />
          )}
          {currentStep === 2 && <DatosConfirmacionStep register={register} errors={errors} isLoading={isMutating} />}
          {currentStep === 3 && <ResumenStep alumno={getValues()} esAnonimo={false} />}
        </form>

        {errorMutation && (
          <p className="text-[12px] text-danger">
            {errorMutation.message ?? 'Ocurrió un error. Intentá de nuevo.'}
          </p>
        )}

        <div className="flex items-center justify-between gap-3">
          {currentStep < 3 ? (
            <>
              <Button type="button" variant="ghost" onClick={retroceder} disabled={isMutating}>
                {currentStep === 0 ? 'Cancelar' : 'Atrás'}
              </Button>
              <Button type="button" onClick={avanzar} disabled={isMutating}>
                {currentStep === 2 ? 'Confirmar alta' : 'Siguiente'}
              </Button>
            </>
          ) : (
            <Button type="button" onClick={() => navigate(-1)} className="ml-auto">
              Volver al padrón
            </Button>
          )}
        </div>
      </GlassCard>
    </PageContainer>
  );
}
