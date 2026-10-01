import { useEffect, useRef, useState } from 'react';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/Skeleton';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';
import { CATEGORIA_AVISO_LABEL, CUANTIFICADOR_LABEL, formatearFechaHora, instanciaLabel } from '@/lib/avisos';
import { useCrearAviso } from '../hooks/useCrearAviso';
import { useEditarAviso } from '../hooks/useEditarAviso';
import { usePreviewDeAudiencia } from '../hooks/usePreviewDeAudiencia';
import { useSegmentosDeAudiencia } from '../hooks/useSegmentosDeAudiencia';

const SEGMENT_GROUPS = [
  { label: 'General', keys: ['ALL'] },
  { label: 'Cursada', keys: ['MUST_TAKE_FINAL', 'PROMOTED', 'PASSED', 'FAILED_COURSE'] },
  { label: 'Parciales', keys: ['EXAM_FAILED', 'EXAM_PASSED', 'EXAM_MISSING'] },
  { label: 'Trabajos Prácticos', keys: ['TP_FAILED', 'TP_PASSED', 'TP_MISSING'] },
  { label: 'Final', keys: ['FINAL_PASSED', 'FINAL_FAILED'] },
  { label: 'Asistencia', keys: ['ABSENCES'] },
];

function derivarModo(aviso) {
  if (!aviso) return 'nuevo';
  return aviso.status === 'SCHEDULED' ? 'editar-programado' : 'editar-enviado';
}

function toLocalDatetimeInput(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function minDatetimeLocal() {
  const d = new Date();
  d.setMinutes(d.getMinutes() + 5);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function todayIsoDate() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function buildPayload({ titulo, body, category, urgent, expiresAt, envioMode, scheduledFor, segmentKey, selectedInstances, quantifier, modo }) {
  return {
    title: titulo,
    body,
    category: category || null,
    urgent,
    expiresAt: expiresAt || null,
    scheduledFor: modo !== 'editar-enviado' && envioMode === 'programar' && scheduledFor ? scheduledFor : null,
    audience: segmentKey ? {
      key: segmentKey,
      instances: selectedInstances.length > 0 ? selectedInstances : null,
      quantifier: selectedInstances.length >= 2 ? quantifier : null,
      threshold: null,
    } : null,
  };
}

export function AvisoDialog({ open, onOpenChange, sectionId, tipo, aviso }) {
  const modo = derivarModo(aviso);

  const [titulo, setTitulo] = useState('');
  const [body, setBody] = useState('');
  const [category, setCategory] = useState('');
  const [urgent, setUrgent] = useState(false);
  const [expiresAt, setExpiresAt] = useState('');
  const [segmentKey, setSegmentKey] = useState('');
  const [selectedInstances, setSelectedInstances] = useState([]);
  const [quantifier, setQuantifier] = useState('ANY');
  const [envioMode, setEnvioMode] = useState('ahora');
  const [scheduledFor, setScheduledFor] = useState('');
  const [rosterOpen, setRosterOpen] = useState(false);
  const [confirmClose, setConfirmClose] = useState(false);
  const [dirty, setDirty] = useState(false);

  const segmentosQuery = useSegmentosDeAudiencia(sectionId, tipo);
  const previewMutation = usePreviewDeAudiencia(sectionId, tipo);
  const crearMutation = useCrearAviso(sectionId, tipo);
  const editarMutation = useEditarAviso(sectionId, tipo, aviso?.id);

  const debounceRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    if (aviso) {
      setTitulo(aviso.title ?? '');
      setBody(aviso.body ?? '');
      setCategory(aviso.category ?? '');
      setUrgent(aviso.urgent ?? false);
      setExpiresAt(aviso.expiresAt ?? '');
      setScheduledFor(toLocalDatetimeInput(aviso.scheduledFor));
      setEnvioMode(aviso.scheduledFor ? 'programar' : 'ahora');
      setSegmentKey('');
      setSelectedInstances([]);
      setQuantifier('ANY');
      setRosterOpen(false);
      setDirty(false);
    } else {
      setTitulo(''); setBody(''); setCategory(''); setUrgent(false);
      setExpiresAt(''); setScheduledFor(''); setEnvioMode('ahora');
      setSegmentKey(''); setSelectedInstances([]); setQuantifier('ANY');
      setRosterOpen(false); setDirty(false);
    }
    previewMutation.reset();
  }, [open, aviso?.id]);

  const segmentos = segmentosQuery.data ?? [];
  const segmentoActual = segmentos.find((s) => s.key === segmentKey);
  const needsInstances = segmentoActual?.needsInstances ?? false;
  const instanceCount = segmentoActual?.instanceCount ?? 0;

  useEffect(() => {
    if (!segmentKey) { previewMutation.reset(); return; }
    if (needsInstances && selectedInstances.length === 0) { previewMutation.reset(); return; }
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      previewMutation.mutate({
        key: segmentKey,
        instances: selectedInstances.length > 0 ? selectedInstances : null,
        quantifier: selectedInstances.length >= 2 ? quantifier : null,
        threshold: null,
      });
    }, 300);
    return () => clearTimeout(debounceRef.current);
  }, [segmentKey, selectedInstances, quantifier]);

  function handleSegmentChange(key) {
    setSegmentKey(key);
    setSelectedInstances([]);
    setRosterOpen(false);
    setDirty(true);
  }

  function toggleInstance(index) {
    setSelectedInstances((prev) =>
      prev.includes(index) ? prev.filter((i) => i !== index) : [...prev, index]
    );
    setDirty(true);
  }

  function handleClose() {
    if (dirty) { setConfirmClose(true); return; }
    onOpenChange(false);
  }

  function handleConfirmClose() {
    setConfirmClose(false);
    onOpenChange(false);
  }

  function handleSubmit() {
    const payload = buildPayload({ titulo, body, category, urgent, expiresAt, envioMode, scheduledFor, segmentKey, selectedInstances, quantifier, modo });
    const onSuccess = () => { setDirty(false); onOpenChange(false); };
    if (modo === 'nuevo') {
      crearMutation.mutate(payload, { onSuccess });
    } else {
      editarMutation.mutate(payload, { onSuccess });
    }
  }

  const preview = previewMutation.data;
  const previewCount = preview?.count ?? null;
  const total = preview?.totalInSection ?? null;
  const hasUngradedInstances = preview?.hasUngradedInstances ?? false;
  const rosterStudents = preview?.students ?? [];

  const zeroAhora = previewCount === 0 && envioMode === 'ahora' && segmentKey && (!needsInstances || selectedInstances.length > 0);
  const zeroProgram = previewCount === 0 && envioMode === 'programar' && segmentKey && (!needsInstances || selectedInstances.length > 0);
  const noInstSelected = needsInstances && selectedInstances.length === 0;
  const showPreview = !noInstSelected && previewCount !== null;

  const submitting = crearMutation.isPending || editarMutation.isPending;
  const canSubmit = titulo.trim() && body.trim() && segmentKey
    && (!needsInstances || selectedInstances.length > 0)
    && !zeroAhora
    && !submitting;

  const ctaLabel = modo === 'nuevo'
    ? 'Publicar aviso'
    : modo === 'editar-programado'
    ? 'Guardar cambios'
    : 'Guardar y re-notificar';

  return (
    <>
      <Dialog open={open} onOpenChange={(v) => { if (!v) handleClose(); }}>
        <DialogContent
          showCloseButton={false}
          className="bg-dialog-bg sm:max-w-lg max-h-[85dvh] flex flex-col p-0 gap-0 overflow-hidden"
        >
          {/* ── Header ── */}
          <div className="flex flex-col gap-1.5 flex-shrink-0 px-5 pt-5 pb-4 border-b border-border">
            <div className="flex items-start justify-between gap-3">
              <DialogTitle className="font-heading text-[16.5px] font-bold text-ink">
                {modo === 'nuevo' ? 'Nuevo aviso' : 'Editar aviso'}
              </DialogTitle>
              <Button variant="ghost" size="icon-sm" onClick={handleClose} className="flex-shrink-0 -mt-0.5">
                <span className="sr-only">Cerrar</span>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </Button>
            </div>
            {modo === 'editar-programado' && (
              <DialogDescription className="text-[12.5px] text-ink-soft">
                Los cambios se aplican al envío programado para el {formatearFechaHora(aviso?.scheduledFor)}.
              </DialogDescription>
            )}
            {modo === 'editar-enviado' && (
              <DialogDescription className="text-[12.5px] text-warning flex items-start gap-1.5">
                <svg className="mt-0.5 flex-shrink-0" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                Editar este aviso notifica de nuevo a todos los destinatarios.
              </DialogDescription>
            )}
          </div>

          {/* ── Body (scrollable) ── */}
          <div className="flex-1 min-h-0 overflow-y-auto px-5 py-4 flex flex-col gap-4">

            {/* ── Bloque 1: A quién ── */}
            <div className="flex flex-col gap-3">
              <div className="flex flex-col gap-1.5">
                <Label className="text-[11px] font-bold uppercase tracking-[0.07em] text-ink-faint">
                  A quién
                </Label>
                {segmentosQuery.isLoading ? (
                  <Skeleton className="h-9 w-full" />
                ) : segmentosQuery.isError ? (
                  <p className="text-[12.5px] text-danger">Error al cargar los grupos de audiencia.</p>
                ) : (
                  <Select value={segmentKey} onValueChange={handleSegmentChange}>
                    <SelectTrigger className="bg-dialog-bg">
                      <SelectValue placeholder="— Seleccioná un grupo —" />
                    </SelectTrigger>
                    <SelectContent>
                      {SEGMENT_GROUPS.map((group) => {
                        const items = group.keys
                          .map((key) => segmentos.find((s) => s.key === key))
                          .filter(Boolean);
                        if (items.length === 0) return null;
                        return (
                          <SelectGroup key={group.label}>
                            <SelectLabel>{group.label}</SelectLabel>
                            {items.map((seg) => (
                              <SelectItem key={seg.key} value={seg.key}>
                                {seg.label}
                                {seg.count != null && (
                                  <span className="ml-1.5 text-ink-faint">({seg.count})</span>
                                )}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        );
                      })}
                    </SelectContent>
                  </Select>
                )}
              </div>

              {/* Chips de instancias */}
              {needsInstances && instanceCount > 0 && (
                <div className="flex flex-col gap-2">
                  <span className="text-[11px] font-bold uppercase tracking-[0.07em] text-ink-faint">
                    En cuáles
                  </span>
                  <div className="flex flex-wrap gap-2">
                    {Array.from({ length: instanceCount }, (_, i) => i + 1).map((idx) => (
                      <button
                        key={idx}
                        type="button"
                        aria-pressed={selectedInstances.includes(idx)}
                        onClick={() => toggleInstance(idx)}
                        className={cn(
                          'rounded-full px-3 py-1.5 text-[12.5px] font-semibold transition-all',
                          selectedInstances.includes(idx)
                            ? 'bg-gradient-to-br from-primary to-accent-2 text-white shadow-sm'
                            : 'border border-border text-ink-soft hover:border-primary hover:text-primary'
                        )}
                      >
                        {instanciaLabel(segmentKey, idx)}
                      </button>
                    ))}
                  </div>

                  {/* Cuantificador (≥2 instancias seleccionadas) */}
                  {selectedInstances.length >= 2 && (
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-[13px] text-ink-soft">Los que tengan</span>
                      <Select value={quantifier} onValueChange={(v) => { setQuantifier(v); setDirty(true); }}>
                        <SelectTrigger className="h-7 text-[12.5px] font-semibold w-auto bg-dialog-bg">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {Object.entries(CUANTIFICADOR_LABEL).map(([k, label]) => (
                            <SelectItem key={k} value={k}>{label}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  )}
                </div>
              )}

              {/* Nota: sin instancias seleccionadas */}
              {noInstSelected && (
                <div className="nota-fila border-l-2 border-primary bg-accent-soft rounded-r-md px-3 py-2 text-[12.5px] text-ink-soft leading-relaxed flex gap-2">
                  <span className="flex-shrink-0 mt-0.5">ℹ</span>
                  <span>Seleccioná al menos una instancia para ver cuántos alumnos reciben el aviso.</span>
                </div>
              )}

              {/* Preview de audiencia */}
              {showPreview && (
                <div className="flex flex-col gap-1.5">
                  <div className="bg-accent-soft border border-border rounded-lg px-3 py-2.5">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[13.5px] font-bold text-ink">
                        {previewCount === 0 ? (
                          <span className="text-danger">0 destinatarios</span>
                        ) : (
                          <>
                            <span className="text-primary">{previewCount}</span>
                            {' de '}
                            {total} alumnos
                            {total > 0 && previewCount / total >= 0.9 && (
                              <span className="ml-1.5 text-[11px] text-ink-faint font-normal">
                                ({Math.round((previewCount / total) * 100)}%)
                              </span>
                            )}
                          </>
                        )}
                      </span>
                      {previewCount > 0 && rosterStudents.length > 0 && (
                        <button
                          type="button"
                          onClick={() => setRosterOpen((v) => !v)}
                          className="text-[12px] font-semibold text-primary flex items-center gap-1"
                        >
                          {rosterOpen ? 'Ocultar' : 'Ver quiénes'}
                          <svg
                            className={cn('transition-transform', rosterOpen && 'rotate-180')}
                            width="10" height="10" viewBox="0 0 24 24" fill="none"
                            stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"
                          >
                            <polyline points="6 9 12 15 18 9" />
                          </svg>
                        </button>
                      )}
                    </div>
                    {rosterOpen && rosterStudents.length > 0 && (
                      <div className="mt-2 pt-2 border-t border-border flex flex-col gap-1 max-h-28 overflow-y-auto">
                        {rosterStudents.map((s) => (
                          <div key={s.id} className="text-[12.5px] text-ink-soft flex items-center gap-1.5">
                            <span className="w-5 h-5 rounded-full bg-neutral-bg text-ink-faint text-[8.5px] font-bold flex items-center justify-center flex-shrink-0">
                              {s.name?.[0]}{s.lastname?.[0]}
                            </span>
                            {s.name} {s.lastname}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                  <p className="text-[11.5px] text-ink-faint leading-relaxed">
                    {segmentKey === 'ALL'
                      ? 'Todos los alumnos de la comisión van a ver este aviso.'
                      : 'Solo los alumnos que cumplan el criterio van a ver este aviso.'}
                  </p>
                </div>
              )}

              {/* Nota: 0 + Ahora */}
              {zeroAhora && (
                <div className="nota-fila border-l-2 border-warning bg-warning-bg rounded-r-md px-3 py-2 text-[12.5px] text-ink-soft leading-relaxed flex gap-2">
                  <span className="flex-shrink-0 mt-0.5">⚠</span>
                  <span>Ningún alumno coincide con este criterio ahora mismo. El aviso no se puede enviar hasta que haya destinatarios.</span>
                </div>
              )}

              {/* Nota: 0 + Programar */}
              {zeroProgram && (
                <div className="nota-fila border-l-2 border-primary bg-accent-soft rounded-r-md px-3 py-2 text-[12.5px] text-ink-soft leading-relaxed flex gap-2">
                  <span className="flex-shrink-0 mt-0.5">ℹ</span>
                  <span>Ningún alumno coincide ahora mismo, pero el grupo se recalcula al momento del envío programado.</span>
                </div>
              )}

              {/* Nota: sin calificar */}
              {hasUngradedInstances && !noInstSelected && (
                <div className="nota-fila border-l-2 border-warning bg-warning-bg rounded-r-md px-3 py-2 text-[12.5px] text-ink-soft leading-relaxed flex gap-2">
                  <span className="flex-shrink-0 mt-0.5">⚠</span>
                  <span>Algunos alumnos aún no tienen nota cargada. El grupo se actualiza automáticamente cuando se cargan las notas faltantes.</span>
                </div>
              )}
            </div>

            {/* ── Divider ── */}
            <div className="h-px bg-border" />

            {/* ── Bloque 2: Qué y cuándo ── */}
            <div className="flex flex-col gap-4">

              {/* Título + Categoría */}
              <div className="grid grid-cols-1 sm:grid-cols-[1fr_148px] gap-3 items-start">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="aviso-titulo" className="text-[11px] font-bold uppercase tracking-[0.07em] text-ink-faint">
                    Título <span className="text-danger normal-case">*</span>
                  </Label>
                  <Input
                    id="aviso-titulo"
                    value={titulo}
                    onChange={(e) => { setTitulo(e.target.value); setDirty(true); }}
                    maxLength={100}
                    placeholder="Ej: Recuperatorio del 1° parcial"
                    className="bg-dialog-bg"
                  />
                  <span className={cn(
                    'text-[10.5px] text-right tabular-nums',
                    titulo.length >= 100 ? 'text-danger' : titulo.length >= 90 ? 'text-warning' : 'text-ink-faint'
                  )}>
                    {titulo.length} / 100
                  </span>
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="aviso-cat" className="text-[11px] font-bold uppercase tracking-[0.07em] text-ink-faint">
                    Categoría
                  </Label>
                  <Select value={category} onValueChange={(v) => { setCategory(v); setDirty(true); }}>
                    <SelectTrigger id="aviso-cat" className="bg-dialog-bg">
                      <SelectValue placeholder="—" />
                    </SelectTrigger>
                    <SelectContent>
                      {Object.entries(CATEGORIA_AVISO_LABEL).map(([k, label]) => (
                        <SelectItem key={k} value={k}>{label}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              {/* Mensaje */}
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="aviso-body" className="text-[11px] font-bold uppercase tracking-[0.07em] text-ink-faint">
                  Mensaje <span className="text-danger normal-case">*</span>
                </Label>
                <Textarea
                  id="aviso-body"
                  value={body}
                  onChange={(e) => { setBody(e.target.value); setDirty(true); }}
                  maxLength={1000}
                  rows={4}
                  placeholder="Escribí el mensaje para los alumnos…"
                  className="bg-dialog-bg resize-none"
                />
                <span className={cn(
                  'text-[10.5px] text-right tabular-nums',
                  body.length >= 1000 ? 'text-danger' : body.length >= 900 ? 'text-warning' : 'text-ink-faint'
                )}>
                  {body.length} / 1000
                </span>
              </div>

              {/* Cuándo se envía (nuevo / editar-programado) */}
              {modo !== 'editar-enviado' && (
                <div className="flex flex-col gap-2">
                  <span className="text-[11px] font-bold uppercase tracking-[0.07em] text-ink-faint">
                    Cuándo se envía
                  </span>
                  <div className="flex gap-2">
                    {['ahora', 'programar'].map((m) => (
                      <button
                        key={m}
                        type="button"
                        aria-pressed={envioMode === m}
                        onClick={() => { setEnvioMode(m); setDirty(true); }}
                        className={cn(
                          'rounded-full px-3 py-1.5 text-[12.5px] font-semibold transition-all',
                          envioMode === m
                            ? 'bg-gradient-to-br from-primary to-accent-2 text-white shadow-sm'
                            : 'border border-border text-ink-soft hover:border-primary hover:text-primary'
                        )}
                      >
                        {m === 'ahora' ? 'Ahora' : 'Programar…'}
                      </button>
                    ))}
                  </div>
                  {envioMode === 'programar' && (
                    <Input
                      type="datetime-local"
                      value={scheduledFor}
                      min={minDatetimeLocal()}
                      onChange={(e) => { setScheduledFor(e.target.value); setDirty(true); }}
                      className="w-auto bg-dialog-bg"
                    />
                  )}
                </div>
              )}

              {/* Enviado el (editar-enviado) */}
              {modo === 'editar-enviado' && aviso?.sentAt && (
                <div className="flex flex-col gap-1">
                  <span className="text-[11px] font-bold uppercase tracking-[0.07em] text-ink-faint">Enviado</span>
                  <span className="text-[13.5px] text-ink-soft">
                    <strong className="text-ink">{formatearFechaHora(aviso.sentAt)}</strong>
                  </span>
                </div>
              )}

              {/* Urgente */}
              <div className="flex flex-col gap-2">
                <span className="text-[11px] font-bold uppercase tracking-[0.07em] text-ink-faint">Urgencia</span>
                <button
                  type="button"
                  aria-pressed={urgent}
                  onClick={() => { setUrgent((v) => !v); setDirty(true); }}
                  className={cn(
                    'self-start rounded-full px-3 py-1.5 text-[12.5px] font-semibold transition-all',
                    urgent
                      ? 'bg-gradient-to-br from-primary to-accent-2 text-white shadow-sm'
                      : 'border border-border text-ink-soft hover:border-primary hover:text-primary'
                  )}
                >
                  🔴&nbsp; Urgente
                </button>
                <p className="text-[11.5px] text-ink-faint leading-relaxed">
                  Marcá como urgente si el alumno debe verlo de inmediato.
                </p>
              </div>

              {/* Caducidad */}
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center gap-2">
                  <span className="text-[11px] font-bold uppercase tracking-[0.07em] text-ink-faint">Hasta cuándo se muestra</span>
                  <span className="text-[10.5px] text-ink-faint">(opcional)</span>
                </div>
                <Input
                  type="date"
                  value={expiresAt}
                  min={todayIsoDate()}
                  onChange={(e) => { setExpiresAt(e.target.value); setDirty(true); }}
                  className="w-auto bg-dialog-bg"
                />
                <p className="text-[11.5px] text-ink-faint leading-relaxed">
                  El aviso se marca como vencido pasada esta fecha. Sin fecha: visible indefinidamente.
                </p>
              </div>

            </div>
          </div>

          {/* ── Footer (sticky) ── */}
          <div className="flex-shrink-0 px-5 py-3 border-t border-border bg-muted/50 flex items-center justify-between gap-3">
            <Button variant="outline" onClick={handleClose} disabled={submitting}>
              Cancelar
            </Button>
            <Button variant="gradient" onClick={handleSubmit} disabled={!canSubmit}>
              {submitting ? 'Guardando…' : ctaLabel}
            </Button>
          </div>

        </DialogContent>
      </Dialog>

      {/* ── Confirm discard ── */}
      <AlertDialog open={confirmClose} onOpenChange={setConfirmClose}>
        <AlertDialogContent className="bg-dialog-bg">
          <AlertDialogHeader>
            <AlertDialogTitle>¿Descartar el aviso?</AlertDialogTitle>
            <AlertDialogDescription>
              Hay cambios sin guardar. Si cerrás, se van a perder.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Seguir editando</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleConfirmClose}
              className="bg-danger text-white hover:bg-danger/90"
            >
              Descartar
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
