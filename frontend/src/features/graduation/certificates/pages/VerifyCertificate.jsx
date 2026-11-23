import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { CertificateDetails } from '@/features/graduation/certificates/components/CertificateDetails';
import { CertificateVerdict } from '@/features/graduation/certificates/components/CertificateVerdict';
import { VerifyAnotherCode } from '@/features/graduation/certificates/components/VerifyAnotherCode';
import { useRetryCountdown } from '@/features/graduation/certificates/hooks/useRetryCountdown';
import { usePublicCertificate } from '@/features/graduation/certificates/hooks/usePublicCertificate';
import { formatCertificateCode, isWellFormedCertificateCode, normalizeCertificateCode } from '@/features/graduation/certificates/lib/certificateCode';
import { verdictOf } from '@/features/graduation/certificates/lib/certificateView';

const DEFAULT_RETRY_SECONDS = 60;

const NOT_FOUND_VERDICT = {
  icon: 'unknown',
  tone: 'neutral',
  badge: 'No encontrada',
  title: 'No encontramos un documento con ese código',
  message: 'Revisá el código impreso debajo del QR y volvé a intentar. Si el problema sigue, el documento podría no ser auténtico.',
};

function LoadingCard() {
  return (
    <GlassCard className="overflow-hidden" aria-busy="true">
      <div className="flex flex-col items-start gap-3 bg-neutral-bg px-5 pt-5 pb-4 sm:px-[22px]">
        <Skeleton className="h-7 w-44 rounded-full" />
        <Skeleton className="h-6 w-4/5" />
        <Skeleton className="h-4 w-3/5" />
      </div>
      <div className="flex flex-col gap-3 px-5 py-4 sm:px-[22px]">
        <Skeleton className="h-[18px]" />
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
    document.title = `${title} · Verificación de documentos`;
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
  if (notFound) title = NOT_FOUND_VERDICT.badge;
  else if (limited) title = 'Demasiadas consultas';
  else if (failed) title = 'No pudimos verificar';
  else if (verdict) title = verdict.badge;
  useDocumentTitle(title);

  function verifyOther(otherCode) {
    navigate(`/verificar/${otherCode}`);
  }

  const verifyAnother = (
    <GlassCard>
      <VerifyAnotherCode onSubmit={verifyOther} disabled={waiting} label="¿Tenés otro documento? Verificá otro código" />
    </GlassCard>
  );

  return (
    <div className="relative min-h-screen px-4 pt-7 pb-12">
      <div className="mx-auto flex max-w-[560px] flex-col gap-4">
        <InstitutionBrand layout="horizontal" titulo="Instituto Agustina Bermejo" className="justify-center" />

        {certificate && (
          <p className="text-center text-size-12 tabular-nums [overflow-wrap:anywhere] text-ink-faint">
            verificar/{formatCertificateCode(normalized)}
          </p>
        )}

        {loading && <LoadingCard />}

        {!loading && notFound && (
          <GlassCard className="overflow-hidden">
            <CertificateVerdict {...NOT_FOUND_VERDICT} />
            <VerifyAnotherCode onSubmit={verifyOther} primary />
          </GlassCard>
        )}

        {!loading && limited && (
          <>
            <GlassCard className="overflow-hidden">
              <CertificateVerdict
                icon="wait"
                tone="neutral"
                badge="Demasiadas consultas"
                title="Hiciste muchas verificaciones seguidas"
                message={waiting ? `Probá de nuevo en ${cooldown.secondsLeft} s.` : 'Ya podés probar de nuevo.'}
              >
                <Button type="button" variant="primary" className="mt-1 h-11 px-[18px]" disabled={waiting} onClick={() => query.refetch()}>
                  Reintentar
                </Button>
              </CertificateVerdict>
            </GlassCard>
            {verifyAnother}
          </>
        )}

        {!loading && failed && (
          <>
            <GlassCard className="overflow-hidden">
              <CertificateVerdict
                role="alert"
                icon="offline"
                tone="neutral"
                badge="No pudimos verificar"
                title="No pudimos consultar el documento"
                message="Es un problema de conexión. Probá de nuevo."
              >
                <Button type="button" variant="primary" className="mt-1 h-11 px-[18px]" onClick={() => query.refetch()}>
                  Reintentar
                </Button>
              </CertificateVerdict>
            </GlassCard>
            {verifyAnother}
          </>
        )}

        {!loading && certificate && (
          <>
            <GlassCard className="overflow-hidden">
              <CertificateVerdict {...verdict} />
              <CertificateDetails certificate={certificate} />
            </GlassCard>
            {verifyAnother}
          </>
        )}

        <p className="text-center text-size-12-5 text-ink-faint">Instituto Agustina Bermejo · Verificación de documentos</p>
      </div>
    </div>
  );
}
