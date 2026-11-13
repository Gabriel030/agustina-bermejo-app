import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { ThemeToggle } from '@/components/ThemeToggle';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { CertificateDetails } from '@/features/graduation/certificates/components/CertificateDetails';
import { CertificateVerdict } from '@/features/graduation/certificates/components/CertificateVerdict';
import { VerifyAnotherCode } from '@/features/graduation/certificates/components/VerifyAnotherCode';
import { useRetryCountdown } from '@/features/graduation/certificates/hooks/useRetryCountdown';
import { usePublicCertificate } from '@/features/graduation/certificates/hooks/usePublicCertificate';
import { isWellFormedCertificateCode, normalizeCertificateCode } from '@/features/graduation/certificates/lib/certificateCode';
import { verdictOf } from '@/features/graduation/certificates/lib/certificateView';

const DEFAULT_RETRY_SECONDS = 60;

const NOT_FOUND_VERDICT = {
  icon: 'unknown',
  tone: 'neutral',
  title: 'Sin resultados',
  message: 'No encontramos un documento con este código. Revisá que esté completo.',
};

function LoadingCard() {
  return (
    <GlassCard className="overflow-hidden" aria-busy="true">
      <div className="flex items-center gap-4 bg-neutral-bg px-4 py-[22px] sm:px-5">
        <Skeleton className="size-16 shrink-0 rounded-full" />
        <div className="flex flex-1 flex-col gap-2">
          <Skeleton className="h-[26px] w-3/5" />
          <Skeleton className="h-3.5 w-[85%]" />
        </div>
      </div>
      <div className="flex flex-col gap-3.5 p-5">
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="h-[18px]" />
        <Skeleton className="h-[18px]" />
        <Skeleton className="h-[18px]" />
      </div>
    </GlassCard>
  );
}

function useDocumentTitle(title) {
  useEffect(() => {
    const previous = document.title;
    document.title = `${title} · Verificar certificado`;
    return () => {
      document.title = previous;
    };
  }, [title]);
}

export function VerifyCertificate() {
  const { code } = useParams();
  const navigate = useNavigate();
  const normalized = normalizeCertificateCode(code);
  const wellFormed = isWellFormedCertificateCode(normalized);
  const query = usePublicCertificate(normalized);
  const cooldown = useRetryCountdown();

  const failure = query.error;
  const limited = failure?.kind === 'RATE_LIMITED';
  const notFound = !wellFormed || failure?.kind === 'NOT_FOUND';
  const failed = !notFound && !limited && query.isError;
  const loading = wellFormed && query.isFetching && !query.data;
  const certificate = !notFound && !limited && !failed ? query.data : null;
  const verdict = certificate ? verdictOf(certificate) : null;

  const { start: startCooldown } = cooldown;
  useEffect(() => {
    if (limited) startCooldown(failure.retryAfterSeconds ?? DEFAULT_RETRY_SECONDS);
  }, [limited, failure, startCooldown]);

  const waiting = limited && cooldown.active;

  let title = 'Verificando';
  if (notFound) title = NOT_FOUND_VERDICT.title;
  else if (limited) title = 'Demasiadas consultas';
  else if (failed) title = 'Error';
  else if (verdict) title = verdict.title;
  useDocumentTitle(title);

  function verifyOther(otherCode) {
    navigate(`/verificar/${otherCode}`);
  }

  const verifyAnother = <VerifyAnotherCode onSubmit={verifyOther} disabled={waiting} />;

  return (
    <div className="relative min-h-screen px-4 pt-5 pb-12">
      <MeshBackground />
      <div className="mx-auto flex max-w-[560px] flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
          <InstitutionBrand layout="horizontal" titulo="Instituto Agustina Bermejo" />
          <ThemeToggle />
        </div>

        {loading && <LoadingCard />}

        {!loading && notFound && (
          <GlassCard className="overflow-hidden">
            <CertificateVerdict {...NOT_FOUND_VERDICT} />
            {verifyAnother}
          </GlassCard>
        )}

        {!loading && limited && (
          <GlassCard className="overflow-hidden">
            <CertificateVerdict
              icon="wait"
              tone="warning"
              title="Demasiadas consultas"
              message={waiting ? `Probá de nuevo en ${cooldown.secondsLeft} s.` : 'Ya podés probar de nuevo.'}
            >
              <Button type="button" className="mt-3 h-11 px-[18px]" disabled={waiting} onClick={() => query.refetch()}>
                Reintentar
              </Button>
            </CertificateVerdict>
            {verifyAnother}
          </GlassCard>
        )}

        {!loading && failed && (
          <GlassCard className="overflow-hidden">
            <CertificateVerdict
              role="alert"
              icon="offline"
              tone="neutral"
              title="No pudimos verificar"
              message="Es un problema de conexión. Probá de nuevo."
            >
              <Button type="button" variant="gradient" className="mt-3 h-11 px-[18px]" onClick={() => query.refetch()}>
                Reintentar
              </Button>
            </CertificateVerdict>
            {verifyAnother}
          </GlassCard>
        )}

        {!loading && certificate && (
          <GlassCard className="overflow-hidden">
            <CertificateVerdict {...verdict} />
            <CertificateDetails
              certificate={certificate}
              code={normalized}
              consultedAt={new Date(query.dataUpdatedAt)}
            />
            {verifyAnother}
          </GlassCard>
        )}

        <p className="text-center text-size-12-5 text-ink-faint">
          Verificación pública. No se muestran datos de contacto ni de quien emitió el documento.
        </p>
      </div>
    </div>
  );
}
