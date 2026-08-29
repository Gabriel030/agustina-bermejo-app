import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { EmptyState } from '@/components/EmptyState';

// Pantalla de negocio real: fuera de alcance de esta fase (Épica 2/3/4 la
// implementan). Este placeholder deja las rutas navegables mientras tanto —
// es la pantalla que más se ve hoy (cubre casi todo el menú de cada rol), así
// que respeta el mismo ancho que Catálogo/Detalle/Inscripción en vez de
// estirarse a todo <main>.
export function Placeholder({ titulo }) {
  return (
    <PageContainer>
      <GlassCard className="flex flex-1 flex-col p-12">
        <EmptyState title={titulo} description="Esta pantalla todavía no está implementada — es parte de una fase posterior del roadmap." />
      </GlassCard>
    </PageContainer>
  );
}
