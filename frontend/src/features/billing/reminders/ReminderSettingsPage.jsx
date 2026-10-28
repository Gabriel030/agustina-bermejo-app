import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ChevronDown } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { Stepper } from '@/components/Stepper';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useOfferNames } from '@/features/billing/hooks/useOfferNames';
import {
  useReminderPolicies,
  useReminderScheduleVersions,
  useReminderSettingsVersions,
} from '@/features/billing/hooks/useCollectionReminders';
import { inboxPath, roleBaseOf } from '@/features/billing/authorization/authorizationRequests';
import { ExceptionsTable } from './ExceptionsTable';
import { ReminderConfirmStep } from './ReminderConfirmStep';
import { ReminderTimeline } from './ReminderTimeline';
import { GeneralSettingsForm, SendingOverrides } from './SettingsFields';
import { VersionHistory } from './VersionHistory';
import { GROUP_LABELS, INITIAL_NOTICES, INITIAL_SETTINGS, OVERRIDE_KEYS, SETTINGS_KEYS, diffSchedules, diffSettingValues, pickEffective } from './reminderSchedule';

const GENERAL = 'GENERAL';
const TABS = ['PROGRAMS', 'MONTHLY_COURSES', GENERAL];
const STEPS = [
  { key: 'changes', label: 'Cambios' },
  { key: 'confirm', label: 'Confirmar' },
];
const EMPTY_OVERRIDES = Object.fromEntries(OVERRIDE_KEYS.map((key) => [key, null]));
const EMPTY_SCHEDULE = { notices: [], sendingOverrides: EMPTY_OVERRIDES };

const showSimulatedMails = import.meta.env.DEV || import.meta.env.VITE_SHOW_SIMULATED_MAILS === 'true';

const INITIAL_SCHEDULE = { notices: INITIAL_NOTICES, sendingOverrides: EMPTY_OVERRIDES };

function normalizeSchedule(schedule, fallback = EMPTY_SCHEDULE) {
  if (!schedule) return fallback;
  return { notices: schedule.notices ?? [], sendingOverrides: { ...EMPTY_OVERRIDES, ...schedule.sendingOverrides } };
}

function PendingBanner({ roleBase }) {
  return (
    <div role="status" className="flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-warning-bg/70 px-4 py-3 text-sm text-ink">
      <span>Hay una propuesta pendiente de aprobación. Hasta que se resuelva, esta política queda en solo lectura.</span>
      <Link to={inboxPath(roleBase)} className="font-semibold text-primary hover:underline">
        Ver aprobaciones
      </Link>
    </div>
  );
}

function ResultView({ result, roleBase, onClose }) {
  return (
    <GlassCard className="p-6">
      <EmptyState
        icon={result.pending ? '📨' : '✅'}
        title={result.pending ? 'Propuesta enviada al director' : 'Cambio publicado'}
        description={
          result.pending
            ? `El cambio queda a la espera de su aprobación. Pedido de autorización n.° ${result.requestId}.`
            : 'La nueva configuración queda programada desde la fecha de vigencia.'
        }
        action={
          <div className="flex flex-wrap justify-center gap-2">
            {result.pending && (
              <Button variant="outline" asChild>
                <Link to={`${inboxPath(roleBase)}/${result.requestId}`}>Ver el pedido</Link>
              </Button>
            )}
            <Button variant="outline" onClick={onClose}>
              Volver a los recordatorios
            </Button>
          </div>
        }
      />
    </GlassCard>
  );
}

function ChangesBar({ count, onDiscard, onReview }) {
  return (
    <GlassCard className="sticky bottom-4 z-10 flex flex-wrap items-center justify-between gap-3 px-5 py-3">
      <span className="text-sm font-semibold text-ink">
        {count} {count === 1 ? 'cambio sin publicar' : 'cambios sin publicar'}
      </span>
      <div className="flex gap-2">
        <Button variant="ghost" onClick={onDiscard}>
          Descartar
        </Button>
        <Button variant="gradient" onClick={onReview}>
          Revisar cambios
        </Button>
      </div>
    </GlassCard>
  );
}

function SendingSettingsBlock({ overrides, general, readOnly, onChange }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="flex flex-col gap-3">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="flex items-center gap-2 font-heading text-base font-semibold text-ink"
      >
        Ajustes de envío
        <ChevronDown className={`size-4 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && <SendingOverrides overrides={overrides} general={general} disabled={readOnly} onChange={onChange} />}
    </div>
  );
}

export function ReminderSettingsPage() {
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const isDirector = roleBase === 'director';
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab');
  const tab = TABS.includes(requestedTab) ? requestedTab : TABS[0];
  const isGeneral = tab === GENERAL;

  const [exception, setException] = useState(null);
  const [drafts, setDrafts] = useState({});
  const [step, setStep] = useState('changes');
  const [removing, setRemoving] = useState(false);
  const [result, setResult] = useState(null);

  const policies = useReminderPolicies();
  const names = useOfferNames();
  const settingsVersions = useReminderSettingsVersions();
  const groupScope = isGeneral ? null : { group: tab, targetId: null };
  const exceptionScope = !isGeneral && exception && !exception.isNew ? { group: tab, targetId: exception.id } : null;
  const groupVersions = useReminderScheduleVersions(groupScope);
  const exceptionVersions = useReminderScheduleVersions(exceptionScope);

  const scope = isGeneral ? null : { group: tab, targetId: exception?.id ?? null };
  const draftKey = isGeneral ? GENERAL : `${tab}:${exception?.id ?? 'group'}`;

  const groupBase = normalizeSchedule(pickEffective(groupVersions.data)?.schedule, INITIAL_SCHEDULE);
  const baseSchedule = exception ? (exception.isNew ? EMPTY_SCHEDULE : normalizeSchedule(pickEffective(exceptionVersions.data)?.schedule)) : groupBase;
  const settingsBase = pickEffective(settingsVersions.data)?.values ?? INITIAL_SETTINGS;
  const base = isGeneral ? settingsBase : baseSchedule;
  const draft = drafts[draftKey] ?? base;

  const policy = useMemo(() => {
    if (isGeneral || !policies.data) return null;
    if (exception) return policies.data.exceptions.find((item) => item.group === tab && item.targetId === exception.id) ?? null;
    return policies.data.groups.find((item) => item.group === tab) ?? null;
  }, [policies.data, isGeneral, exception, tab]);
  const readOnly = Boolean(policy?.hasOpenProposal);

  const changeCount = !base || !draft ? 0 : isGeneral ? diffSettingValues(base, draft, SETTINGS_KEYS).length : diffSchedules(base, draft).length;
  const hasChanges = changeCount > 0 || removing;

  function setDraft(next) {
    setDrafts((current) => ({ ...current, [draftKey]: next }));
  }

  function discard() {
    setDrafts((current) => {
      const { [draftKey]: removed, ...rest } = current;
      void removed;
      return rest;
    });
    setRemoving(false);
    if (exception?.isNew) setException(null);
  }

  function changeTab(value) {
    setSearchParams(value === TABS[0] ? {} : { tab: value });
    setException(null);
    setRemoving(false);
  }

  function finish(outcome) {
    discard();
    setStep('changes');
    setResult(outcome);
    toast.success(outcome.pending ? 'Propuesta enviada al director.' : 'Cambio publicado.');
  }

  function startException(targetId) {
    setDrafts((current) => ({ ...current, [`${tab}:${targetId}`]: groupBase }));
    setException({ id: targetId, isNew: true });
  }

  const exceptionName = exception ? (tab === 'PROGRAMS' ? names.programName(exception.id) : names.courseName(exception.id)) : null;
  const groupExceptions = (policies.data?.exceptions ?? []).filter((item) => item.group === tab);
  const loading = policies.isLoading || settingsVersions.isLoading || (!isGeneral && (groupVersions.isLoading || exceptionVersions.isLoading));
  const failed = policies.isError || settingsVersions.isError || groupVersions.isError;

  if (result) {
    return (
      <PageContainer title="Recordatorios de pago">
        <ResultView result={result} roleBase={roleBase} onClose={() => setResult(null)} />
      </PageContainer>
    );
  }

  if (step === 'confirm') {
    const kind = isGeneral ? 'settings' : removing ? 'removal' : 'schedule';
    return (
      <PageContainer title="Recordatorios de pago">
        <Stepper pasos={STEPS} pasoActual="confirm" pasosVisitados={['changes', 'confirm']} onStepClick={(key) => key === 'changes' && setStep('changes')} />
        <ReminderConfirmStep
          kind={kind}
          scope={scope}
          base={base}
          draft={draft}
          isDirector={isDirector}
          onBack={() => setStep('changes')}
          onDone={finish}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="Recordatorios de pago" description="Cuándo y cómo le avisamos a cada alumno que tiene una cuota por pagar.">
      {hasChanges && <Stepper pasos={STEPS} pasoActual="changes" pasosVisitados={['changes']} />}
      <Tabs value={tab} onValueChange={changeTab}>
        <TabsList variant="segmented">
          <TabsTrigger value="PROGRAMS">Carreras</TabsTrigger>
          <TabsTrigger value="MONTHLY_COURSES">Cursos con pago mensual</TabsTrigger>
          <TabsTrigger value={GENERAL}>General</TabsTrigger>
        </TabsList>

        <div className="mt-5 flex flex-col gap-5">
          {loading ? (
            <Skeleton className="h-64 w-full rounded-3xl" aria-label="Cargando" />
          ) : failed ? (
            <GlassCard className="p-6">
              <EmptyState
                title="No pudimos cargar la configuración"
                action={
                  <Button variant="outline" onClick={() => [policies, settingsVersions, groupVersions].forEach((query) => query.refetch?.())}>
                    Reintentar
                  </Button>
                }
              />
            </GlassCard>
          ) : isGeneral ? (
            <TabsContent value={GENERAL} className="flex flex-col gap-5">
              <Tabs defaultValue="settings">
                <TabsList variant="segmented">
                  <TabsTrigger value="settings">Ajustes</TabsTrigger>
                  <TabsTrigger value="history">Historial</TabsTrigger>
                </TabsList>
                <TabsContent value="settings" className="mt-4">
                  <GlassCard className="p-5">
                    <GeneralSettingsForm values={draft ?? {}} onChange={(key, value) => setDraft({ ...draft, [key]: value })} />
                  </GlassCard>
                </TabsContent>
                <TabsContent value="history" className="mt-4">
                  <GlassCard className="p-0">
                    <VersionHistory versions={settingsVersions.data} />
                  </GlassCard>
                </TabsContent>
              </Tabs>
            </TabsContent>
          ) : (
            <TabsContent value={tab} className="flex flex-col gap-5">
              {exception && (
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <button type="button" onClick={() => { discard(); setException(null); }} className="text-sm font-semibold text-primary hover:underline">
                    ← Volver a {GROUP_LABELS[tab]}
                  </button>
                  <h2 className="font-heading text-lg font-semibold text-ink">{exceptionName}</h2>
                  {!exception.isNew && !readOnly && (
                    <Button variant="outline" size="sm" onClick={() => { setRemoving(true); setStep('confirm'); }}>
                      Quitar excepción
                    </Button>
                  )}
                </div>
              )}
              {readOnly && <PendingBanner roleBase={roleBase} />}
              <Tabs defaultValue="notices">
                <div className="flex flex-wrap items-center gap-3">
                  <TabsList variant="segmented">
                    <TabsTrigger value="notices">Avisos</TabsTrigger>
                    <TabsTrigger value="history">Historial</TabsTrigger>
                  </TabsList>
                  {readOnly && <StatusPill tone="warning">Pendiente de aprobación</StatusPill>}
                </div>
                <TabsContent value="notices" className="mt-4 flex flex-col gap-6">
                  <GlassCard className="flex flex-col gap-6 p-5">
                    <ReminderTimeline notices={draft.notices} readOnly={readOnly} onChange={(notices) => setDraft({ ...draft, notices })} />
                    <SendingSettingsBlock
                      overrides={draft.sendingOverrides}
                      general={settingsBase}
                      readOnly={readOnly}
                      onChange={(sendingOverrides) => setDraft({ ...draft, sendingOverrides })}
                    />
                  </GlassCard>
                  {!exception && (
                    <GlassCard className="p-5">
                      <ExceptionsTable
                        group={tab}
                        exceptions={groupExceptions}
                        names={names}
                        canAdd={!names.isLoading}
                        onSelect={(id) => setException({ id, isNew: false })}
                        onAdd={startException}
                      />
                    </GlassCard>
                  )}
                </TabsContent>
                <TabsContent value="history" className="mt-4">
                  <GlassCard className="p-0">
                    <VersionHistory
                      versions={(exception ? exceptionVersions : groupVersions).data}
                      isLoading={(exception ? exceptionVersions : groupVersions).isLoading}
                    />
                  </GlassCard>
                </TabsContent>
              </Tabs>
            </TabsContent>
          )}
          {hasChanges && !readOnly && <ChangesBar count={changeCount} onDiscard={discard} onReview={() => setStep('confirm')} />}
        </div>
      </Tabs>
      {showSimulatedMails && (
        <p className="text-xs text-ink-faint">
          <Link to={`/${roleBase}/recordatorios-de-pago/mails-simulados`} className="hover:underline">
            Mails simulados
          </Link>
        </p>
      )}
    </PageContainer>
  );
}
