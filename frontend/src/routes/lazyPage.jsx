import { Suspense, lazy } from 'react';
import { CargaInicial } from '@/pages/CargaInicial';

export function lazyPage(loader, exportName) {
  const createPage = () =>
    lazy(() =>
      loader().then(
        (module) => ({ default: module[exportName] }),
        (error) => {
          Page = createPage();
          throw error;
        },
      ),
    );
  let Page = createPage();
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
