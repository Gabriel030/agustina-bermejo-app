import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';

export function CatalogBlock({ query, resource, skeletonClassName = 'h-24', children }) {
  if (query.isError) {
    return <LoadErrorState bare error={query.error} onRetry={query.refetch} resource={resource} />;
  }
  if (query.isLoading) {
    return <Skeleton className={skeletonClassName} />;
  }
  return children;
}
