import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

const MS_POR_DIA = 24 * 60 * 60 * 1000;

function paraInput(iso) {
  return iso ? iso.slice(0, 16) : '';
}

function conSegundos(valor) {
  return valor.length === 16 ? `${valor}:00` : valor;
}

function mostrarFechaHora(valor) {
  if (!valor) return '—';
  return new Date(valor).toLocaleString('es-AR', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function diferenciaFecha(antes, despues) {
  if (!antes || !despues) return antes === despues ? 'Sin cambios' : 'Cambia';
  const dias = Math.round((new Date(despues) - new Date(antes)) / MS_POR_DIA);
  if (dias === 0) return new Date(despues).getTime() === new Date(antes).getTime() ? 'Sin cambios' : 'Cambia el horario';
  const abs = Math.abs(dias);
  return `${dias > 0 ? '+' : '−'}${abs} ${abs === 1 ? 'día' : 'días'}`;
}

function diferenciaTexto(antes, despues) {
  return (antes ?? '') === (despues ?? '') ? 'Sin cambios' : 'Cambia';
}

function filasCambio(mesa, nuevo) {
  return [
    { clave: 'examDate', etiqueta: 'Fecha del examen', hoy: mostrarFechaHora(mesa.examDate), nuevo: mostrarFechaHora(nuevo.examDate), diferencia: diferenciaFecha(paraInput(mesa.examDate), nuevo.examDate) },
    { clave: 'classroom', etiqueta: 'Aula', hoy: mesa.classroom || '—', nuevo: nuevo.classroom || '—', diferencia: diferenciaTexto(mesa.classroom, nuevo.classroom) },
    { clave: 'registrationStart', etiqueta: 'Inicio de inscripción', hoy: mostrarFechaHora(mesa.registrationStart), nuevo: mostrarFechaHora(nuevo.registrationStart), diferencia: diferenciaFecha(paraInput(mesa.registrationStart), nuevo.registrationStart) },
    { clave: 'registrationEnd', etiqueta: 'Fin de inscripción', hoy: mostrarFechaHora(mesa.registrationEnd), nuevo: mostrarFechaHora(nuevo.registrationEnd), diferencia: diferenciaFecha(paraInput(mesa.registrationEnd), nuevo.registrationEnd) },
  ];
}

export function ReprogramarMesaDialog({ open, onClose, mesa, onConfirmar, enviando }) {
  const [paso, setPaso] = useState(1);
  const [form, setForm] = useState({
    examDate: paraInput(mesa.examDate),
    classroom: mesa.classroom ?? '',
    registrationStart: paraInput(mesa.registrationStart),
    registrationEnd: paraInput(mesa.registrationEnd),
  });

  function cambiar(campo) {
    return (e) => setForm((actual) => ({ ...actual, [campo]: e.target.value }));
  }

  const filas = filasCambio(mesa, form);
  const hayCambios = filas.some((f) => f.diferencia !== 'Sin cambios');
  const ventanaInvalida = !!form.registrationStart && !!form.registrationEnd && form.registrationEnd < form.registrationStart;

  function confirmar() {
    const payload = { examDate: conSegundos(form.examDate) };
    if (form.classroom) payload.classroom = form.classroom;
    if (form.registrationStart) payload.registrationStart = conSegundos(form.registrationStart);
    if (form.registrationEnd) payload.registrationEnd = conSegundos(form.registrationEnd);
    onConfirmar(payload);
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{paso === 1 ? 'Reprogramar mesa' : 'Confirmá los cambios'}</DialogTitle>
          <DialogDescription>
            {paso === 1 ? 'Elegí la nueva fecha y, si hace falta, el aula y la ventana de inscripción.' : 'Revisá qué cambia antes de reprogramar la mesa.'}
          </DialogDescription>
        </DialogHeader>

        {paso === 1 ? (
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="reprogramar-fecha">Fecha y hora del examen</Label>
              <Input id="reprogramar-fecha" type="datetime-local" value={form.examDate} onChange={cambiar('examDate')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="reprogramar-aula">Aula</Label>
              <Input id="reprogramar-aula" value={form.classroom} maxLength={255} onChange={cambiar('classroom')} />
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="reprogramar-inicio">Inicio de inscripción</Label>
                <Input id="reprogramar-inicio" type="datetime-local" value={form.registrationStart} onChange={cambiar('registrationStart')} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="reprogramar-fin">Fin de inscripción</Label>
                <Input id="reprogramar-fin" type="datetime-local" value={form.registrationEnd} onChange={cambiar('registrationEnd')} />
              </div>
            </div>
            {ventanaInvalida && (
              <p className="text-sm font-semibold text-danger" role="alert">El fin de la inscripción no puede ser anterior al inicio.</p>
            )}
          </div>
        ) : (
          <div className="flex flex-col gap-1">
            <div className="grid grid-cols-[1.4fr_1.2fr_1.2fr_1fr] gap-2 border-b border-border px-1 pb-2 text-[11px] font-bold uppercase tracking-wide text-ink-faint">
              <div>Dato</div><div>Hoy</div><div>Nuevo</div><div>Diferencia</div>
            </div>
            {filas.map((f) => (
              <div key={f.clave} className="grid grid-cols-[1.4fr_1.2fr_1.2fr_1fr] items-center gap-2 px-1 py-2 text-[13px]">
                <div className="font-semibold text-ink">{f.etiqueta}</div>
                <div className="text-ink-soft">{f.hoy}</div>
                <div className="font-semibold text-ink">{f.nuevo}</div>
                <div className={f.diferencia === 'Sin cambios' ? 'text-ink-faint' : 'font-semibold text-primary'}>{f.diferencia}</div>
              </div>
            ))}
          </div>
        )}

        <DialogFooter>
          {paso === 1 ? (
            <>
              <Button variant="outline" onClick={onClose}>Cerrar</Button>
              <Button variant="gradient" disabled={!form.examDate || ventanaInvalida || !hayCambios} onClick={() => setPaso(2)}>
                Revisar cambios
              </Button>
            </>
          ) : (
            <>
              <Button variant="outline" disabled={enviando} onClick={() => setPaso(1)}>Volver</Button>
              <Button variant="gradient" disabled={enviando} onClick={confirmar}>Confirmar reprogramación</Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
