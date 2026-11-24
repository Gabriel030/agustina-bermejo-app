import { Lock } from 'lucide-react';
import { Notice } from '@/components/Notice';

export function PersonalDataNotice({ className }) {
  return (
    <Notice className={className}>
      <span className="flex items-start gap-2">
        <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        <span>No tenés permiso para ver datos personales. Se muestra el número de persona; nombres y mails quedan ocultos.</span>
      </span>
    </Notice>
  );
}
