import { useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useTemplateVersion, useTemplateVersions } from '@/features/graduation/certificates/hooks/useCertificateTemplate';
import { TEXT_FIELDS } from '@/features/graduation/certificates/lib/certificateTemplate';
import { formatDayAndTime } from '@/features/graduation/certificates/lib/certificateView';

function formatPublishedAt(value) {
  return value ? formatDayAndTime(new Date(value)) : '';
}

function SignerLine({ label, signer }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-size-12 font-semibold text-ink-faint">{label}</dt>
      <dd className="text-size-14 text-ink">{signer?.name ? `${signer.name} · ${signer.role ?? ''}` : 'Sin firmante'}</dd>
    </div>
  );
}

function VersionDialog({ kind, number, onClose }) {
  const version = useTemplateVersion(kind, number);
  const content = version.data?.content;
  return (
    <Dialog open={number != null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Versión {number}</DialogTitle>
          <DialogDescription>Versión publicada: no se puede editar. Los certificados ya emitidos conservan la suya.</DialogDescription>
        </DialogHeader>
        {version.isPending && <div role="status" aria-label="Cargando versión"><Skeleton className="h-48" /></div>}
        {version.isError && <LoadErrorState bare error={version.error} onRetry={version.refetch} resource="la versión" />}
        {content && (
          <dl className="flex max-h-[60vh] flex-col gap-3 overflow-y-auto">
            {TEXT_FIELDS.map((field) => (
              <div key={field.name} className="flex flex-col gap-0.5">
                <dt className="text-size-12 font-semibold text-ink-faint">{field.label}</dt>
                <dd className="text-size-14 text-ink">{content[field.name] || 'Sin completar'}</dd>
              </div>
            ))}
            <SignerLine label="Firmante 1" signer={content.firstSigner} />
            <SignerLine label="Firmante 2" signer={content.secondSigner} />
          </dl>
        )}
      </DialogContent>
    </Dialog>
  );
}

export function TemplateVersions({ kind }) {
  const versions = useTemplateVersions(kind);
  const [selected, setSelected] = useState(null);

  let body;
  if (versions.isPending) body = <div role="status" aria-label="Cargando versiones"><Skeleton className="h-24" /></div>;
  else if (versions.isError) body = <LoadErrorState bare error={versions.error} onRetry={versions.refetch} resource="las versiones" />;
  else if (versions.data.length === 0) body = <p className="text-size-14 text-ink-soft">Todavía no hay versiones publicadas.</p>;
  else {
    const sorted = [...versions.data].sort((a, b) => b.versionNumber - a.versionNumber);
    body = (
      <ul className="flex flex-col gap-1">
        {sorted.map((version, index) => (
          <li key={version.versionNumber} className="flex flex-wrap items-center justify-between gap-2 rounded-2xl px-4 py-3 hover:bg-neutral-bg/60">
            <div className="flex flex-col">
              <span className="text-size-14 font-semibold text-ink">
                Versión {version.versionNumber}
                {index === 0 ? ' · vigente' : ''}
              </span>
              <span className="text-size-13 text-ink-soft">
                Publicada el {formatPublishedAt(version.publishedAt)} por {version.publishedBy}
              </span>
            </div>
            <Button type="button" variant="outline" size="sm" aria-label={`Ver versión ${version.versionNumber}`} onClick={() => setSelected(version.versionNumber)}>
              Ver
            </Button>
          </li>
        ))}
      </ul>
    );
  }

  return (
    <GlassCard className="flex flex-col gap-3 p-4 md:p-5">
      <h2 className="font-heading text-size-15 font-semibold text-ink">Versiones publicadas</h2>
      {body}
      <VersionDialog kind={kind} number={selected} onClose={() => setSelected(null)} />
    </GlassCard>
  );
}
