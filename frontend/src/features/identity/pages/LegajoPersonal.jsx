import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { useAuth } from '@/context/AuthContext';
import { useLegajoPersonal } from '@/features/identity/hooks/useLegajoPersonal';
import { useWithdrawPersonal, useReactivarPersonal, useResetPersonalPassword, useEditarPersonal } from '@/features/identity/hooks/useAccionesPersonal';
import { DarDeBajaPersonalModal } from '@/features/identity/components/DarDeBajaPersonalModal';
import { ReactivarPersonalModal } from '@/features/identity/components/ReactivarPersonalModal';
import { ResetPasswordModal } from '@/features/identity/components/ResetPasswordModal';
import { EditarPersonalModal } from '@/features/identity/components/EditarPersonalModal';
import { canManageStaffRole } from '@/features/identity/staffRoles';

function staffActionErrorMessage(err, fallback) {
  if (err?.status === 403) return 'No tenés permiso para gestionar a esta persona.';
  return fallback;
}

const ETIQUETA_ROL = {
  DOCENTE: 'Docente',
  PRECEPTOR: 'Preceptor',
  ADMINISTRATIVO: 'Administrativo',
};

const ESTADO_BADGE = {
  ACTIVE: { label: 'Activo', tone: 'success' },
  INACTIVE: { label: 'Inactivo', tone: 'neutral' },
};

function Campo({ label, value }) {
  if (!value) return null;
  return (
    <div className="flex flex-col gap-1">
      <span className="text-[11px] font-medium uppercase tracking-wider text-ink-faint">{label}</span>
      <Input value={value} readOnly className="cursor-default" />
    </div>
  );
}

export function LegajoPersonal() {
  const { id } = useParams();
  const { usuario } = useAuth();
  const { data: legajo, isLoading, isError } = useLegajoPersonal(id);

  const [bajaOpen, setBajaOpen] = useState(false);
  const [reactivarOpen, setReactivarOpen] = useState(false);
  const [resetOpen, setResetOpen] = useState(false);
  const [editarOpen, setEditarOpen] = useState(false);

  const puedeAccionar = !!legajo && canManageStaffRole(usuario?.roles, legajo.role);

  const withdrawMutation = useWithdrawPersonal(id);
  const reactivarMutation = useReactivarPersonal(id);
  const resetMutation = useResetPersonalPassword(id);
  const editarMutation = useEditarPersonal(id);

  const badge = legajo ? ESTADO_BADGE[legajo.status] : null;
  const nombre = legajo ? `${legajo.apellido}, ${legajo.nombre}` : '';
  const puedeGestionarBaja = puedeAccionar && legajo?.status === 'ACTIVE';
  const puedeReactivar = puedeAccionar && legajo?.status === 'INACTIVE';

  return (
    <PageContainer>
      <div className="flex flex-col gap-4">
        <GlassCard className="flex flex-col gap-4 p-5">
          {isLoading ? (
            <Skeleton className="h-10 w-64" />
          ) : isError ? (
            <p className="text-[14px] text-ink-faint">No se pudo cargar el legajo.</p>
          ) : (
            <>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div>
                    <h1 className="font-heading text-xl font-bold text-ink">
                      {legajo.apellido}, {legajo.nombre}
                    </h1>
                    <p className="text-[13px] text-ink-faint">
                      {ETIQUETA_ROL[legajo.role] ?? legajo.role} · DNI {legajo.dni}
                    </p>
                  </div>
                  {badge && (
                    <StatusPill tone={badge.tone}>{badge.label}</StatusPill>
                  )}
                </div>
                {puedeAccionar && (
                  <Button variant="outline" size="sm" onClick={() => setEditarOpen(true)}>
                    Editar datos
                  </Button>
                )}
              </div>

              <div className="flex flex-col gap-6">
                <div>
                  <h3 className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Datos personales</h3>
                  <div className="grid grid-cols-2 gap-3">
                    <Campo label="Nombre" value={legajo.nombre} />
                    <Campo label="Apellido" value={legajo.apellido} />
                    <Campo label="DNI" value={legajo.dni} />
                    <Campo label="Usuario" value={legajo.username} />
                    <Campo label="Email" value={legajo.email} />
                    <Campo label="Teléfono" value={legajo.telefono} />
                  </div>
                </div>

                {legajo.domicilio && (legajo.domicilio.street || legajo.domicilio.city) && (
                  <div>
                    <h3 className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Domicilio</h3>
                    <div className="grid grid-cols-2 gap-3">
                      <Campo label="Calle" value={legajo.domicilio.street} />
                      <Campo label="Número" value={legajo.domicilio.number} />
                      <Campo label="Piso" value={legajo.domicilio.floor} />
                      <Campo label="Ciudad" value={legajo.domicilio.city} />
                      <Campo label="Provincia" value={legajo.domicilio.state} />
                      <Campo label="Código postal" value={legajo.domicilio.postalCode} />
                    </div>
                  </div>
                )}

                {puedeAccionar && (
                  <div className="flex flex-col gap-4">
                    <h3 className="text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Acciones</h3>
                    <div className="flex flex-col divide-y divide-border rounded-lg border border-border">
                      <div className="flex items-center justify-between gap-4 px-4 py-3">
                        <div className="flex flex-col gap-0.5">
                          <span className="text-[14px] font-medium text-ink">Resetear contraseña</span>
                          <span className="text-[12px] text-ink-faint">Envía un link de recuperación al email del personal.</span>
                        </div>
                        <Button variant="gradient" size="sm" onClick={() => setResetOpen(true)}>
                          Resetear contraseña
                        </Button>
                      </div>

                      {puedeGestionarBaja && (
                        <div className="flex items-center justify-between gap-4 px-4 py-3">
                          <div className="flex flex-col gap-0.5">
                            <span className="text-[14px] font-medium text-ink">Dar de baja</span>
                            <span className="text-[12px] text-ink-faint">Marca al personal como inactivo. Puede reactivarse más adelante.</span>
                          </div>
                          <Button variant="destructive" size="sm" onClick={() => setBajaOpen(true)}>
                            Dar de baja
                          </Button>
                        </div>
                      )}

                      {puedeReactivar && (
                        <div className="flex items-center justify-between gap-4 px-4 py-3">
                          <div className="flex flex-col gap-0.5">
                            <span className="text-[14px] font-medium text-ink">Reactivar</span>
                            <span className="text-[12px] text-ink-faint">Restaura el acceso al sistema para este miembro del personal.</span>
                          </div>
                          <Button variant="gradient" size="sm" onClick={() => setReactivarOpen(true)}>
                            Reactivar
                          </Button>
                        </div>
                      )}
                    </div>
                  </div>
                )}

                <div>
                  <h3 className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Comisiones</h3>
                  <p className="text-[13px] text-ink-faint">Disponible en la Épica 10 — Gestión académica.</p>
                </div>
              </div>
            </>
          )}
        </GlassCard>
      </div>

      <DarDeBajaPersonalModal
        open={bajaOpen}
        onClose={() => setBajaOpen(false)}
        nombre={nombre}
        onConfirmar={() =>
          withdrawMutation.mutate(undefined, {
            onSuccess: () => {
              setBajaOpen(false);
              toast.success(`${nombre} quedó dado de baja`);
            },
            onError: (err) => toast.error(staffActionErrorMessage(err, 'No se pudo dar de baja. Intentá de nuevo.')),
          })
        }
        isPending={withdrawMutation.isPending}
      />

      <ReactivarPersonalModal
        open={reactivarOpen}
        onClose={() => setReactivarOpen(false)}
        nombre={nombre}
        onConfirmar={() =>
          reactivarMutation.mutate(undefined, {
            onSuccess: () => {
              setReactivarOpen(false);
              toast.success(`${nombre} fue reactivado`);
            },
            onError: (err) => toast.error(staffActionErrorMessage(err, 'No se pudo reactivar. Intentá de nuevo.')),
          })
        }
        isPending={reactivarMutation.isPending}
      />

      <ResetPasswordModal
        open={resetOpen}
        onClose={() => setResetOpen(false)}
        email={legajo?.email}
        onConfirmar={() =>
          resetMutation.mutateAsync().catch((err) => {
            toast.error(staffActionErrorMessage(err, 'No se pudo enviar el link. Intentá de nuevo.'));
            throw err;
          })
        }
        isPending={resetMutation.isPending}
      />

      <EditarPersonalModal
        open={editarOpen}
        onClose={() => setEditarOpen(false)}
        legajo={legajo}
        onConfirmar={(data) =>
          editarMutation.mutate(data, {
            onSuccess: () => {
              setEditarOpen(false);
              toast.success('Datos de contacto actualizados');
            },
            onError: (err) =>
              toast.error(
                err?.status === 400
                  ? 'Revisá los datos: el email no es válido.'
                  : staffActionErrorMessage(err, 'No se pudieron guardar los cambios. Intentá de nuevo.'),
              ),
          })
        }
        isPending={editarMutation.isPending}
      />
    </PageContainer>
  );
}
