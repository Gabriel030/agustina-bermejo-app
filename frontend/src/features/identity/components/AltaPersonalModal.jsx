import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { DocumentField } from '@/components/DocumentField';
import { validateDocument } from '@/lib/documentType';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useAuth } from '@/context/AuthContext';
import { STAFF_ROLE_LABELS, manageableStaffRoles } from '@/features/identity/staffRoles';

const EMPTY = {
  nombre: '', apellido: '', email: '', documentType: 'DNI', dni: '', role: '',
  street: '', number: '', floor: '', city: '', state: '', postalCode: '', country: '',
};

export function AltaPersonalModal({ open, onClose, onConfirmar, isPending }) {
  const { usuario } = useAuth();
  const rolesDisponibles = manageableStaffRoles(usuario?.roles).map((value) => ({ value, label: STAFF_ROLE_LABELS[value] }));

  const [form, setForm] = useState(EMPTY);

  function set(field) {
    return (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }));
  }

  function handleClose() {
    setForm(EMPTY);
    onClose();
  }

  function handleConfirmar() {
    onConfirmar({
      nombre: form.nombre, apellido: form.apellido, email: form.email,
      documentType: form.documentType, dni: form.dni, role: form.role,
      street: form.street || undefined, number: form.number || undefined,
      floor: form.floor || undefined, city: form.city || undefined,
      state: form.state || undefined, postalCode: form.postalCode || undefined,
      country: form.country || undefined,
    });
  }

  const valido = form.nombre && form.apellido && form.email && !validateDocument(form.documentType, form.dni) && form.role;

  return (
    <Dialog open={open} onOpenChange={(o) => !o && handleClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Alta de personal</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Nombre *</label>
              <Input value={form.nombre} onChange={set('nombre')} placeholder="Ej: María" />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Apellido *</label>
              <Input value={form.apellido} onChange={set('apellido')} placeholder="Ej: García" />
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-[13px] font-medium text-ink">Email *</label>
            <Input type="email" value={form.email} onChange={set('email')} placeholder="correo@ejemplo.com" />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Documento *</label>
              <DocumentField
                type={form.documentType}
                number={form.dni}
                onTypeChange={(v) => setForm((prev) => ({ ...prev, documentType: v }))}
                onNumberChange={(v) => setForm((prev) => ({ ...prev, dni: v }))}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Rol *</label>
              <Select value={form.role} onValueChange={(v) => setForm((prev) => ({ ...prev, role: v }))}>
                <SelectTrigger aria-label="Rol">
                  <SelectValue placeholder="Seleccioná un rol" />
                </SelectTrigger>
                <SelectContent>
                  {rolesDisponibles.map(({ value, label }) => (
                    <SelectItem key={value} value={value}>{label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Calle</label>
              <Input value={form.street} onChange={set('street')} placeholder="Av. Corrientes" />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Número</label>
              <Input value={form.number} onChange={set('number')} placeholder="1234" />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Piso</label>
              <Input value={form.floor} onChange={set('floor')} placeholder="2B" />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">Ciudad</label>
              <Input value={form.city} onChange={set('city')} placeholder="Buenos Aires" />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium text-ink">CP</label>
              <Input value={form.postalCode} onChange={set('postalCode')} placeholder="1000" />
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isPending}>
            Cancelar
          </Button>
          <Button variant="gradient" onClick={handleConfirmar} disabled={!valido || isPending}>
            {isPending ? 'Creando...' : 'Crear personal'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
