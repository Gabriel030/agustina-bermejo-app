import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { useNextInformativeSession } from '@/features/enrollment/hooks/useCatalogOffering';
import { describeInformativeSession } from '@/features/enrollment/lib/catalogOffering';

export const INFORMATIVE_SESSIONS_PATH = '/reuniones-informativas';

export function NextInformativeSessionStrip() {
  const session = useNextInformativeSession();
  if (!session) return null;
  return (
    <aside
      aria-label="Próxima reunión informativa"
      className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-accent-soft px-4 py-3.5"
    >
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="text-size-11 font-bold uppercase tracking-wider text-primary">Próxima reunión informativa</span>
        <span className="font-heading text-size-15 font-semibold text-ink">{session.title}</span>
        <span className="text-size-13 text-ink-soft">{describeInformativeSession(session)}</span>
      </div>
      <Button asChild variant="primary" className="min-h-11 max-sm:w-full">
        <Link to={INFORMATIVE_SESSIONS_PATH}>Ver reuniones</Link>
      </Button>
    </aside>
  );
}
