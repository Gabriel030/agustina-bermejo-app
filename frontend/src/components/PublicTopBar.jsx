import { Link, useLocation } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { useAuth } from '@/context/AuthContext';
import { homePathFor } from '@/lib/activeRole';

const LOGIN_ACTION = { to: '/login', label: 'Iniciar sesión', variant: 'outline' };
const REGISTER_ACTION = { to: '/registrarse', label: 'Registrarse', variant: 'primary' };

const ACTIONS_BY_PATH = {
  '/legal/terminos': [LOGIN_ACTION, REGISTER_ACTION],
  '/legal/privacidad': [LOGIN_ACTION, REGISTER_ACTION],
  '/olvide-mi-contrasena': [LOGIN_ACTION],
};

export function PublicTopBar() {
  const { pathname } = useLocation();
  const { usuario } = useAuth();
  const actions = usuario ? [] : (ACTIONS_BY_PATH[pathname] ?? []);
  const homePath = usuario ? homePathFor(usuario) : '/login';

  return (
    <header className="mx-auto mt-4 w-[calc(100%-2rem)] max-w-[1248px] rounded-card border border-border bg-surface">
      <nav aria-label="Cuenta" className="flex min-h-14 items-center justify-between gap-2 px-3">
        <Link
          to={homePath}
          aria-label="Ir al inicio"
          className="min-w-0 shrink rounded-control py-2 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring"
        >
          <InstitutionBrand layout="horizontal" className="max-sm:[&>div:last-child]:hidden" />
        </Link>
        {actions.length > 0 && (
          <div className="flex shrink-0 items-center gap-2">
            {actions.map((action) => (
              <Button key={action.to} asChild variant={action.variant} className="h-11 motion-reduce:transition-none">
                <Link to={action.to}>{action.label}</Link>
              </Button>
            ))}
          </div>
        )}
      </nav>
    </header>
  );
}
