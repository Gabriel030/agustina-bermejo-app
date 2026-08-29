import { GlassCard } from '@/components/GlassCard';

// Tarjeta de estadística (label chico en mayúscula + contenido grande) — antes
// reescrita a mano en Mis carreras (3 veces: 2 números, 1 barra de progreso).
// `children` es el contenido (número, número/total, barra) — deliberadamente
// libre en vez de props rígidas de "value/total", porque el contenido varía
// bastante entre usos (ver Mis carreras: "Avance del plan" es una barra, no
// un número).
export function StatCard({ label, children, className }) {
  return (
    <GlassCard className={`p-5 ${className ?? ''}`}>
      <div className="text-[11px] font-bold uppercase tracking-wide text-ink-faint">{label}</div>
      <div className="mt-1">{children}</div>
    </GlassCard>
  );
}
