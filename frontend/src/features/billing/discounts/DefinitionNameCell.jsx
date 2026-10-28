import { Link } from 'react-router-dom';

export function DefinitionNameCell({ entry, basePath, subtitle }) {
  return (
    <div className="flex min-w-0 flex-col text-left">
      <Link
        to={`${basePath}/${entry.id}`}
        className="font-semibold text-ink hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        {entry.name}
      </Link>
      {subtitle && subtitle !== entry.name && <span className="text-xs text-ink-faint">{subtitle}</span>}
    </div>
  );
}
