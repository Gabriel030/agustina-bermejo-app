import { useState, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ChevronLeft, Copy, Check, Upload, Clock, CheckCircle, XCircle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Stepper } from '@/components/Stepper';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { usePaymentConfig, useSubirComprobante, useMisComprobantes } from '@/hooks/useBilling';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

const PASOS = [
  { key: 'datos', label: 'Datos bancarios' },
  { key: 'comprobante', label: 'Subir comprobante' },
  { key: 'estado', label: 'Seguimiento' },
];

function useCopied() {
  const [copiado, setCopiado] = useState(false);
  function copiar(texto) {
    navigator.clipboard.writeText(texto).then(() => {
      setCopiado(true);
      setTimeout(() => setCopiado(false), 2000);
    });
  }
  return { copiado, copiar };
}

function DatoField({ label, value }) {
  const { copiado, copiar } = useCopied();
  if (!value) return null;
  return (
    <div className="flex items-center justify-between gap-4 py-2.5">
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-faint">{label}</p>
        <p className="mt-0.5 font-mono font-semibold">{value}</p>
      </div>
      <button
        type="button"
        onClick={() => copiar(value)}
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-border text-ink-faint transition-colors hover:border-brand hover:text-brand"
        title="Copiar"
      >
        {copiado ? <Check size={14} /> : <Copy size={14} />}
      </button>
    </div>
  );
}

function PasoDatos({ config, onNext }) {
  return (
    <div className="flex flex-col gap-6">
      <GlassCard className="divide-y divide-border p-5">
        <DatoField label="Titular" value={config?.accountHolder} />
        <DatoField label="CBU" value={config?.bankCbu} />
        <DatoField label="Alias" value={config?.bankAlias} />
      </GlassCard>

      {config?.qrImageUrl && (
        <GlassCard className="flex flex-col items-center gap-3 p-5">
          <p className="text-sm font-semibold text-ink-faint">Código QR de pago</p>
          <img
            src={`${API_BASE}${config.qrImageUrl}`}
            alt="QR de pago"
            className="h-48 w-48 rounded-xl object-contain"
          />
        </GlassCard>
      )}

      <p className="text-sm text-ink-faint">
        Realizá la transferencia por el monto exacto de la factura y luego subí el comprobante en el siguiente paso.
      </p>

      <Button variant="gradient" onClick={onNext}>
        Ya transferí — subir comprobante
      </Button>
    </div>
  );
}

function PasoComprobante({ invoiceId, onSuccess }) {
  const fileInputRef = useRef(null);
  const [file, setFile] = useState(null);
  const { mutate, isPending, error } = useSubirComprobante();

  function handleFileChange(e) {
    setFile(e.target.files[0] ?? null);
  }

  function handleSubmit() {
    if (!file) return;
    mutate(
      { invoiceId, file },
      { onSuccess: () => onSuccess() },
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <GlassCard className="p-5">
        <p className="mb-4 text-sm text-ink-faint">
          Subí el comprobante de transferencia (PDF, JPG o PNG, máximo 5MB). Una vez revisado, el pago quedará registrado.
        </p>

        <div
          className="flex cursor-pointer flex-col items-center gap-3 rounded-xl border-2 border-dashed border-border py-10 transition-colors hover:border-brand/60"
          onClick={() => fileInputRef.current?.click()}
        >
          <Upload size={28} className="text-ink-faint" />
          {file ? (
            <p className="text-sm font-semibold text-ink">{file.name}</p>
          ) : (
            <p className="text-sm text-ink-faint">Hacé clic para seleccionar el archivo</p>
          )}
        </div>
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.jpg,.jpeg,.png"
          className="hidden"
          onChange={handleFileChange}
        />
      </GlassCard>

      {error && <p className="text-sm text-danger">{error.message}</p>}

      <Button variant="gradient" disabled={!file || isPending} onClick={handleSubmit}>
        {isPending ? 'Enviando…' : 'Enviar comprobante'}
      </Button>
    </div>
  );
}

const RECEIPT_STATUS = {
  PENDING_REVIEW: { label: 'Pendiente de revisión', icon: Clock, tone: 'warning' },
  APPROVED: { label: 'Aprobado', icon: CheckCircle, tone: 'success' },
  REJECTED: { label: 'Rechazado', icon: XCircle, tone: 'danger' },
};

function PasoEstado({ invoiceId }) {
  const navigate = useNavigate();
  const { data: comprobantes, isLoading } = useMisComprobantes();
  const comprobante = comprobantes?.find((c) => c.invoiceId === parseInt(invoiceId, 10));

  if (isLoading) return <Skeleton className="h-32" />;

  if (!comprobante) {
    return (
      <GlassCard className="p-6 text-center text-sm text-ink-faint">
        No se encontró el comprobante. Intentá recargar la página.
      </GlassCard>
    );
  }

  const info = RECEIPT_STATUS[comprobante.status] ?? { label: comprobante.status, icon: Clock, tone: 'neutral' };
  const Icon = info.icon;
  const colorClass = { warning: 'text-warning', success: 'text-success', danger: 'text-danger', neutral: 'text-ink-faint' }[info.tone];

  return (
    <div className="flex flex-col gap-6">
      <GlassCard className="flex flex-col items-center gap-4 p-8 text-center">
        <Icon size={48} className={colorClass} />
        <div>
          <p className="text-lg font-bold">{info.label}</p>
          {comprobante.status === 'PENDING_REVIEW' && (
            <p className="mt-1 text-sm text-ink-faint">
              Tu comprobante está siendo revisado. Te notificaremos cuando esté aprobado.
            </p>
          )}
          {comprobante.status === 'APPROVED' && (
            <p className="mt-1 text-sm text-ink-faint">Tu pago fue confirmado correctamente.</p>
          )}
          {comprobante.status === 'REJECTED' && (
            <>
              <p className="mt-1 text-sm text-ink-faint">Tu comprobante fue rechazado.</p>
              {comprobante.rejectionReason && (
                <p className="mt-2 text-sm font-semibold text-danger">{comprobante.rejectionReason}</p>
              )}
            </>
          )}
        </div>
      </GlassCard>

      <Button variant="gradient" onClick={() => navigate('/alumno/pagos')}>
        Volver a Mis pagos
      </Button>
    </div>
  );
}

export function AlumnoPagarTransferencia() {
  const { invoiceId } = useParams();
  const navigate = useNavigate();
  const { data: config, isLoading } = usePaymentConfig();
  const [pasoActual, setPasoActual] = useState('datos');
  const [pasosVisitados, setPasosVisitados] = useState(['datos']);

  function irAPaso(paso) {
    setPasoActual(paso);
    if (!pasosVisitados.includes(paso)) {
      setPasosVisitados((prev) => [...prev, paso]);
    }
  }

  return (
    <PageContainer className="max-w-lg">
      <button
        type="button"
        onClick={() => navigate(`/alumno/pagos/${invoiceId}/seleccion`)}
        className="flex items-center gap-1.5 text-sm text-ink-faint hover:text-ink"
      >
        <ChevronLeft size={16} />
        Volver
      </button>

      <h1 className="text-xl font-bold">Pago por transferencia</h1>

      <Stepper
        pasos={PASOS}
        pasoActual={pasoActual}
        pasosVisitados={pasosVisitados}
        onStepClick={irAPaso}
      />

      {isLoading && <Skeleton className="h-48" />}

      {!isLoading && pasoActual === 'datos' && (
        <PasoDatos config={config} onNext={() => irAPaso('comprobante')} />
      )}
      {pasoActual === 'comprobante' && (
        <PasoComprobante invoiceId={invoiceId} onSuccess={() => irAPaso('estado')} />
      )}
      {pasoActual === 'estado' && (
        <PasoEstado invoiceId={invoiceId} />
      )}
    </PageContainer>
  );
}
