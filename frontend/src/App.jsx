import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '@/api/queryClient';
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

export function App() {
  return (
    <GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''}>
      <ThemeProvider>
        <ErrorBoundary scope="root" fallback={(props) => <ErrorFallback {...props} fullScreen />}>
          <QueryClientProvider client={queryClient}>
            <BrowserRouter>
              <AuthProvider>
                <AppRouter />
              </AuthProvider>
            </BrowserRouter>
          </QueryClientProvider>
        </ErrorBoundary>
        <Toaster position="bottom-right" />
      </ThemeProvider>
    </GoogleOAuthProvider>
  );
}
