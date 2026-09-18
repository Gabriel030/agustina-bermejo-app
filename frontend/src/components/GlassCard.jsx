import { cn } from '@/lib/utils';

/*
 * La superficie ".card" de los mockups: blur + borde + sombra doble
 * (contacto sutil + elevación difusa). Ningún componente vuelve a
 * declarar estas propiedades inline — ver Diseño UI - Vidrio Líquido.
 */
export function GlassCard({ className, interactive = false, style, ...props }) {
  return (
    <div
      className={cn(
        'rounded-xl border border-border bg-surface backdrop-blur-[28px] backdrop-saturate-[1.6]',
        interactive &&
          'cursor-pointer transition-[transform,border-color,box-shadow] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] hover:-translate-y-1 hover:border-brand/40',
        className,
      )}
      style={{
        boxShadow: '0 1px 2px oklch(30% 0.03 260 / 0.04), 0 24px 60px -20px oklch(30% 0.08 260 / 0.28)',
        ...style,
      }}
      {...props}
    />
  );
}
