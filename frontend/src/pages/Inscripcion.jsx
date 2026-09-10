import { useState } from 'react';
import { motion } from 'motion/react';
import { MailCheck } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Button } from '@/components/ui/button';
import { DatosBasicosStep } from '@/features/gestion-personas/steps/DatosBasicosStep';
import { useCrearProspecto } from '@/features/gestion-personas/hooks/useProspectos';

const schema = z.object({
  nombre: z.string().min(1, 'Requerido'),
  apellido: z.string().min(1, 'Requerido'),
  email: z.string().email('Email inválido'),
  telefono: z.string().optional(),
});

export function Inscripcion() {
  const [emailEnviado, setEmailEnviado] = useState(null);
  const crearProspecto = useCrearProspecto();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ resolver: zodResolver(schema), mode: 'onTouched' });

  function onSubmit(values) {
    crearProspecto.mutate(
      { firstName: values.nombre, lastName: values.apellido, email: values.email, phone: values.telefono || undefined },
      { onSuccess: () => setEmailEnviado(values.email) }
    );
  }

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center p-4">
      <MeshBackground />
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25 }}
        className="w-full max-w-md"
      >
        <div className="mb-8 flex flex-col items-center gap-3">
          <InstitutionBrand />
          <h1 className="font-heading text-2xl font-bold text-ink text-center">Inscripción</h1>
          <p className="text-sm text-ink-soft text-center">Completá tus datos para iniciar el proceso de inscripción</p>
        </div>

        <GlassCard className="p-6">
          {emailEnviado ? (
            <div className="flex flex-col items-center gap-4 py-4 text-center">
              <div className="flex h-14 w-14 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30">
                <MailCheck size={28} className="text-green-600" />
              </div>
              <div>
                <p className="font-semibold text-ink">¡Listo! Revisá tu correo</p>
                <p className="mt-1 text-[13px] text-ink-soft">
                  Enviamos un correo a <strong>{emailEnviado}</strong> para confirmar tu inscripción. Seguí las instrucciones que te llegaron.
                </p>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
              <DatosBasicosStep register={register} errors={errors} isLoading={crearProspecto.isPending} />

              {crearProspecto.error && (
                <p className="text-[12px] text-danger">
                  {crearProspecto.error?.message ?? 'Algo salió mal. Intentá de nuevo.'}
                </p>
              )}

              <Button type="submit" disabled={crearProspecto.isPending} className="w-full">
                {crearProspecto.isPending ? 'Enviando…' : 'Inscribirme'}
              </Button>
            </form>
          )}
        </GlassCard>
      </motion.div>
    </div>
  );
}
