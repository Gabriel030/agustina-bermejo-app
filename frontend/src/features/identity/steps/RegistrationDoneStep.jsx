import { CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { missingDocumentsSummary } from '@/features/identity/lib/studentRegistration';

export function RegistrationDoneStep({ fullName, email, missingDocuments, onOpenRecord, onRegisterAnother }) {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-2 text-success" role="status">
        <CheckCircle2 size={20} />
        <h2 className="text-base font-semibold">Alta confirmada</h2>
      </div>
      <p className="text-size-13 text-ink-soft">
        {fullName} ya está registrado. Le enviamos un email a <strong>{email}</strong> para que elija su contraseña; ingresa con su documento como usuario.
      </p>
      <p className="text-size-13 text-ink">{missingDocumentsSummary(missingDocuments)}</p>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="button" variant="gradient" onClick={onOpenRecord}>Ir al legajo</Button>
        <Button type="button" variant="outline" onClick={onRegisterAnother}>Dar de alta a otro</Button>
      </div>
    </div>
  );
}
