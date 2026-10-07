import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

export function EditarPersonalModal({ open, onClose, legajo, onConfirmar, isPending }) {
  const [form, setForm] = useState({ email: '', telefono: '', street: '', number: '', floor: '', city: '', state: '', postalCode: '', country: '' });

  useEffect(() => {
    if (legajo) {
      setForm({
        email: legajo.email ?? '',
        telefono: legajo.telefono ?? '',
        street: legajo.domicilio?.street ?? '',
        number: legajo.domicilio?.number ?? '',
        floor: legajo.domicilio?.floor ?? '',
        city: legajo.domicilio?.city ?? '',
        state: legajo.domicilio?.state ?? '',
        postalCode: legajo.domicilio?.postalCode ?? '',
        country: legajo.domicilio?.country ?? '',
      });
    }
  }, [legajo, open]);

  function set(field) {
    return (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }));
  }

  function handleClose() {
    onClose();
  }

  function handleConfirmar() {
    onConfirmar({
      email: form.email || undefined,
      telefono: form.telefono || undefined,
      street: form.street || undefined,
      number: form.number || undefined,
      floor: form.floor || undefined,
      city: form.city || undefined,
      state: form.state || undefined,
      postalCode: form.postalCode || undefined,
      country: form.country || undefined,
    });
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && handleClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Editar datos de contacto</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Email</label>
              <Input type="email" value={form.email} onChange={set('email')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Teléfono</label>
              <Input value={form.telefono} onChange={set('telefono')} placeholder="+54 11..." />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Calle</label>
              <Input value={form.street} onChange={set('street')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Número</label>
              <Input value={form.number} onChange={set('number')} />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Piso</label>
              <Input value={form.floor} onChange={set('floor')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Ciudad</label>
              <Input value={form.city} onChange={set('city')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">CP</label>
              <Input value={form.postalCode} onChange={set('postalCode')} />
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isPending}>
            Cancelar
          </Button>
          <Button variant="gradient" onClick={handleConfirmar} disabled={isPending}>
            {isPending ? 'Guardando...' : 'Guardar cambios'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
