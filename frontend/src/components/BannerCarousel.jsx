import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { cn } from '@/lib/utils';

const INTERVALO_MS = 3000;

// Mecánica de carrusel (auto-avance + flechas + dots) — el contenido real de
// cada slide es la feature de producto todavía sin construir (carrusel
// configurable por Administrativo/Director, ver spec de Alumno · Inicio);
// acá van 5 slides genéricos de placeholder hasta que exista esa gestión.
const SLIDES = [
  { titulo: '¡Bienvenido/a a Agustina Bermejo!', texto: 'Pronto vas a ver acá novedades de tu institución.' },
  { titulo: 'Explorá el Catálogo', texto: 'Sumate a un curso corto sin salir de tu carrera.' },
  { titulo: 'Revisá tu estado de cuenta', texto: 'Mantené tus cuotas al día desde Inicio.' },
  { titulo: 'Seguí tu progreso', texto: 'Consultá tus materias aprobadas y en curso en Mis carreras.' },
  { titulo: 'Novedades institucionales', texto: 'Este espacio lo va a definir tu institución muy pronto.' },
];

export function BannerCarousel() {
  const [indice, setIndice] = useState(0);
  const [pausado, setPausado] = useState(false);

  useEffect(() => {
    if (pausado) return;
    const id = setInterval(() => setIndice((i) => (i + 1) % SLIDES.length), INTERVALO_MS);
    return () => clearInterval(id);
  }, [pausado, indice]);

  function anterior() {
    setIndice((i) => (i - 1 + SLIDES.length) % SLIDES.length);
  }

  function siguiente() {
    setIndice((i) => (i + 1) % SLIDES.length);
  }

  const slide = SLIDES[indice];

  return (
    <GlassCard
      className="relative overflow-hidden bg-gradient-to-br from-brand to-accent-2 p-6 text-white"
      onMouseEnter={() => setPausado(true)}
      onMouseLeave={() => setPausado(false)}
    >
      <div className="text-[11px] font-bold uppercase tracking-wide text-white/75">Novedad institucional</div>
      <div className="mt-1 font-heading text-lg font-bold">{slide.titulo}</div>
      <div className="mt-1 text-[13px] text-white/85">{slide.texto}</div>

      <div className="mt-4 flex items-center gap-1.5">
        {SLIDES.map((_, i) => (
          <button
            key={i}
            type="button"
            aria-label={`Ir al slide ${i + 1}`}
            onClick={() => setIndice(i)}
            className={cn('h-1.5 rounded-full transition-all', i === indice ? 'w-5 bg-white' : 'w-1.5 bg-white/40 hover:bg-white/60')}
          />
        ))}
      </div>

      <button
        type="button"
        aria-label="Slide anterior"
        onClick={anterior}
        className="absolute left-3 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/15 text-white transition-colors hover:bg-white/30"
      >
        <ChevronLeft className="h-5 w-5" />
      </button>
      <button
        type="button"
        aria-label="Slide siguiente"
        onClick={siguiente}
        className="absolute right-3 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/15 text-white transition-colors hover:bg-white/30"
      >
        <ChevronRight className="h-5 w-5" />
      </button>
    </GlassCard>
  );
}
