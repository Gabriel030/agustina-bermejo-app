import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';

// Ruta de navegación estilo Finder — jerarquía de dominio ("dónde está la
// carpeta"), no historial real de clicks ("por dónde entré") — decisión de
// UX (ver spec Épica 2 Fase 2, useBreadcrumb.js). Cada item intermedio es un
// Link con destino determinístico; el último (acá estás) no es clickeable.
export function Breadcrumb({ items }) {
  if (!items || items.length === 0) return null;

  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-[13px]">
      {items.map((item, i) => {
        const esUltimo = i === items.length - 1;
        return (
          <span key={item.to ?? item.label} className="flex items-center gap-1.5">
            {i > 0 && <ChevronRight className="h-3.5 w-3.5 text-ink-faint" />}
            {esUltimo || !item.to ? (
              <span className="font-semibold text-ink">{item.label}</span>
            ) : (
              <Link to={item.to} className="font-medium text-ink-faint hover:text-primary">
                {item.label}
              </Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
