import { useNavigate } from 'react-router-dom';
import { useRoleBasePath } from '@/features/academic-structure/hooks/useRoleBasePath';
import { Pencil } from 'lucide-react';
import { Button } from '@/components/ui/button';

const RESUME_LABELS = {
  DRAFT: 'Continuar armado',
  REJECTED: 'Corregir y reenviar',
};

export function ResumeCycleButton({ cycle }) {
  const navigate = useNavigate();
  const basePath = useRoleBasePath();
  const label = RESUME_LABELS[cycle.status];
  if (!label) return null;

  return (
    <Button
      variant="outline"
      size="sm"
      onClick={() => navigate(`${basePath}/oferta-academica/nuevo-ciclo?cicloId=${cycle.id}`)}
    >
      <Pencil className="mr-1.5 h-3.5 w-3.5" />
      {label}
    </Button>
  );
}
