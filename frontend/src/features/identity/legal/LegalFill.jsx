export function LegalFill({ children }) {
  return (
    <mark className="whitespace-nowrap rounded bg-accent-soft px-1.5 font-bold text-primary">[{children}]</mark>
  );
}
