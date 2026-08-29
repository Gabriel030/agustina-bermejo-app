import { cn } from '@/lib/utils';

// Nombre de la institución — despliegue de una sola institución (ver
// docs/context/frontend.md), no viene de ningún endpoint, se resuelve solo.
const NOMBRE_INSTITUCION = 'Agustina Bermejo';
const INICIALES_INSTITUCION = 'AB';

const TAMANIOS = {
  sm: { avatar: 'h-9 w-9 rounded-xl text-sm', nombre: 'text-[13px] font-bold' },
  lg: { avatar: 'h-[52px] w-[52px] rounded-[17px] text-xl', nombre: 'text-lg font-bold tracking-tight' },
};

// Marca de la institución (avatar con iniciales + nombre) — antes duplicada a
// mano en Login/Registrarse (versión centrada) y ahora también en Sidebar/
// MobileMenu (versión compacta horizontal). `subtitulo` es el texto chico
// debajo del título, solo tiene sentido en la variante vertical. `titulo`
// pisa el nombre de la institución cuando la pantalla necesita su propio
// título ahí (ej. Registrarse: "Creá tu cuenta") sin dejar de mostrar la
// marca — si no se pasa, muestra el nombre de la institución.
export function InstitutionBrand({
  layout = 'vertical',
  size = layout === 'vertical' ? 'lg' : 'sm',
  titulo = NOMBRE_INSTITUCION,
  subtitulo,
  className,
}) {
  const t = TAMANIOS[size];

  return (
    <div className={cn('flex items-center gap-3', layout === 'vertical' && 'flex-col text-center', className)}>
      <div
        className={cn(
          'flex shrink-0 items-center justify-center bg-gradient-to-br from-accent-2 to-accent font-heading font-bold text-white',
          t.avatar,
        )}
      >
        {INICIALES_INSTITUCION}
      </div>
      <div className={layout === 'vertical' ? 'text-center' : 'min-w-0'}>
        <div className={cn('truncate font-heading text-ink', t.nombre)}>{titulo}</div>
        {subtitulo && <div className="mt-0.5 text-sm text-ink-faint">{subtitulo}</div>}
      </div>
    </div>
  );
}
