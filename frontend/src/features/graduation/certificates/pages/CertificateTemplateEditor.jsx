import { useState } from 'react';
import { PageContainer } from '@/components/PageContainer';
import { Label } from '@/components/ui/label';
import { NativeSelect, NativeSelectOptGroup } from '@/components/ui/native-select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAuth } from '@/context/AuthContext';
import { TemplatePanel } from '@/features/graduation/certificates/components/TemplatePanel';
import { TEMPLATE_GROUPS, TEMPLATE_TABS } from '@/features/graduation/certificates/lib/certificateTemplate';

export function CertificateTemplateEditor() {
  const { usuario } = useAuth();
  const readOnly = !(usuario?.roles ?? []).includes('DIRECTOR');
  const [templateKey, setTemplateKey] = useState('PROGRAM');
  const [edits, setEdits] = useState({});

  function setEdit(targetKey, form) {
    setEdits((current) => ({ ...current, [targetKey]: form }));
  }

  return (
    <PageContainer title="Plantillas de certificados" description="Logo, textos y firmantes de cada documento">
      <Tabs value={templateKey} onValueChange={setTemplateKey} className="gap-4">
        <TabsList variant="segmented" aria-label="Documentos" className="hidden max-w-full md:inline-flex">
          {TEMPLATE_TABS.map((tab) => (
            <TabsTrigger key={tab.value} value={tab.value}>
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>
        <div className="flex flex-col gap-1.5 md:hidden">
          <Label htmlFor="template-document">Documento</Label>
          <NativeSelect
            id="template-document"
            className="[&_select]:min-h-11"
            value={templateKey}
            onChange={(event) => setTemplateKey(event.target.value)}
          >
            {TEMPLATE_GROUPS.map((group) => (
              <NativeSelectOptGroup key={group.value} label={group.label}>
                {TEMPLATE_TABS.filter((tab) => tab.group === group.value).map((tab) => (
                  <option key={tab.value} value={tab.value}>
                    {tab.label}
                  </option>
                ))}
              </NativeSelectOptGroup>
            ))}
          </NativeSelect>
        </div>
        {TEMPLATE_TABS.map((tab) => (
          <TabsContent key={tab.value} value={tab.value} className="mt-0">
            <TemplatePanel
              templateKey={tab.value}
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
