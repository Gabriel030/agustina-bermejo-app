import { useState } from 'react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/Skeleton';
import {
  usePerfilPropio,
  useActualizarPerfil,
  useCambiarContrasena,
  useSesionesActivas,
  useRevocarTodasLasSesiones,
  usePreferenciasNotificaciones,
  useActualizarPreferenciasNotificaciones,
} from '@/features/gestion-personas/hooks/useMiPerfil';

const ETIQUETAS_EVENTO = {
  ACCOUNT_CREATED: 'Cuenta creada',
  SUBJECT_ENROLLMENT: 'Inscripción a materia',
  GRADE_POSTED: 'Nota publicada',
  INVOICE_GENERATED: 'Factura generada',
  PAYMENT_DUE_SOON: 'Vencimiento próximo',
  EXAM_BOARD_REGISTERED: 'Mesa de examen registrada',
  INSTITUTIONAL_NOTICE: 'Aviso institucional',
};

const ETIQUETAS_CANAL = {
  APP: 'En la app',
  EMAIL: 'Por email',
};

function CampoFormulario({ label, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[13px] font-medium text-ink-soft">{label}</label>
      {children}
    </div>
  );
}

function TabDatosPropios() {
  const { data: perfil, isLoading } = usePerfilPropio();
  const actualizar = useActualizarPerfil();

  const [form, setForm] = useState(null);
  const [guardado, setGuardado] = useState(false);
  const [error, setError] = useState('');

  const valores = form ?? perfil ?? {};

  function set(campo, valor) {
    setGuardado(false);
    setError('');
    setForm((prev) => ({ ...(prev ?? perfil ?? {}), [campo]: valor }));
  }

  async function guardar(e) {
    e.preventDefault();
    setError('');
    try {
      await actualizar.mutateAsync({
        email: valores.email,
        telefono: valores.telefono,
        sexo: valores.sexo,
        dni: valores.dni,
        street: valores.street,
        number: valores.number,
        floor: valores.floor,
        city: valores.city,
        state: valores.state,
        postalCode: valores.postalCode,
        country: valores.country,
      });
      setForm(null);
      setGuardado(true);
    } catch (err) {
      setError(err.message ?? 'No se pudo guardar.');
    }
  }

  if (isLoading) {
    return (
      <div className="flex flex-col gap-3">
        {[...Array(6)].map((_, i) => (
          <Skeleton key={i} className="h-10 w-full" />
        ))}
      </div>
    );
  }

  return (
    <form onSubmit={guardar} className="flex flex-col gap-5">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <CampoFormulario label="Nombre">
          <Input value={valores.nombre ?? ''} disabled className="bg-neutral-bg/50" />
        </CampoFormulario>
        <CampoFormulario label="Apellido">
          <Input value={valores.apellido ?? ''} disabled className="bg-neutral-bg/50" />
        </CampoFormulario>
        <CampoFormulario label="DNI">
          <Input
            value={valores.dni ?? ''}
            onChange={(e) => set('dni', e.target.value)}
            placeholder="Número de DNI"
          />
        </CampoFormulario>
        <CampoFormulario label="Sexo">
          <Select value={valores.sexo ?? ''} onValueChange={(v) => set('sexo', v)}>
            <SelectTrigger>
              <SelectValue placeholder="Seleccioná" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="M">Masculino</SelectItem>
              <SelectItem value="F">Femenino</SelectItem>
              <SelectItem value="X">No binario / otro</SelectItem>
            </SelectContent>
          </Select>
        </CampoFormulario>
        <CampoFormulario label="Email">
          <Input
            type="email"
            value={valores.email ?? ''}
            onChange={(e) => set('email', e.target.value)}
            placeholder="tu@email.com"
          />
        </CampoFormulario>
        <CampoFormulario label="Teléfono">
          <Input
            value={valores.telefono ?? ''}
            onChange={(e) => set('telefono', e.target.value)}
            placeholder="+54 11 ..."
          />
        </CampoFormulario>
      </div>

      <div className="border-t border-border pt-4">
        <p className="mb-3 text-[13px] font-medium text-ink-soft">Domicilio</p>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <CampoFormulario label="Calle">
            <Input
              value={valores.street ?? ''}
              onChange={(e) => set('street', e.target.value)}
              placeholder="Av. Ejemplo"
            />
          </CampoFormulario>
          <div className="grid grid-cols-2 gap-4">
            <CampoFormulario label="Número">
              <Input
                value={valores.number ?? ''}
                onChange={(e) => set('number', e.target.value)}
                placeholder="123"
              />
            </CampoFormulario>
            <CampoFormulario label="Piso / Dpto">
              <Input
                value={valores.floor ?? ''}
                onChange={(e) => set('floor', e.target.value)}
                placeholder="2B"
              />
            </CampoFormulario>
          </div>
          <CampoFormulario label="Ciudad">
            <Input
              value={valores.city ?? ''}
              onChange={(e) => set('city', e.target.value)}
              placeholder="Buenos Aires"
            />
          </CampoFormulario>
          <CampoFormulario label="Provincia">
            <Input
              value={valores.state ?? ''}
              onChange={(e) => set('state', e.target.value)}
              placeholder="CABA"
            />
          </CampoFormulario>
          <CampoFormulario label="Código postal">
            <Input
              value={valores.postalCode ?? ''}
              onChange={(e) => set('postalCode', e.target.value)}
              placeholder="1234"
            />
          </CampoFormulario>
          <CampoFormulario label="País">
            <Input
              value={valores.country ?? ''}
              onChange={(e) => set('country', e.target.value)}
              placeholder="Argentina"
            />
          </CampoFormulario>
        </div>
      </div>

      {error && <p className="text-[13px] text-danger">{error}</p>}
      {guardado && <p className="text-[13px] text-success">Datos guardados.</p>}

      <div className="flex justify-end">
        <Button type="submit" disabled={actualizar.isPending}>
          {actualizar.isPending ? 'Guardando...' : 'Guardar cambios'}
        </Button>
      </div>
    </form>
  );
}

function TabSeguridad() {
  const { data: sesiones, isLoading: cargandoSesiones } = useSesionesActivas();
  const revocar = useRevocarTodasLasSesiones();
  const cambiarContrasena = useCambiarContrasena();

  const [pass, setPass] = useState({ actual: '', nueva: '', confirmar: '' });
  const [errorPass, setErrorPass] = useState('');
  const [passGuardada, setPassGuardada] = useState(false);
  const [errorSesiones, setErrorSesiones] = useState('');
  const [sesionesRevocadas, setSesionesRevocadas] = useState(false);

  async function guardarContrasena(e) {
    e.preventDefault();
    setErrorPass('');
    setPassGuardada(false);
    if (pass.nueva !== pass.confirmar) {
      setErrorPass('Las contraseñas nuevas no coinciden.');
      return;
    }
    try {
      await cambiarContrasena.mutateAsync({ currentPassword: pass.actual, newPassword: pass.nueva });
      setPass({ actual: '', nueva: '', confirmar: '' });
      setPassGuardada(true);
    } catch (err) {
      setErrorPass(err.message ?? 'No se pudo cambiar la contraseña.');
    }
  }

  async function cerrarTodasLasSesiones() {
    setErrorSesiones('');
    setSesionesRevocadas(false);
    try {
      await revocar.mutateAsync();
      setSesionesRevocadas(true);
    } catch (err) {
      setErrorSesiones(err.message ?? 'No se pudieron cerrar las sesiones.');
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-4">
        <h3 className="text-[14px] font-semibold text-ink">Cambiar contraseña</h3>
        <form onSubmit={guardarContrasena} className="flex flex-col gap-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <CampoFormulario label="Contraseña actual">
              <Input
                type="password"
                value={pass.actual}
                onChange={(e) => setPass((p) => ({ ...p, actual: e.target.value }))}
                required
              />
            </CampoFormulario>
            <div />
            <CampoFormulario label="Nueva contraseña">
              <Input
                type="password"
                value={pass.nueva}
                onChange={(e) => setPass((p) => ({ ...p, nueva: e.target.value }))}
                required
              />
            </CampoFormulario>
            <CampoFormulario label="Confirmar nueva contraseña">
              <Input
                type="password"
                value={pass.confirmar}
                onChange={(e) => setPass((p) => ({ ...p, confirmar: e.target.value }))}
                required
              />
            </CampoFormulario>
          </div>
          {errorPass && <p className="text-[13px] text-danger">{errorPass}</p>}
          {passGuardada && <p className="text-[13px] text-success">Contraseña actualizada.</p>}
          <div className="flex justify-end">
            <Button type="submit" disabled={cambiarContrasena.isPending}>
              {cambiarContrasena.isPending ? 'Guardando...' : 'Cambiar contraseña'}
            </Button>
          </div>
        </form>
      </div>

      <div className="border-t border-border pt-6">
        <h3 className="mb-3 text-[14px] font-semibold text-ink">Sesiones activas</h3>
        {cargandoSesiones ? (
          <div className="flex flex-col gap-2">
            {[...Array(2)].map((_, i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : sesiones?.length ? (
          <div className="flex flex-col gap-2">
            {sesiones.map((s) => (
              <div key={s.id} className="flex items-center justify-between rounded-lg border border-border px-4 py-2.5">
                <div>
                  <div className="text-[13px] font-medium text-ink">
                    Sesión #{s.id}
                  </div>
                  <div className="text-[11px] text-ink-faint">
                    Iniciada: {new Date(s.createdAt).toLocaleString('es-AR')}
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-[13px] text-ink-faint">No hay sesiones activas.</p>
        )}
        {errorSesiones && <p className="mt-2 text-[13px] text-danger">{errorSesiones}</p>}
        {sesionesRevocadas && <p className="mt-2 text-[13px] text-success">Todas las sesiones fueron cerradas.</p>}
        <div className="mt-4 flex justify-end">
          <Button
            variant="outline"
            onClick={cerrarTodasLasSesiones}
            disabled={revocar.isPending}
            className="text-danger hover:border-danger hover:text-danger"
          >
            {revocar.isPending ? 'Cerrando...' : 'Cerrar todas las sesiones'}
          </Button>
        </div>
      </div>
    </div>
  );
}

function TabNotificaciones() {
  const { data, isLoading } = usePreferenciasNotificaciones();
  const actualizar = useActualizarPreferenciasNotificaciones();
  const [guardado, setGuardado] = useState(false);
  const [error, setError] = useState('');

  async function togglePref(evento, canal, valorActual) {
    setGuardado(false);
    setError('');
    const nuevoValor = !valorActual;
    try {
      await actualizar.mutateAsync({
        preferences: { [evento]: { [canal]: nuevoValor } },
      });
      setGuardado(true);
    } catch (err) {
      setError(err.message ?? 'No se pudo guardar.');
    }
  }

  if (isLoading) {
    return (
      <div className="flex flex-col gap-2">
        {[...Array(7)].map((_, i) => (
          <Skeleton key={i} className="h-10 w-full" />
        ))}
      </div>
    );
  }

  const prefs = data?.preferences ?? {};
  const eventos = Object.keys(ETIQUETAS_EVENTO);
  const canales = Object.keys(ETIQUETAS_CANAL);

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[400px] text-[13px]">
          <thead>
            <tr className="border-b border-border">
              <th className="pb-2 text-left font-medium text-ink-soft">Notificación</th>
              {canales.map((canal) => (
                <th key={canal} className="pb-2 text-center font-medium text-ink-soft">
                  {ETIQUETAS_CANAL[canal]}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {eventos.map((evento) => (
              <tr key={evento}>
                <td className="py-3 pr-4 text-ink">{ETIQUETAS_EVENTO[evento]}</td>
                {canales.map((canal) => {
                  const activo = prefs[evento]?.[canal] ?? true;
                  return (
                    <td key={canal} className="py-3 text-center">
                      <button
                        type="button"
                        onClick={() => togglePref(evento, canal, activo)}
                        disabled={actualizar.isPending}
                        aria-label={`${ETIQUETAS_EVENTO[evento]} — ${ETIQUETAS_CANAL[canal]}: ${activo ? 'activado' : 'desactivado'}`}
                        className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                          activo ? 'bg-gradient-to-br from-primary to-brand' : 'bg-neutral-bg'
                        }`}
                      >
                        <span
                          className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow-lg ring-0 transition-transform ${
                            activo ? 'translate-x-4' : 'translate-x-0'
                          }`}
                        />
                      </button>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {error && <p className="text-[13px] text-danger">{error}</p>}
      {guardado && <p className="text-[13px] text-success">Preferencia guardada.</p>}
    </div>
  );
}

export function MiPerfil() {
  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">Mi perfil</h1>
        <p className="mt-1 text-[13px] text-ink-faint">Administrá tus datos personales, seguridad y notificaciones.</p>
      </div>

      <GlassCard className="flex flex-col gap-6 p-5 sm:p-7">
        <Tabs defaultValue="datos">
          <TabsList variant="line" className="mb-6">
            <TabsTrigger value="datos">Mis datos</TabsTrigger>
            <TabsTrigger value="seguridad">Seguridad</TabsTrigger>
            <TabsTrigger value="notificaciones">Notificaciones</TabsTrigger>
          </TabsList>
          <TabsContent value="datos">
            <TabDatosPropios />
          </TabsContent>
          <TabsContent value="seguridad">
            <TabSeguridad />
          </TabsContent>
          <TabsContent value="notificaciones">
            <TabNotificaciones />
          </TabsContent>
        </Tabs>
      </GlassCard>
    </PageContainer>
  );
}
