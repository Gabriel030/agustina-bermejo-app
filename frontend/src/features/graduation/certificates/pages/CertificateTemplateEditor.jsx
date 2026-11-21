import { useState } from 'react';
import { PageContainer } from '@/components/PageContainer';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAuth } from '@/context/AuthContext';
import { TemplatePanel } from '@/features/graduation/certificates/components/TemplatePanel';
import { TEMPLATE_KINDS } from '@/features/graduation/certificates/lib/certificateTemplate';

export function CertificateTemplateEditor() {
  const { usuario } = useAuth();
  const readOnly = !(usuario?.roles ?? []).includes('DIRECTOR');
  const [kind, setKind] = useState('PROGRAM');
  const [edits, setEdits] = useState({});

  function setEdit(targetKind, form) {
    setEdits((current) => ({ ...current, [targetKind]: form }));
  }

  return (
    <PageContainer title="Plantilla de certificados" description="Logo, textos fijos y firmantes del certificado de egreso">
      <Tabs value={kind} onValueChange={setKind} className="gap-4">
        <TabsList variant="segmented">
          {TEMPLATE_KINDS.map((tab) => (
            <TabsTrigger key={tab.value} value={tab.value}>
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>
        {TEMPLATE_KINDS.map((tab) => (
          <TabsContent key={tab.value} value={tab.value} className="mt-0">
            <TemplatePanel
              kind={tab.value}
              documentName={tab.documentName}
              readOnly={readOnly}
              edit={edits[tab.value] ?? null}
              onEdit={(form) => setEdit(tab.value, form)}
              onSettled={() => setEdit(tab.value, null)}
            />
          </TabsContent>
        ))}
      </Tabs>
    </PageContainer>
  );
}
