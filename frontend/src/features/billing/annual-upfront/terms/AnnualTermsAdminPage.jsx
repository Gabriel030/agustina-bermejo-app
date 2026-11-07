import { useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { formatDateTime } from '@/lib/billing';
import { TermsDocument } from './TermsDocument';
import { TERMS_MAX_LENGTH, TERMS_MIN_LENGTH, isTermsTextValid, publisherLabelOf, termsPathOf } from './annualTerms';
import { usePublishTerms, useTermsVersions } from './useAnnualUpfrontTerms';

const PUBLISH_FALLBACK = 'No pudimos publicar la versión. Probá de nuevo.';

function TermsEditor({ initialText }) {
  const [text, setText] = useState(initialText);
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState('');
  const mutation = usePublishTerms();
  const length = text.trim().length;
  const valid = isTermsTextValid(text);

  function publish() {
    setMessage('');
    mutation.mutate(text.trim(), {
      onSuccess: (published) => {
        setConfirming(false);
        toast.success(`Publicaste la versión ${published.version}.`);
      },
      onError: (error) => {
        setConfirming(false);
        setMessage(error?.message ?? PUBLISH_FALLBACK);
      },
    });
  }

  return (
    <GlassCard className="flex flex-col gap-4 p-5">
      <Tabs defaultValue="edit" className="gap-3">
        <TabsList variant="segmented">
          <TabsTrigger value="edit">Editar</TabsTrigger>
          <TabsTrigger value="preview">Vista previa</TabsTrigger>
        </TabsList>
        <TabsContent value="edit" className="flex flex-col gap-1.5">
          <Textarea
            aria-label="Texto de los términos"
            aria-describedby="terms-counter"
            value={text}
            maxLength={TERMS_MAX_LENGTH}
            rows={14}
            onChange={(event) => {
              setText(event.target.value);
              setMessage('');
            }}
          />
          <span id="terms-counter" className={valid ? 'text-xs text-ink-faint' : 'text-xs text-danger'}>
            {length} de {TERMS_MAX_LENGTH} caracteres (mínimo {TERMS_MIN_LENGTH})
          </span>
        </TabsContent>
        <TabsContent value="preview">
          <TermsDocument text={text} />
        </TabsContent>
      </Tabs>
      {message && (
        <p role="alert" className="text-sm text-danger">
          {message}
        </p>
      )}
      <div className="flex justify-end">
        <Button variant="gradient" disabled={!valid} onClick={() => setConfirming(true)}>
          Publicar nueva versión
        </Button>
      </div>
      <ConfirmActionDialog
        open={confirming}
        onOpenChange={setConfirming}
        title="¿Publicar la nueva versión?"
        description="Rige desde que la publicás y no se puede deshacer. Las versiones publicadas son inmutables: para corregir algo hay que publicar otra."
        confirmLabel="Publicar"
        confirmVariant="default"
        pending={mutation.isPending}
        onConfirm={publish}
      />
    </GlassCard>
  );
}

function VersionsTable({ versions }) {
  return (
    <GlassCard className="p-0">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Versión</TableHead>
            <TableHead>Publicada</TableHead>
            <TableHead>Publicó</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead className="text-right">
              <span className="sr-only">Acciones</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {versions.map((version) => (
            <TableRow key={version.versionNumber}>
              <TableCell className="font-semibold">{version.version}</TableCell>
              <TableCell>{formatDateTime(version.publishedAt)}</TableCell>
              <TableCell>{publisherLabelOf(version.publishedByPersonId)}</TableCell>
              <TableCell>
                <StatusPill tone={version.current ? 'success' : 'neutral'}>{version.current ? 'Vigente' : 'Reemplazada'}</StatusPill>
              </TableCell>
              <TableCell className="text-right">
                <Button asChild variant="outline" size="sm">
                  <Link to={termsPathOf(version.versionNumber)} target="_blank" rel="noopener noreferrer">
                    Ver {version.version}
                  </Link>
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </GlassCard>
  );
}

function LoadingTerms() {
  return (
    <GlassCard className="flex flex-col gap-3 p-5" aria-busy="true" aria-label="Cargando los términos">
      {[0, 1, 2, 3].map((line) => (
        <Skeleton key={line} className="h-5 w-full" />
      ))}
    </GlassCard>
  );
}

export function AnnualTermsAdminPage() {
  const query = useTermsVersions();
  const versions = [...(query.data ?? [])].sort((a, b) => b.versionNumber - a.versionNumber);
  const latest = versions.find((version) => version.current) ?? versions[0];

  return (
    <PageContainer title="Términos del pago anual" description="Lo que aceptan los alumnos al pedir el año completo.">
      {query.isPending && <LoadingTerms />}
      {query.isError && <LoadErrorState error={query.error} onRetry={() => query.refetch()} resource="los términos" />}
      {query.data && (
        <>
          <TermsEditor key={latest?.versionNumber ?? 0} initialText={latest?.text ?? ''} />
          <h2 className="font-heading text-lg font-semibold text-ink">Versiones publicadas</h2>
          <VersionsTable versions={versions} />
        </>
      )}
    </PageContainer>
  );
}
