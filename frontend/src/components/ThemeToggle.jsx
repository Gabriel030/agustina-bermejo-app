import { Moon, Sun } from 'lucide-react';
import { useTheme } from '@/context/ThemeContext';

export function ThemeToggle() {
  const { tema, alternarTema } = useTheme();
  const oscuro = tema === 'dark';

  return (
    <button
      type="button"
      onClick={alternarTema}
      aria-pressed={oscuro}
      aria-label={oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
      className={`relative h-8 w-14 shrink-0 rounded-full border border-border transition-colors duration-300 ${
        oscuro ? 'bg-gradient-to-br from-brand to-accent-2' : 'bg-neutral-bg'
      }`}
    >
      <span
        className={`absolute top-1 left-1 flex h-6 w-6 items-center justify-center rounded-full bg-surface shadow-sm transition-transform duration-300 ease-[cubic-bezier(0.34,1.56,0.64,1)] ${
          oscuro ? 'translate-x-6' : 'translate-x-0'
        }`}
      >
        {oscuro ? <Moon className="h-3.5 w-3.5 text-brand" /> : <Sun className="h-3.5 w-3.5 text-ink-faint" />}
      </span>
    </button>
  );
}
