import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { approvalTotals, closedWithoutTeacher } from '../lib/closureSheet';

const MAX_FIELD_LENGTH = 50;

function Totals({ sheet }) {
  const totals = approvalTotals(sheet);
  return (
    <ul className="flex flex-col gap-1 text-sm text-ink">
      <li>
        <b>{totals.promote}</b> promocionan
      </li>
      <li>
        <b>{totals.goToFinal}</b> van a final
      </li>
      <li>
        <b>{totals.failed}</b> desaprobados o libres
      </li>
    </ul>
  );
}

export function ClosureApproveDialog({ open, sheet, pending, onClose, onApprove }) {
  const [step, setStep] = useState('location');
  const [book, setBook] = useState('');
  const [folio, setFolio] = useState('');
  const [error, setError] = useState('');
  const withoutTeacher = closedWithoutTeacher(sheet);

  function close() {
    setStep('location');
    setError('');
    onClose();
  }

  function goToReview() {
    if (!book.trim() || !folio.trim()) {
      setError('Cargá el libro y el folio donde se asienta el acta.');
      return;
    }
    setError('');
    setStep('review');
  }

  async function approve() {
    const done = await onApprove({ book: book.trim(), folio: folio.trim(), closeWithoutTeacherConfirmation: withoutTeacher });
    if (done) close();
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && !pending && close()}>
      <DialogContent>
        {step === 'location' ? (
          <>
            <DialogHeader>
              <DialogTitle>{withoutTeacher ? 'Cerrar sin confirmación del docente' : 'Aprobar y cerrar'}</DialogTitle>
              <DialogDescription>Paso 1 de 2. Cargá dónde se asienta el acta.</DialogDescription>
            </DialogHeader>
            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="closure-book">Libro</Label>
                <Input id="closure-book" value={book} maxLength={MAX_FIELD_LENGTH} placeholder="L28" onChange={(event) => setBook(event.target.value)} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="closure-folio">Folio</Label>
                <Input id="closure-folio" value={folio} maxLength={MAX_FIELD_LENGTH} placeholder="93" onChange={(event) => setFolio(event.target.value)} />
              </div>
            </div>
            {error && (
              <div role="alert" className="text-size-12 font-semibold text-danger">
                {error}
              </div>
            )}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={close}>
                Cancelar
              </Button>
              <Button type="button" variant="gradient" onClick={goToReview}>
                Continuar
              </Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>Confirmá el cierre del acta</DialogTitle>
              <DialogDescription>
                Paso 2 de 2. Se emite el acta numerada en el libro {book.trim()}, folio {folio.trim()}, y no se puede modificar.
                {withoutTeacher ? ' El acta deja constancia de que el docente no confirmó.' : ''}
              </DialogDescription>
            </DialogHeader>
            <Totals sheet={sheet} />
            <DialogFooter>
              <Button type="button" variant="outline" disabled={pending} onClick={() => setStep('location')}>
                Volver
              </Button>
              <Button type="button" variant="gradient" loading={pending} onClick={approve}>
                {withoutTeacher ? 'Cerrar el acta' : 'Aprobar y cerrar'}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
