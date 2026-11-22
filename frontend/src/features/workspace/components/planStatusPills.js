import { CheckCircle2, Clock, Flag, Lock, Plus, XCircle } from 'lucide-react';

export const PLAN_STATUS_PILL = {
  'en-curso': { label: 'En curso', tone: 'accent', Icon: Clock },
  regular: { label: 'Cursada regular · falta final', tone: 'warning', Icon: Flag },
  aprobada: { label: 'Aprobada', tone: 'success', Icon: CheckCircle2 },
  desaprobada: { label: 'Desaprobada · a recursar', tone: 'danger', Icon: XCircle },
  disponible: { label: 'Disponible', tone: 'neutral', Icon: Plus },
  bloqueada: { label: 'Bloqueada', tone: 'neutral', Icon: Lock },
};
