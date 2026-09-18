import { Children } from 'react';

// Envuelve cada hijo directo para que entre con una demora creciente (60ms
// entre uno y el siguiente) en vez de aparecer todos juntos — pensado para
// grids/listas de tarjetas (StatCard, Padrón, Catálogo). No reemplaza el
// contenedor grid/flex existente: cada hijo pasa a ser su propio wrapper,
// así hereda el layout que ya define el padre.
export function Stagger({ children, as: Component = 'div', className, delayStep = 60 }) {
  return (
    <>
      {Children.map(children, (child, index) => (
        <Component
          key={child?.key ?? index}
          className={className}
          style={{
            animation: 'stagger-in 0.4s cubic-bezier(0.22, 1, 0.36, 1) both',
            animationDelay: `${index * delayStep}ms`,
          }}
        >
          {child}
        </Component>
      ))}
    </>
  );
}
