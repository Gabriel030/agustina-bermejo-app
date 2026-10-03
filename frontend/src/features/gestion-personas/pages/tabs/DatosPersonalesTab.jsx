import { useDocumentosAlumno } from '@/features/gestion-personas/hooks/useLegajoAlumno';
import { Skeleton } from '@/components/Skeleton';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

const TIPO_DOCUMENTO = {
  DNI_FRENTE: 'DNI frente',
  DNI_DORSO: 'DNI dorso',
  ANALITICO_SECUNDARIO: 'Analítico secundario',
};

const ESTADO_ONBOARDING = {
  CONFIRMADO: 'Confirmado',
  PROSPECTO: 'Prospecto',
};

const ESTADO_STATUS = {
  ACTIVE: 'Activo',
  INACTIVE: 'Dado de baja',
  DELETED: 'Dado de baja',
};

const MOTIVO_BAJA = {
  ABANDONO: 'Abandono',
  CAMBIO_CARRERA: 'Cambio de carrera',
  OTRO: 'Otro',
};

const ADDRESS_FIELDS = ['street', 'number', 'floor', 'city', 'state', 'postalCode', 'country'];

function Campo({ label, value }) {
  if (!value) return null;
  return (
    <div className="flex flex-col gap-1">
      <span className="text-[11px] font-medium uppercase tracking-wider text-ink-faint">{label}</span>
      <Input value={value} readOnly className="cursor-default" />
    </div>
  );
}

function Seccion({ titulo, children }) {
  return (
    <div className="flex flex-col gap-4">
      <h3 className="text-[11px] font-semibold uppercase tracking-wider text-ink-faint">{titulo}</h3>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">{children}</div>
    </div>
  );
}

function DocumentosSection({ studentId }) {
  const { data: documentos, isLoading } = useDocumentosAlumno(studentId);

  return (
    <div className="flex flex-col gap-4">
      <h3 className="text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Documentos</h3>
      {isLoading ? (
        <Skeleton className="h-16 w-full" />
      ) : !documentos?.length ? (
        <p className="text-[13px] text-ink-faint">Sin documentos cargados.</p>
      ) : (
        <div className="flex flex-col divide-y divide-border">
          {documentos.map((doc) => (
            <div key={doc.id} className="flex items-center justify-between py-3">
              <span className="text-[14px] text-ink">{TIPO_DOCUMENTO[doc.type] ?? doc.type}</span>
              <span className="text-[12px] text-ink-faint">
                {new Date(doc.uploadedAt).toLocaleDateString('es-AR', {
                  day: '2-digit',
                  month: '2-digit',
                  year: 'numeric',
                })}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function DatosPersonalesTab({ legajo, puedeAccionar, puedeDarDeBaja, onReset, onBaja }) {
  if (!legajo) return <Skeleton className="h-64 w-full" />;

  const domicilio = legajo.domicilio;
  const hasAddress = !!domicilio && ADDRESS_FIELDS.some((field) => domicilio[field]);
  const isWithdrawn = legajo.status === 'DELETED';

  return (
    <div className="flex flex-col gap-8">
      <Seccion titulo="Información personal">
        <Campo label="Nombre" value={`${legajo.nombre} ${legajo.apellido}`} />
        <Campo label="Legajo" value={legajo.legajo ? `#${legajo.legajo}` : null} />
        <Campo label="DNI" value={legajo.dni} />
        <Campo label="Sexo / Género" value={legajo.sexo} />
        <Campo label="Nacionalidad" value={legajo.nacionalidad} />
        <Campo label="Teléfono" value={legajo.telefono} />
        <Campo label="Email" value={legajo.email} />
        <Campo label="Usuario" value={legajo.username} />
        <Campo label="Estado" value={ESTADO_STATUS[legajo.status] ?? legajo.status} />
        <Campo label="Onboarding" value={ESTADO_ONBOARDING[legajo.onboardingStatus] ?? legajo.onboardingStatus} />
      </Seccion>

      {hasAddress && (
        <Seccion titulo="Domicilio">
          <Campo label="Calle" value={domicilio.street} />
          <Campo label="Número" value={domicilio.number} />
          <Campo label="Piso" value={domicilio.floor} />
          <Campo label="Ciudad" value={domicilio.city} />
          <Campo label="Provincia" value={domicilio.state} />
          <Campo label="Código postal" value={domicilio.postalCode} />
          <Campo label="País" value={domicilio.country} />
        </Seccion>
      )}

      {isWithdrawn && legajo.motivoBaja && (
        <Seccion titulo="Información de baja">
          <Campo label="Motivo" value={MOTIVO_BAJA[legajo.motivoBaja] ?? legajo.motivoBaja} />
          {legajo.detalleBaja && <Campo label="Detalle" value={legajo.detalleBaja} />}
        </Seccion>
      )}

      <DocumentosSection studentId={legajo.id} />

      {puedeAccionar && (
        <div className="flex flex-col gap-4">
          <h3 className="text-[11px] font-semibold uppercase tracking-wider text-ink-faint">Acciones</h3>
          <div className="flex flex-col divide-y divide-border rounded-lg border border-border">
            <div className="flex items-center justify-between gap-4 px-4 py-3">
              <div className="flex flex-col gap-0.5">
                <span className="text-[14px] font-medium text-ink">Resetear contraseña</span>
                <span className="text-[12px] text-ink-faint">Envía un link de recuperación al email del alumno.</span>
              </div>
              <Button variant="gradient" size="sm" onClick={onReset}>
                Resetear contraseña
              </Button>
            </div>
            {puedeDarDeBaja && (
              <div className="flex items-center justify-between gap-4 px-4 py-3">
                <div className="flex flex-col gap-0.5">
                  <span className="text-[14px] font-medium text-ink">Dar de baja</span>
                  <span className="text-[12px] text-ink-faint">Marca al alumno como dado de baja del sistema. Esta acción no se puede deshacer.</span>
                </div>
                <Button variant="destructive" size="sm" onClick={onBaja}>
                  Dar de baja
                </Button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
