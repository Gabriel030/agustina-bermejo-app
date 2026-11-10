import { Input } from '@/components/ui/input'
// Genérico — reusable por Épica 8 Fase 3 (grilla de pagos, también paginada,
// ver spec técnica de Épica 7 Fase 3). Estado local: el valor vive en la URL
// (?search=) de la pantalla que lo usa, no acá.
export function Buscador({ value, onChange, placeholder = 'Buscar…' }) {
  return (
    <Input
      type="search"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      className="md:max-w-xs"
    />
  );
}
