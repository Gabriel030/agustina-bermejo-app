import { useId, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuth } from '@/context/AuthContext';
import { GlassCard } from '@/components/GlassCard';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { MeshBackground } from '@/components/MeshBackground';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import {
  homePathFor,
  readAskAlways,
  readLastRole,
  roleLabel,
  rolePath,
  selectableRoles,
  writeAskAlways,
  writeLastRole,
} from '@/lib/activeRole';

const ROLE_HINTS = {
  DIRECTOR: 'Gestión general de la institución',
  ADMINISTRATIVO: 'Alumnos, inscripciones y cobros',
  PRECEPTOR: 'Asistencia y seguimiento de cursos',
  DOCENTE: 'Tus comisiones, notas y asistencia',
  ALUMNO: 'Tus cursos, notas y pagos',
};

function RoleOption({ role, checked, onSelect }) {
  return (
    <label
      className={cn(
        'flex min-h-14 cursor-pointer items-center justify-between gap-3 rounded-2xl border px-4 py-3 transition-colors focus-within:ring-3 focus-within:ring-ring/50',
        checked ? 'border-brand bg-accent-soft' : 'border-border bg-surface hover:bg-neutral-bg/60',
      )}
    >
      <input
        type="radio"
        name="role"
        value={role}
        checked={checked}
        onChange={() => onSelect(role)}
        className="sr-only"
      />
      <span className="flex flex-col gap-0.5">
        <span className="text-sm font-semibold text-ink">{roleLabel(role)}</span>
        {ROLE_HINTS[role] && <span className="text-xs text-ink-soft">{ROLE_HINTS[role]}</span>}
      </span>
      {checked && <Check aria-hidden="true" className="h-4 w-4 shrink-0 text-brand" />}
    </label>
  );
}

export function RolePicker() {
  const { usuario, logout } = useAuth();
  const navigate = useNavigate();
  const askId = useId();
  const choices = selectableRoles(usuario?.roles);
  const lastRole = usuario ? readLastRole(usuario.id) : null;
  const [selected, setSelected] = useState(choices.includes(lastRole) ? lastRole : choices[0]);
  const [askAlways, setAskAlways] = useState(() => (usuario ? readAskAlways(usuario.id) : false));

  if (!usuario || choices.length < 2) return <Navigate to={homePathFor(usuario)} replace />;

  function handleSubmit(event) {
    event.preventDefault();
    writeLastRole(usuario.id, selected);
    writeAskAlways(usuario.id, askAlways);
    navigate(rolePath(selected), { replace: true });
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <MeshBackground />
      <GlassCard className="w-full max-w-md p-6 sm:p-8">
        <InstitutionBrand layout="horizontal" />
        <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-5">
          <fieldset className="flex flex-col gap-3">
            <legend className="mb-3">
              <h1 className="font-heading text-xl font-bold tracking-tight text-ink">Elegí cómo querés entrar</h1>
              <p className="mt-1 text-size-13 text-ink-faint">
                {usuario.nombre}, tenés más de un rol. Después podés cambiar desde el menú.
              </p>
            </legend>
            {choices.map((role) => (
              <RoleOption key={role} role={role} checked={role === selected} onSelect={setSelected} />
            ))}
          </fieldset>
          <div className="flex items-center gap-2.5">
            <Checkbox id={askId} checked={askAlways} onCheckedChange={(value) => setAskAlways(value === true)} />
            <label htmlFor={askId} className="text-size-13 text-ink-soft">
              Preguntarme el rol al entrar
            </label>
          </div>
          <div className="flex flex-col gap-2">
            <Button type="submit" variant="gradient" className="w-full">
              Entrar como {roleLabel(selected)}
            </Button>
            <Button type="button" variant="ghost" className="w-full" onClick={logout}>
              No soy {usuario.nombre}: cerrar sesión
            </Button>
          </div>
        </form>
      </GlassCard>
    </div>
  );
}
