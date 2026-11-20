import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { ThemeToggle } from '@/components/ThemeToggle';
import { useAuth } from '@/context/AuthContext';
import { ContactConsentMatrix } from '@/features/identity/components/ContactConsentMatrix';
import { NotificationMatrix } from '@/features/identity/components/NotificationMatrix';
import { TabSeguridad } from '@/features/identity/pages/MiPerfil';

function OptionsSection({ id, title, children }) {
  return (
    <section aria-labelledby={id}>
      <GlassCard className="flex flex-col gap-5 p-5 sm:p-7">
        <h2 id={id} className="font-heading text-lg font-semibold text-ink">
          {title}
        </h2>
        {children}
      </GlassCard>
    </section>
  );
}

export function StudentOptions() {
  const { logout } = useAuth();
  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">Opciones</h1>
        <p className="mt-1 text-size-13 text-ink-faint">Apariencia, notificaciones y seguridad de tu cuenta.</p>
      </div>

      <OptionsSection id="opciones-apariencia" title="Apariencia">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-size-14 font-medium text-ink">Modo oscuro</p>
            <p className="text-size-13 text-ink-faint">Cambia entre el tema claro y el oscuro.</p>
          </div>
          <ThemeToggle />
        </div>
      </OptionsSection>

      <OptionsSection id="opciones-notificaciones" title="Notificaciones">
        <NotificationMatrix />
        <ContactConsentMatrix />
      </OptionsSection>

      <OptionsSection id="opciones-seguridad" title="Seguridad">
        <TabSeguridad />
      </OptionsSection>

      <OptionsSection id="opciones-sesion" title="Sesión">
        <div className="flex justify-end">
          <Button variant="destructive" onClick={() => logout()}>
            Cerrar sesión
          </Button>
        </div>
      </OptionsSection>
    </PageContainer>
  );
}
