export function SkipToContent({ targetId = 'main-content' }) {
  return (
    <a
      href={`#${targetId}`}
      className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] focus:rounded-xl focus:bg-surface focus:px-4 focus:py-2 focus:text-ink focus:ring-2 focus:ring-ring"
    >
      Saltar al contenido
    </a>
  );
}
