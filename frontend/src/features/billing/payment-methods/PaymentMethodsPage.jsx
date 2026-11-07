import { useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAuth } from '@/context/AuthContext';
import { PaymentMethodsAuditTab } from './PaymentMethodsAuditTab';
import { PaymentMethodsTab } from './PaymentMethodsTab';
import { SettingChangeDialog } from './SettingChangeDialog';
import { toSettingEntries } from './paymentMethods';
import { useInstituteSettings } from './useInstituteSettings';

function LoadingSettings() {
  return (
    <GlassCard className="flex flex-col gap-5 p-5" aria-busy="true" aria-label="Cargando medios de pago">
      {[0, 1, 2, 3].map((item) => (
        <div key={item} className="flex items-center gap-3">
          <Skeleton className="size-10" />
          <Skeleton className="h-5 flex-1" />
          <Skeleton className="h-6 w-10" />
        </div>
      ))}
    </GlassCard>
  );
}

function SettingsContent({ settings, isDirector, onRefresh }) {
  const [tab, setTab] = useState('methods');
  const [editingKey, setEditingKey] = useState(null);
  const entries = toSettingEntries(settings);
  const editing = editingKey ? [...entries.methods, entries.notifications].find((entry) => entry.key === editingKey) : null;

  return (
    <Tabs value={tab} onValueChange={setTab} className="gap-4">
      {isDirector && (
        <TabsList variant="segmented">
          <TabsTrigger value="methods">Medios</TabsTrigger>
          <TabsTrigger value="audit">Auditoría</TabsTrigger>
        </TabsList>
      )}
      <TabsContent value="methods" className="flex flex-col gap-4">
        <PaymentMethodsTab entries={entries} readOnly={!isDirector} onToggle={setEditingKey} />
      </TabsContent>
      {isDirector && (
        <TabsContent value="audit">
          <PaymentMethodsAuditTab />
        </TabsContent>
      )}
      {editing && <SettingChangeDialog key={editing.key} entry={editing} onClose={() => setEditingKey(null)} onRefresh={onRefresh} />}
    </Tabs>
  );
}

export function PaymentMethodsPage() {
  const { usuario } = useAuth();
  const query = useInstituteSettings();
  const isDirector = usuario?.roles?.includes('DIRECTOR') === true;

  return (
    <PageContainer title="Medios de pago" description="Qué medios usa el instituto para cobrar.">
      {query.isPending && <LoadingSettings />}
      {query.isError && <LoadErrorState error={query.error} onRetry={() => query.refetch()} resource="los medios de pago" />}
      {query.data && <SettingsContent settings={query.data} isDirector={isDirector} onRefresh={() => query.refetch()} />}
    </PageContainer>
  );
}
