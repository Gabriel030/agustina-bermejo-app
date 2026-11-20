import { useId, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { GlassCard } from '@/components/GlassCard';
import { Switch } from '@/components/ui/switch';
import { readAskAlways, selectableRoles, writeAskAlways } from '@/lib/activeRole';

export function RoleEntryPreference() {
  const { usuario } = useAuth();
  const switchId = useId();
  const [askAlways, setAskAlways] = useState(() => (usuario ? readAskAlways(usuario.id) : false));

  if (!usuario || selectableRoles(usuario.roles).length < 2) return null;

  function handleChange(value) {
    setAskAlways(value);
    writeAskAlways(usuario.id, value);
  }

  return (
    <GlassCard className="flex items-center justify-between gap-4 p-5 sm:p-7">
      <div>
        <label htmlFor={switchId} className="text-sm font-semibold text-ink">
          Preguntarme el rol al entrar
        </label>
        <p className="mt-1 text-size-13 text-ink-faint">
          Si lo dejás apagado, entrás directo con el último rol que usaste.
        </p>
      </div>
      <Switch id={switchId} checked={askAlways} onCheckedChange={handleChange} />
    </GlassCard>
  );
}
