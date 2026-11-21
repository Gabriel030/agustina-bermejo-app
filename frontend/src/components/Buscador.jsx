import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
// Genérico — reusable por Épica 8 Fase 3 (grilla de pagos, también paginada,
// ver spec técnica de Épica 7 Fase 3). Estado local: el valor vive en la URL
// (?search=) de la pantalla que lo usa, no acá.
export function Buscador({ value, onChange, placeholder = 'Buscar…', ariaLabel, className }) {
  return (
    <Input
      type="search"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      aria-label={ariaLabel}
      className={cn('md:max-w-xs', className)}
    />
  );
}
