import { BrowserRouter } from 'react-router-dom';
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client';
import { createQueryClient } from '@/api/queryClient';
import { OFFLINE_BUSTER, OFFLINE_MAX_AGE_MS, offlineDehydrateOptions, offlinePersister } from '@/api/offlinePersistence';
import { LazyMotion } from 'motion/react';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppRouter } from '@/routes/AppRouter';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { ErrorFallback } from '@/components/ErrorFallback';
import { Toaster } from '@/components/ui/sonner';

// Primera pantalla con fetch + paginación + invalidación real (Épica 7 Fase 3,
// ver docs/context/frontend.md) — antes no había caso de uso que lo justificara.
const queryClient = createQueryClient();

const loadMotionFeatures = () => import('@/lib/motionFeatures').then((module) => module.default);

const persistOptions = {
  persister: offlinePersister,
  maxAge: OFFLINE_MAX_AGE_MS,
  buster: OFFLINE_BUSTER,
  dehydrateOptions: offlineDehydrateOptions,
};

export function App() {
  return (
    <GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''}>
      <ThemeProvider>
        <ErrorBoundary scope="root" fallback={(props) => <ErrorFallback {...props} fullScreen />}>
          <PersistQueryClientProvider client={queryClient} persistOptions={persistOptions}>
            <BrowserRouter>
              <AuthProvider>
                <LazyMotion features={loadMotionFeatures}>
                  <AppRouter />
                </LazyMotion>
              </AuthProvider>
            </BrowserRouter>
          </PersistQueryClientProvider>
        </ErrorBoundary>
        <Toaster position="bottom-right" />
      </ThemeProvider>
    </GoogleOAuthProvider>
  );
}
