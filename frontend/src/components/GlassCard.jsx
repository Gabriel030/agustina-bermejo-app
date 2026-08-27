import { cn } from '@/lib/utils';

/*
 * La superficie ".card" de los mockups: blur + borde + sombra doble
 * (contacto sutil + elevación difusa). Ningún componente vuelve a
 * declarar estas propiedades inline — ver Diseño UI - Vidrio Líquido.
 */
export function GlassCard({ className, ...props }) {
  return (
    <div
      className={cn('rounded-xl border border-border bg-surface backdrop-blur-[28px] backdrop-saturate-[1.6]', className)}
      style={{
        boxShadow:
          '0 1px 2px oklch(30% 0.03 260 / 0.04), 0 24px 60px -20px oklch(30% 0.08 260 / 0.28)',
      }}
      {...props}
    />
  );
}
