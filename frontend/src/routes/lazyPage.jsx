import { Suspense, lazy } from 'react';
import { CargaInicial } from '@/pages/CargaInicial';

export function lazyPage(loader, exportName) {
  const Page = lazy(() => loader().then((module) => ({ default: module[exportName] })));
  function LazyPage(props) {
    return (
      <Suspense fallback={<CargaInicial />}>
        <Page {...props} />
      </Suspense>
    );
  }
  LazyPage.displayName = exportName;
  LazyPage.preload = () => loader().then((module) => module[exportName]);
  return LazyPage;
}
