import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { Button } from '@/components/ui/button';

export function NoAutorizado() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <MeshBackground />
      <GlassCard className="flex w-[420px] flex-col items-center gap-3 p-10 text-center">
        <div className="font-heading text-xl font-bold text-ink">No autorizado</div>
        <div className="text-sm text-ink-faint">Tu usuario no tiene permiso para acceder a esta sección.</div>
        <Button asChild className="mt-3 rounded-2xl">
          <Link to="/login">Volver a iniciar sesión</Link>
        </Button>
      </GlassCard>
    </div>
  );
}
