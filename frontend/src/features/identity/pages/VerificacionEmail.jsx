import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
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
  const requestedTokenRef = useRef(null);

  useEffect(() => {
    if (!token) {
      setEstado('error');
      return;
    }
    if (requestedTokenRef.current === token) return;
    requestedTokenRef.current = token;
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
              <p className="text-[14px] text-ink-soft">Confirmando tu email…</p>
            </>
          )}

          {estado === 'ok' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-success-bg">
                <CheckCircle2 size={36} className="text-success" />
              </div>
              <div>
                <p className="font-heading text-lg font-bold text-ink">Email confirmado</p>
                <p className="mt-1 text-[13px] text-ink-soft">
                  Tu email quedó confirmado. Desde ahora lo usás para ingresar.
                </p>
              </div>
              <Link to="/login" className="text-[13px] font-semibold text-brand hover:text-accent-2">
                Ir a ingresar
              </Link>
            </>
          )}

          {estado === 'error' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-danger-bg">
                <XCircle size={36} className="text-danger" />
              </div>
              <div>
                <p className="font-heading text-lg font-bold text-ink">El enlace venció o ya se usó</p>
                <p className="mt-1 text-[13px] text-ink-soft">
                  Los enlaces duran 48 horas y sirven una sola vez. Si todavía no confirmaste tu email, entrá a Mi perfil y pedí un enlace nuevo; si ya lo confirmaste, no hace falta hacer nada más.
                </p>
              </div>
              <div className="flex flex-wrap justify-center gap-4">
                <Link to="/perfil" className="text-[13px] font-semibold text-brand hover:text-accent-2">
                  Ir a Mi perfil
                </Link>
                <Link to="/login" className="text-[13px] font-semibold text-brand hover:text-accent-2">
                  Ir a ingresar
                </Link>
              </div>
            </>
          )}
        </GlassCard>
      </motion.div>
    </div>
  );
}
