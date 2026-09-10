import { useNavigate } from 'react-router-dom';
import { UserPlus, MailCheck, MailX, RefreshCw } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { useProspectosList, useReenviarVerificacion } from '@/features/gestion-personas/hooks/useProspectos';

export function PadronAlumnos() {
  const navigate = useNavigate();
  const { usuario } = useAuth();
  const rol = usuario?.roles[0]?.toLowerCase();
  const { data: alumnos, isLoading } = useProspectosList();

  function handleNuevoAlumno() {
    navigate(`/${rol}/alumnos/nuevo`);
  }

  return (
    <PageContainer>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-ink">Padrón de alumnos</h1>
          <p className="mt-0.5 text-sm text-ink-soft">Alumnos y prospectos registrados en el sistema</p>
        </div>
        <Button onClick={handleNuevoAlumno} className="flex items-center gap-2">
          <UserPlus size={16} />
          Nuevo alumno
        </Button>
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-14" />
          <Skeleton className="h-14" />
          <Skeleton className="h-14" />
        </div>
      ) : (alumnos ?? []).length === 0 ? (
        <GlassCard className="p-4">
          <EmptyState
            title="Sin alumnos registrados"
            description="Usá el botón Nuevo alumno para registrar el primero."
          />
        </GlassCard>
      ) : (
        <div className="flex flex-col gap-2">
          {(alumnos ?? []).map((alumno) => (
            <AlumnoRow key={alumno.id} alumno={alumno} />
          ))}
        </div>
      )}
    </PageContainer>
  );
}

function AlumnoRow({ alumno }) {
  const reenviar = useReenviarVerificacion();
  const emailVerificado = alumno.emailVerified === true;
  const esProspecto = !emailVerificado;

  return (
    <GlassCard className="flex items-center justify-between gap-4 px-4 py-3">
      <div className="flex items-center gap-3 min-w-0">
        <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand to-accent-2 text-[12px] font-bold text-white uppercase">
          {alumno.firstName?.[0]}{alumno.lastName?.[0]}
        </div>
        <div className="min-w-0">
          <p className="text-[14px] font-semibold text-ink truncate">
            {alumno.firstName} {alumno.lastName}
          </p>
          <p className="text-[12px] text-ink-soft truncate">{alumno.email}</p>
        </div>
      </div>

      <div className="flex items-center gap-3 flex-shrink-0">
        {esProspecto ? (
          <>
            <div className="flex items-center gap-1.5 text-[12px] text-amber-600">
              <MailX size={14} />
              <span className="hidden sm:inline">Email sin verificar</span>
            </div>
            <Button
              variant="ghost"
              size="sm"
              className="h-7 gap-1.5 text-[12px]"
              disabled={reenviar.isPending}
              onClick={() => reenviar.mutate(alumno.id)}
            >
              <RefreshCw size={12} />
              Reenviar
            </Button>
          </>
        ) : (
          <div className="flex items-center gap-1.5 text-[12px] text-green-600">
            <MailCheck size={14} />
            <span className="hidden sm:inline">Verificado</span>
          </div>
        )}
      </div>
    </GlassCard>
  );
}
