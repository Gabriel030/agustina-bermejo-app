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
import { useCrearProspecto, useSubirDocumentoProspecto, useGuardarBorradorProspecto, useConfirmarAlta } from '@/features/gestion-personas/hooks/useProspectos';

const PASOS = [
  { key: '0', label: 'Datos básicos' },
  { key: '1', label: 'Documentos' },
  { key: '2', label: 'Confirmación' },
  { key: '3', label: 'Resumen' },
];

const schema = z.object({
  nombre: z.string().min(1, 'Requerido'),
  apellido: z.string().min(1, 'Requerido'),
  email: z.string().email('Email inválido'),
  telefono: z.string().optional(),
  dni: z.string().min(1, 'Requerido'),
  street: z.string().min(1, 'Requerido'),
  number: z.string().optional(),
  floor: z.string().optional(),
  city: z.string().min(1, 'Requerido'),
  state: z.string().optional(),
  postalCode: z.string().optional(),
  country: z.string().optional(),
});

const CAMPOS_POR_PASO = {
  0: ['nombre', 'apellido', 'email'],
  1: [],
  2: ['dni', 'street', 'city'],
};

export function NuevoAlumno() {
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = useState(0);
  const [pasosVisitados, setPasosVisitados] = useState(['0']);
  const [studentId, setStudentId] = useState(null);
  const [documentos, setDocumentos] = useState({});
  const [errorDocumentos, setErrorDocumentos] = useState(null);

  const crearProspecto = useCrearProspecto();
  const subirDocumento = useSubirDocumentoProspecto();
  const guardarBorrador = useGuardarBorradorProspecto();
  const confirmarAlta = useConfirmarAlta();

  const {
    register,
    trigger,
    getValues,
    formState: { errors },
  } = useForm({ resolver: zodResolver(schema), mode: 'onTouched' });

  const isMutating = crearProspecto.isPending || guardarBorrador.isPending || confirmarAlta.isPending;

  function handleFileSelected(tipo, file) {
    subirDocumento.mutate(
      { studentId, type: tipo, file },
      { onSuccess: (doc) => setDocumentos((prev) => ({ ...prev, [tipo]: doc })) }
    );
  }

  async function avanzar() {
    const camposActuales = CAMPOS_POR_PASO[currentStep] ?? [];
    if (camposActuales.length > 0) {
      const valid = await trigger(camposActuales);
      if (!valid) return;
    }

    if (currentStep === 1) {
      const TIPOS_REQUERIDOS = ['DNI_FRENTE', 'DNI_DORSO', 'ANALITICO_SECUNDARIO'];
      const faltantes = TIPOS_REQUERIDOS.filter((t) => !documentos[t]);
      if (faltantes.length > 0) {
        setErrorDocumentos('Tenés que subir todos los documentos para continuar.');
        return;
      }
      setErrorDocumentos(null);
    }

    if (currentStep === 0) {
      const values = getValues();
      crearProspecto.mutate(
        { nombre: values.nombre, apellido: values.apellido, email: values.email, telefono: values.telefono || undefined },
        {
          onSuccess: (data) => {
            setStudentId(data.id);
            setCurrentStep(1);
            setPasosVisitados((prev) => [...new Set([...prev, '1'])]);
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
              { studentId, datos: borrador },
              { onSuccess: () => { setCurrentStep(3); setPasosVisitados((prev) => [...new Set([...prev, '3'])]); } }
            );
          },
        }
      );
      return;
    }

    const next = currentStep + 1;
    setCurrentStep(next);
    setPasosVisitados((prev) => [...new Set([...prev, String(next)])]);
  }

  function irAStep(key) {
    setCurrentStep(Number(key));
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

      <GlassCard className="p-6 flex flex-col gap-6">
        <Stepper
          pasos={PASOS}
          pasoActual={String(currentStep)}
          pasosVisitados={pasosVisitados}
          onStepClick={isMutating ? undefined : irAStep}
        />

        <form onSubmit={(e) => e.preventDefault()}>
          {currentStep === 0 && <DatosBasicosStep register={register} errors={errors} isLoading={isMutating} />}
          {currentStep === 1 && (
            <DocumentosStep
              documentosSubidos={documentos}
              onFileSelected={handleFileSelected}
              isUploading={subirDocumento.isPending}
              error={subirDocumento.isError}
              errorValidacion={errorDocumentos}
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
