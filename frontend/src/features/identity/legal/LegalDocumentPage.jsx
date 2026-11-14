import { Link } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { StatusPill } from '@/components/StatusPill';
import { useAuth } from '@/context/AuthContext';
import { useCurrentLegalDocuments } from '@/features/identity/hooks/useLegal';
import { DocumentToc } from '@/features/identity/legal/DocumentToc';
import { LEGAL_CONTENT } from '@/features/identity/legal/legalContent';

function LegalDocumentPage({ type }) {
  const { usuario } = useAuth();
  const { data } = useCurrentLegalDocuments();
  const content = LEGAL_CONTENT[type];
  const version = data?.documents?.find((document) => document.type === type)?.version;

  return (
    <div className="relative flex min-h-screen flex-col items-center gap-4 px-4 py-8">
      <MeshBackground />
      <div
        role="note"
        className="flex w-full max-w-[760px] items-center gap-2.5 rounded-lg bg-warning-bg px-3.5 py-3 text-size-13 font-semibold text-warning-ink"
      >
        <AlertTriangle className="size-4 shrink-0" aria-hidden="true" />
        <span>BORRADOR: debe revisarlo un abogado antes de publicarse.</span>
      </div>
      <div className="grid w-full max-w-[760px] grid-cols-1 gap-6 lg:grid-cols-[200px_minmax(0,1fr)]">
        <DocumentToc sections={content.sections} />
        <GlassCard className="p-6">
          <div className="mb-3 flex flex-wrap gap-2">
            {version && <StatusPill tone="neutral">Versión {version}</StatusPill>}
            {type === 'PRIVACY' && <StatusPill tone="neutral">Ley 25.326</StatusPill>}
          </div>
          <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">{content.title}</h1>
          <div className="mt-4 flex flex-col gap-1 text-size-14 leading-relaxed text-ink-soft">
            {content.sections.map((section) => (
              <section key={section.id} aria-labelledby={section.id}>
                <h2 id={section.id} className="mb-1.5 mt-5 scroll-mt-4 font-heading text-base font-bold text-ink">
                  {section.title}
                </h2>
                {section.body}
              </section>
            ))}
          </div>
          <Link
            to={usuario ? '/perfil' : '/registrarse'}
            className="mt-6 inline-block text-size-13 font-semibold text-brand hover:text-accent-2"
          >
            {usuario ? 'Volver a Mi perfil' : 'Volver al registro'}
          </Link>
        </GlassCard>
      </div>
    </div>
  );
}

export function TermsPage() {
  return <LegalDocumentPage type="TERMS" />;
}

export function PrivacyPage() {
  return <LegalDocumentPage type="PRIVACY" />;
}
