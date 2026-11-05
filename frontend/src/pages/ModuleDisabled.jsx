import { Link } from "react-router-dom";
import { PageContainer } from "@/components/PageContainer";
import { GlassCard } from "@/components/GlassCard";
import { EmptyState } from "@/components/EmptyState";
import { Button } from "@/components/ui/button";

export function ModuleDisabled({ role }) {
  return (
    <PageContainer>
      <GlassCard className="flex flex-1 flex-col p-12">
        <EmptyState
          icon="🔒"
          title="Este módulo no está habilitado en tu instituto"
          description="Si lo necesitás, consultalo con quien administra tu instituto."
          action={
            <Button asChild variant="outline">
              <Link to={`/${role}/inicio`}>Volver a Inicio</Link>
            </Button>
          }
        />
      </GlassCard>
    </PageContainer>
  );
}
