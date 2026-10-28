export function ViolationList({ violations, title }) {
  if (!violations?.length) return null;
  return (
    <div role="alert" className="flex flex-col gap-1.5 rounded-xl bg-danger-bg/70 px-4 py-3 text-sm text-ink">
      {title && <p className="font-semibold">{title}</p>}
      <ul className="flex list-disc flex-col gap-1 pl-5 text-danger">
        {violations.map((violation, index) => (
          <li key={`${violation.code ?? 'violation'}-${index}`}>
            <span className="text-ink">{violation.message}</span>
            {violation.code && <span className="ml-1.5 text-[11px] text-ink-faint">{violation.code}</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}
