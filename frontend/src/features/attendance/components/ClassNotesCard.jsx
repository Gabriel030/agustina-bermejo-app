import { Ban } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

export function ClassNotesCard({ topic, onTopicChange, observations, onObservationsChange, canceled, onCancelClass }) {
  return (
    <GlassCard role="region" aria-label="Datos de la clase" className="flex flex-col gap-4 p-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="class-topic" className="font-heading text-size-14 font-bold">Temario</Label>
          <Textarea id="class-topic" value={topic} onChange={(e) => onTopicChange(e.target.value.slice(0, 1000))} placeholder="Escriba los temas desarrollados durante la clase..." />
          <div className="text-right text-size-12 tabular-nums text-ink-faint">{topic.length} / 1000 caracteres</div>
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="class-observations" className="font-heading text-size-14 font-bold">Observaciones (opcional)</Label>
          <Textarea id="class-observations" value={observations} onChange={(e) => onObservationsChange(e.target.value.slice(0, 500))} placeholder="Observaciones de la clase..." />
          <div className="text-right text-size-12 tabular-nums text-ink-faint">{observations.length} / 500 caracteres</div>
        </div>
      </div>
      <div className="border-t border-border pt-3">
        <Button variant="destructive" className="min-h-11" onClick={onCancelClass} disabled={canceled}>
          <Ban aria-hidden="true" />
          Cancelar clase
        </Button>
      </div>
    </GlassCard>
  );
}
