import { useDocumentosAlumno } from '@/features/identity/hooks/useLegajoAlumno';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { SEX_LABEL } from '@/lib/person';

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
      <span className="text-size-11 font-medium uppercase tracking-wider text-ink-faint">{label}</span>
      <Input value={value} readOnly className="cursor-default" />
    </div>
  );
}

function Seccion({ titulo, children }) {
  return (
    <div className="flex flex-col gap-4">
      <h3 className="text-size-11 font-semibold uppercase tracking-wider text-ink-faint">{titulo}</h3>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">{children}</div>
    </div>
  );
}

function DocumentosSection({ studentId }) {
  const { data: documentos, isLoading, isError, error, refetch } = useDocumentosAlumno(studentId);

  return (
    <div className="flex flex-col gap-4">
      <h3 className="text-size-11 font-semibold uppercase tracking-wider text-ink-faint">Documentos</h3>
      {isLoading ? (
        <Skeleton className="h-16 w-full" />
      ) : isError ? (
        <LoadErrorState bare error={error} onRetry={refetch} resource="los documentos" />
      ) : !documentos?.length ? (
        <p className="text-size-13 text-ink-faint">Sin documentos cargados.</p>
      ) : (
        <div className="flex flex-col divide-y divide-border">
          {documentos.map((doc) => (
            <div key={doc.id} className="flex items-center justify-between py-3">
              <span className="text-size-14 text-ink">{TIPO_DOCUMENTO[doc.type] ?? doc.type}</span>
              <span className="text-size-12 text-ink-faint">
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

export function DatosPersonalesTab({ legajo, puedeAccionar, puedeDarDeBaja, puedeReactivar, onReset, onBaja, onReactivar }) {
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
        <Campo label="Fecha de nacimiento" value={legajo.birthDate ? legajo.birthDate.split('-').reverse().join('/') : null} />
        {legajo.minor && <Campo label="Edad" value="Menor de 18 años" />}
        <Campo label="Sexo / Género" value={SEX_LABEL[legajo.sexo] ?? legajo.sexo} />
        <Campo label="Nacionalidad" value={legajo.nacionalidad} />
        <Campo label="Teléfono" value={legajo.telefono} />
        <Campo label="Email" value={legajo.email} />
        <Campo label="Usuario" value={legajo.username} />
        <Campo label="Estado" value={ESTADO_STATUS[legajo.status] ?? legajo.status} />
        <Campo label="Onboarding" value={ESTADO_ONBOARDING[legajo.onboardingStatus] ?? legajo.onboardingStatus} />
      </Seccion>

      {legajo.guardian && (
        <Seccion titulo="Tutor">
          <Campo label="Nombre" value={legajo.guardian.fullName} />
          <Campo label="DNI" value={legajo.guardian.documentNumber} />
          <Campo label="Parentesco" value={legajo.guardian.relationship} />
          <Campo label="Teléfono" value={legajo.guardian.phone} />
          <Campo label="Email" value={legajo.guardian.email} />
        </Seccion>
      )}

      {legajo.emergencyContact && (
        <Seccion titulo="Contacto de emergencia">
          <Campo label="Nombre" value={legajo.emergencyContact.name} />
          <Campo label="Parentesco" value={legajo.emergencyContact.relationship} />
          <Campo label="Teléfono" value={legajo.emergencyContact.phone} />
        </Seccion>
      )}

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

      {(puedeAccionar || puedeDarDeBaja || puedeReactivar) && (
        <div className="flex flex-col gap-4">
          <h3 className="text-size-11 font-semibold uppercase tracking-wider text-ink-faint">Acciones</h3>
          <div className="flex flex-col divide-y divide-border rounded-lg border border-border">
            {puedeAccionar && (
              <div className="flex items-center justify-between gap-4 px-4 py-3">
                <div className="flex flex-col gap-0.5">
                  <span className="text-size-14 font-medium text-ink">Resetear contraseña</span>
                  <span className="text-size-12 text-ink-faint">Envía un link de recuperación al email del alumno.</span>
                </div>
                <Button variant="gradient" size="sm" onClick={onReset}>
                  Resetear contraseña
                </Button>
              </div>
            )}
            {puedeReactivar && (
              <div className="flex items-center justify-between gap-4 px-4 py-3">
                <div className="flex flex-col gap-0.5">
                  <span className="text-size-14 font-medium text-ink">Reactivar alumno</span>
                  <span className="text-size-12 text-ink-faint">Vuelve a habilitar la cuenta y conserva su historia académica.</span>
                </div>
                <Button variant="gradient" size="sm" onClick={onReactivar}>
                  Reactivar alumno
                </Button>
              </div>
            )}
            {puedeDarDeBaja && (
              <div className="flex items-center justify-between gap-4 px-4 py-3">
                <div className="flex flex-col gap-0.5">
                  <span className="text-size-14 font-medium text-ink">Dar de baja</span>
                  <span className="text-size-12 text-ink-faint">Marca al alumno como dado de baja del sistema. Podés reactivarlo después desde su legajo.</span>
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
