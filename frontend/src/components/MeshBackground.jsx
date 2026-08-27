/*
 * Fondo mesh de 3 tonos + dos manchas radiales desenfocadas — el fondo base de
 * todas las pantallas del sistema (ver Diseño UI - Vidrio Líquido).
 */
export function MeshBackground() {
  return (
    <div className="fixed inset-0 -z-10 overflow-hidden">
      <div
        className="absolute inset-0"
        style={{ background: 'linear-gradient(160deg, var(--bg1), var(--bg2) 55%, var(--bg3))' }}
      />
      <div
        className="absolute -top-44 -left-36 h-[620px] w-[620px] rounded-full blur-md"
        style={{ background: 'radial-gradient(circle, var(--accent-2) 0%, transparent 70%)', opacity: 0.4 }}
      />
      <div
        className="absolute -bottom-56 -right-40 h-[680px] w-[680px] rounded-full blur-md"
        style={{ background: 'radial-gradient(circle, var(--accent) 0%, transparent 70%)', opacity: 0.35 }}
      />
    </div>
  );
}
