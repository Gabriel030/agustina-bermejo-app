import { useMemo } from 'react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/EmptyState';
import { useAuth } from '@/context/AuthContext';
import { ExceptionsTable } from '@/features/graduation/certificate-exceptions/components/ExceptionsTable';
import { useCertificateExceptions, useDescribedExceptions } from '@/features/graduation/certificate-exceptions/hooks/useCertificateExceptions';

const detailPath = (id) => `/administrativo/constancias/excepciones/${id}`;

export function OwnExceptionsTab({ onGoToRequests }) {
  const { usuario } = useAuth();
  const query = useCertificateExceptions();
  const own = useMemo(() => query.data?.filter((exception) => exception.requestedById === usuario?.id), [query.data, usuario?.id]);
  const { rows } = useDescribedExceptions(own);
  return (
    <ExceptionsTable
      rows={rows}
      query={query}
      detailPath={detailPath}
      errorMessage="No pudimos cargar tus excepciones"
      emptyState={
        <EmptyState
          title="Todavía no pediste ninguna excepción"
          description="Cuando pidas una desde Pedidos, la seguís desde acá."
          action={
            <Button type="button" variant="outline" className="min-h-11 md:min-h-0" onClick={onGoToRequests}>
              Ir a los pedidos
            </Button>
          }
        />
      }
    />
  );
}
