import { useRef, useState } from 'react';
import { Camera, Eye, EyeOff } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { SEX_LABEL } from '@/lib/person';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { Skeleton } from '@/components/Skeleton';
import {
  usePerfilPropio,
  useActualizarPerfil,
  useCambiarContrasena,
  useSesionesActivas,
  useRevocarTodasLasSesiones,
  usePreferenciasNotificaciones,
  useActualizarPreferenciasNotificaciones,
  useAvatar,
  useSubirAvatar,
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

const AVATAR_TYPES = ['image/jpeg', 'image/png'];
const AVATAR_MAX_BYTES = 5 * 1024 * 1024;
const AVATAR_RULE_MESSAGE = 'La foto tiene que ser JPG o PNG de hasta 5 MB.';

const PASSWORD_ERROR_MESSAGES = {
  INVALID_CURRENT_PASSWORD: 'La contraseña actual no es correcta.',
  INVALID_PASSWORD_POLICY: 'La nueva contraseña tiene que tener al menos 8 caracteres, una mayúscula y un número.',
};

function CampoFormulario({ label, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[13px] font-medium text-ink-soft">{label}</label>
      {children}
    </div>
  );
}

function InputContrasena({ value, onChange, required, placeholder }) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <Input
        type={visible ? 'text' : 'password'}
        value={value}
        onChange={onChange}
        required={required}
        placeholder={placeholder}
        className="pr-10"
      />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        tabIndex={-1}
        aria-label={visible ? 'Ocultar contraseña' : 'Ver contraseña'}
        className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-faint transition-colors hover:text-ink"
      >
        {visible ? <EyeOff size={15} /> : <Eye size={15} />}
      </button>
    </div>
  );
}

function AvatarUpload() {
  const { data: perfil } = usePerfilPropio();
  const { data: avatarUrl } = useAvatar(perfil?.hasAvatar);
  const subir = useSubirAvatar();
  const fileRef = useRef(null);

  function iniciales(nombre, apellido) {
    return `${nombre?.[0] ?? ''}${apellido?.[0] ?? ''}`.toUpperCase();
  }

  async function onFileChange(e) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!AVATAR_TYPES.includes(file.type) || file.size > AVATAR_MAX_BYTES) {
      toast.error(AVATAR_RULE_MESSAGE);
      return;
    }
    try {
      await subir.mutateAsync(file);
    } catch (err) {
      toast.error(
        err.code === 'UNSUPPORTED_FILE_TYPE' || err.code === 'MAX_UPLOAD_SIZE_EXCEEDED'
          ? AVATAR_RULE_MESSAGE
          : 'No pudimos subir la foto. Probá de nuevo.',
      );
    }
  }

  return (
    <div className="flex items-center gap-4">
      <button
        type="button"
        onClick={() => fileRef.current?.click()}
        disabled={subir.isPending}
        className="group relative h-16 w-16 shrink-0 overflow-hidden rounded-2xl transition-opacity hover:opacity-90"
      >
        {avatarUrl ? (
          <img src={avatarUrl} alt="Avatar" className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-primary to-brand font-heading text-xl font-bold text-white">
            {iniciales(perfil?.nombre, perfil?.apellido)}
          </div>
        )}
        <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 transition-opacity group-hover:opacity-100">
          <Camera size={18} className="text-white" />
        </div>
      </button>
      <div>
        <p className="text-[13px] font-medium text-ink">Foto de perfil</p>
        <p className="text-[11px] text-ink-faint">JPG o PNG, máx. 5 MB</p>
        {subir.isPending && <p className="text-[11px] text-ink-faint">Subiendo...</p>}
      </div>
      <input
        ref={fileRef}
        type="file"
        accept="image/jpeg,image/png"
        className="hidden"
        onChange={onFileChange}
      />
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
      <AvatarUpload />
      {perfil?.legajo && (
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-medium uppercase tracking-wider text-ink-faint">Legajo</span>
          <span className="rounded-full bg-accent-soft px-3 py-0.5 text-[13px] font-semibold text-brand">
            #{perfil.legajo}
          </span>
        </div>
      )}
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
              {Object.entries(SEX_LABEL).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
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
  const { logout } = useAuth();

  const [pass, setPass] = useState({ actual: '', nueva: '', confirmar: '' });
  const [errorPass, setErrorPass] = useState('');
  const [errorSesiones, setErrorSesiones] = useState('');
  const [sesionesRevocadas, setSesionesRevocadas] = useState(false);

  async function guardarContrasena(e) {
    e.preventDefault();
    setErrorPass('');
    if (pass.nueva !== pass.confirmar) {
      setErrorPass('Las contraseñas nuevas no coinciden.');
      return;
    }
    try {
      await cambiarContrasena.mutateAsync({ currentPassword: pass.actual, newPassword: pass.nueva });
    } catch (err) {
      setErrorPass(PASSWORD_ERROR_MESSAGES[err.code] ?? err.message ?? 'No se pudo cambiar la contraseña.');
      return;
    }
    toast.success('Contraseña actualizada. Por seguridad cerramos todas tus sesiones: ingresá de nuevo con tu nueva contraseña.');
    await logout();
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
              <InputContrasena
                value={pass.actual}
                onChange={(e) => setPass((p) => ({ ...p, actual: e.target.value }))}
                required
              />
            </CampoFormulario>
            <div />
            <CampoFormulario label="Nueva contraseña">
              <InputContrasena
                value={pass.nueva}
                onChange={(e) => setPass((p) => ({ ...p, nueva: e.target.value }))}
                required
              />
              <span className="text-[11.5px] text-ink-faint">Mínimo 8 caracteres, con una mayúscula y un número.</span>
            </CampoFormulario>
            <CampoFormulario label="Confirmar nueva contraseña">
              <InputContrasena
                value={pass.confirmar}
                onChange={(e) => setPass((p) => ({ ...p, confirmar: e.target.value }))}
                required
              />
            </CampoFormulario>
          </div>
          {errorPass && <p className="text-[13px] text-danger">{errorPass}</p>}
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
  const [pendiente, setPendiente] = useState(null);
  const [error, setError] = useState('');

  const prefsOriginales = data?.preferences ?? {};
  const prefs = pendiente ?? prefsOriginales;
  const haycambios = pendiente !== null;

  function togglePref(evento, canal) {
    const base = pendiente ?? prefsOriginales;
    const valorActual = base[evento]?.[canal] ?? true;
    setPendiente({
      ...base,
      [evento]: { ...(base[evento] ?? {}), [canal]: !valorActual },
    });
    setError('');
  }

  async function guardar() {
    try {
      await actualizar.mutateAsync({ preferences: pendiente });
      setPendiente(null);
    } catch (err) {
      setError(err.message ?? 'No se pudo guardar.');
    }
  }

  function cancelar() {
    setPendiente(null);
    setError('');
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

  const eventos = Object.keys(ETIQUETAS_EVENTO);
  const canales = Object.keys(ETIQUETAS_CANAL);

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-x-auto">
      <Table className="min-w-[400px]">
        <TableHeader>
          <TableRow>
            <TableHead>Notificación</TableHead>
            {canales.map((canal) => (
              <TableHead key={canal} className="text-center">
                {ETIQUETAS_CANAL[canal]}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {eventos.map((evento) => (
            <TableRow key={evento}>
              <TableCell className="text-ink">{ETIQUETAS_EVENTO[evento]}</TableCell>
              {canales.map((canal) => {
                const activo = prefs[evento]?.[canal] ?? true;
                return (
                  <TableCell key={canal} className="text-center">
                    <button
                      type="button"
                      onClick={() => togglePref(evento, canal)}
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
                  </TableCell>
                );
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
      </div>
      {error && <p className="text-[13px] text-danger">{error}</p>}
      {haycambios && (
        <div className="flex items-center justify-end gap-2">
          <Button size="sm" variant="ghost" onClick={cancelar} disabled={actualizar.isPending}>
            Cancelar
          </Button>
          <Button size="sm" onClick={guardar} disabled={actualizar.isPending}>
            {actualizar.isPending ? 'Guardando…' : 'Guardar'}
          </Button>
        </div>
      )}
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
          <TabsList variant="segmented" className="mb-6">
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
