import { useRef, useState } from 'react';
import { AlertTriangle, WifiOff } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { useFirstDirectorStatus } from '@/features/platform/hooks/useFirstDirectorStatus';
import { useProvisionFirstDirector } from '@/features/platform/hooks/useProvisionFirstDirector';
import { useFirstDirectorForm } from '@/features/platform/hooks/useFirstDirectorForm';
import { failureMessageOf, fieldErrorOf, isDirectorAlreadyExists, isRetryableFailure } from '@/features/platform/lib/firstDirectorErrors';
import { toRequest } from '@/features/platform/lib/firstDirectorForm';
import { FirstDirectorBanner } from '@/features/platform/components/FirstDirectorBanner';
import { FirstDirectorConfirmDialog } from '@/features/platform/components/FirstDirectorConfirmDialog';
import { FirstDirectorForm } from '@/features/platform/components/FirstDirectorForm';
import { FirstDirectorSuccess } from '@/features/platform/components/FirstDirectorSuccess';
import {
  FirstDirectorExistsCard,
  FirstDirectorForbiddenCard,
  FirstDirectorLoadErrorCard,
  FirstDirectorLoadingCard,
} from '@/features/platform/components/FirstDirectorStateCard';

const RETRY_HINT = 'Podés reintentar: si el Director ya se había creado, no se duplica.';

function SubmitFailureBanner({ error }) {
  if (error.status === undefined) {
    return (
      <FirstDirectorBanner tone="danger" icon={WifiOff} title="No pudimos confirmar si se creó" focusOnMount>
        Se cortó la conexión antes de la respuesta. {RETRY_HINT}
      </FirstDirectorBanner>
    );
  }
  if (isRetryableFailure(error)) {
    return (
      <FirstDirectorBanner tone="danger" icon={AlertTriangle} title="No se pudo confirmar la creación" requestId={error.requestId} focusOnMount>
        El servidor no pudo confirmar el pedido. {RETRY_HINT}
      </FirstDirectorBanner>
    );
  }
  return (
    <FirstDirectorBanner tone="danger" icon={AlertTriangle} title="No se pudo crear el Director" requestId={error.requestId} focusOnMount>
      {failureMessageOf(error)}
    </FirstDirectorBanner>
  );
}

export function PrimerDirector() {
  const status = useFirstDirectorStatus();
  const provision = useProvisionFirstDirector();
  const [confirming, setConfirming] = useState(false);
  const [created, setCreated] = useState(null);
  const [alreadyExists, setAlreadyExists] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [submittedForm, setSubmittedForm] = useState(null);
  const submittingRef = useRef(false);

  const lockedInstitution = status.data && !status.data.directorExists ? status.data.institutionName : null;
  const formState = useFirstDirectorForm(lockedInstitution);

  function askConfirmation() {
    if (provision.isPending) return;
    const firstInvalid = formState.markAttempted();
    if (firstInvalid) document.getElementById(firstInvalid)?.focus();
    else setConfirming(true);
  }

  function submit() {
    if (submittingRef.current || provision.isPending) return;
    submittingRef.current = true;
    const submitted = formState.effectiveForm;
    setConfirming(false);
    setSubmittedForm(submitted);
    setSubmitError(null);
    provision.mutate(toRequest(submitted), {
      onSettled: () => {
        submittingRef.current = false;
      },
      onSuccess: (response) => setCreated(response),
      onError: (error) => {
        const fieldError = fieldErrorOf(error);
        if (isDirectorAlreadyExists(error)) {
          setAlreadyExists(true);
          status.refetch();
        } else if (fieldError) {
          formState.setServerFieldError(fieldError.field, fieldError.message);
          setTimeout(() => document.getElementById(fieldError.field)?.focus(), 0);
        } else {
          setSubmitError(error);
        }
      },
    });
  }

  const header = {
    title: 'Primer Director',
    description: 'Da de alta la institución y a su primer Director.',
    action: (
      <StatusPill tone="warning">
        <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
        Se hace una sola vez
      </StatusPill>
    ),
  };

  if (created) {
    const institutionName = created.institutionCreated
      ? submittedForm.institutionName.trim()
      : (status.data?.institutionName ?? submittedForm.institutionName.trim());
    return (
      <PageContainer {...header}>
        <FirstDirectorSuccess
          institutionName={institutionName}
          directorName={`${submittedForm.firstName.trim()} ${submittedForm.lastName.trim()}`}
          documentType={submittedForm.documentType}
          documentNumber={submittedForm.documentNumber.trim()}
          directorEmail={created.directorEmail}
        />
      </PageContainer>
    );
  }

  if (status.isPending) {
    return (
      <PageContainer {...header}>
        <FirstDirectorLoadingCard />
      </PageContainer>
    );
  }

  if (status.isError) {
    return (
      <PageContainer {...header}>
        {status.error?.status === 403 ? <FirstDirectorForbiddenCard /> : <FirstDirectorLoadErrorCard onRetry={() => status.refetch()} />}
      </PageContainer>
    );
  }

  if (status.data.directorExists || alreadyExists) {
    return (
      <PageContainer {...header}>
        <FirstDirectorExistsCard institutionName={status.data.institutionName} />
      </PageContainer>
    );
  }

  return (
    <PageContainer {...header}>
      <FirstDirectorForm
        formState={formState}
        institutionLocked={!!lockedInstitution}
        pending={provision.isPending}
        banner={submitError && <SubmitFailureBanner error={submitError} />}
        onSubmit={askConfirmation}
      />
      <FirstDirectorConfirmDialog open={confirming} onOpenChange={setConfirming} form={formState.effectiveForm} onConfirm={submit} />
    </PageContainer>
  );
}
