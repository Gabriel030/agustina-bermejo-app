import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { motion } from 'motion/react';
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { apiFetch } from '@/api/client';

export function VerificacionEmail() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [estado, setEstado] = useState('verificando');

  useEffect(() => {
    if (!token) {
      setEstado('error');
      return;
    }
    apiFetch(`/verify-email?token=${encodeURIComponent(token)}`)
      .then(() => setEstado('ok'))
      .catch(() => setEstado('error'));
  }, [token]);

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center p-4">
      <MeshBackground />
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25 }}
        className="w-full max-w-sm"
      >
        <div className="mb-8 flex flex-col items-center gap-3">
          <InstitutionBrand />
        </div>

        <GlassCard className="p-8 flex flex-col items-center gap-4 text-center">
          {estado === 'verificando' && (
            <>
              <Loader2 size={40} className="text-brand animate-spin" />
              <p className="text-[14px] text-ink-soft">Verificando tu email…</p>
            </>
          )}

          {estado === 'ok' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30">
                <CheckCircle2 size={36} className="text-green-600" />
              </div>
              <div>
                <p className="font-heading text-lg font-bold text-ink">Email verificado</p>
                <p className="mt-1 text-[13px] text-ink-soft">
                  Tu email fue confirmado correctamente. La institución procesará tu inscripción y te contactará a la brevedad.
                </p>
              </div>
            </>
          )}

          {estado === 'error' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/30">
                <XCircle size={36} className="text-danger" />
              </div>
              <div>
                <p className="font-heading text-lg font-bold text-ink">Link inválido o expirado</p>
                <p className="mt-1 text-[13px] text-ink-soft">
                  El enlace de verificación no es válido o ya expiró. Pedí a la institución que reenvíe el correo de verificación.
                </p>
              </div>
            </>
          )}
        </GlassCard>
      </motion.div>
    </div>
  );
}
