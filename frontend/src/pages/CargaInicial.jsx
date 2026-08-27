import { MeshBackground } from '@/components/MeshBackground';

// Se muestra mientras se resuelve el refresh de sesión al recargar la página
// (AuthContext.cargando) — evita un parpadeo hacia /login si la sesión sigue vigente.
export function CargaInicial() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <MeshBackground />
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-accent border-t-transparent" />
    </div>
  );
}
