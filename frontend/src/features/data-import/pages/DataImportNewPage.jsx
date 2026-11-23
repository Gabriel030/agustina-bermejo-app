import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useCreateDataImport } from '../hooks/useDataImports';

const LIST_PATH = '/director/importacion-inicial';
const MAX_NAME_LENGTH = 120;

export function DataImportNewPage() {
  const navigate = useNavigate();
  const create = useCreateDataImport();
  const [name, setName] = useState('');
  const trimmed = name.trim();

  async function handleSubmit(event) {
    event.preventDefault();
    if (!trimmed) return;
    try {
      const batch = await create.mutateAsync(trimmed);
      navigate(`${LIST_PATH}/${batch.id}`);
    } catch (error) {
      toast.error(error.message || 'No pudimos crear la importación. Probá de nuevo.');
    }
  }

  return (
    <PageContainer title="Nueva importación" description="Ponele un nombre para reconocerla después, por ejemplo «Carga inicial 2026».">
      <Link to={LIST_PATH} className="w-fit text-sm font-semibold text-primary hover:underline">
        Volver a Importación inicial
      </Link>
      <GlassCard className="max-w-xl p-5">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="import-name" className="text-sm font-medium text-ink">
              Nombre
            </label>
            <Input id="import-name" value={name} maxLength={MAX_NAME_LENGTH} autoFocus onChange={(event) => setName(event.target.value)} />
          </div>
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="outline" asChild>
              <Link to={LIST_PATH}>Cancelar</Link>
            </Button>
            <Button className="min-h-11" type="submit" disabled={!trimmed} loading={create.isPending}>
              Crear importación
            </Button>
          </div>
        </form>
      </GlassCard>
    </PageContainer>
  );
}
