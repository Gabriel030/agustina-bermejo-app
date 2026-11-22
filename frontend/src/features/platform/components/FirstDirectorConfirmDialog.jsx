import { AlertTriangle } from 'lucide-react';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { documentLabel } from '@/lib/documentType';

function SummaryItem({ label, children }) {
  return (
    <div>
      <dt className="text-xs font-extrabold uppercase tracking-wide text-ink-faint">{label}</dt>
      <dd className="mt-0.5 break-words font-bold text-ink">{children}</dd>
    </div>
  );
}

export function FirstDirectorConfirmDialog({ open, onOpenChange, form, onConfirm }) {
  return (
    <ConfirmActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title="¿Crear el primer Director?"
      description={
        <span className="flex items-center gap-2 rounded-md bg-warning-bg px-3 py-2.5 font-bold text-warning-ink">
          <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
          Se envía un email y no se puede deshacer.
        </span>
      }
      buttonClassName="h-11"
      confirmLabel="Sí, crear Director"
      confirmVariant="primary"
      onConfirm={onConfirm}
    >
      <dl className="grid gap-2.5 text-sm">
        <SummaryItem label="Institución">{form.institutionName.trim()}</SummaryItem>
        <SummaryItem label="Director">
          {form.firstName.trim()} {form.lastName.trim()} · <span className="tabular-nums">{documentLabel(form.documentType)} {form.documentNumber.trim()}</span>
        </SummaryItem>
        <SummaryItem label="Email para crear su contraseña"><span className="text-brand">{form.email.trim()}</span></SummaryItem>
      </dl>
    </ConfirmActionDialog>
  );
}
