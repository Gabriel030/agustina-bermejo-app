export function MeshBackground() {
  return (
    <div className="fixed inset-0 -z-10 overflow-hidden">
      <div className="absolute inset-0" style={{ background: 'var(--page-bg)' }} />
      <div
        className="mesh-blob absolute -top-44 -left-36 h-[620px] w-[620px] rounded-full blur-md"
        style={{ background: 'radial-gradient(circle, var(--accent-2) 0%, transparent 70%)', opacity: 0.4 }}
      />
      <div
        className="mesh-blob absolute -bottom-56 -right-40 h-[680px] w-[680px] rounded-full blur-md"
        style={{ background: 'radial-gradient(circle, var(--accent) 0%, transparent 70%)', opacity: 0.35 }}
      />
    </div>
  );
}
