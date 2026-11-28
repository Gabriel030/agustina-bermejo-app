import { StatusPill } from '@/components/StatusPill';
import { HELP_ROLE_LABELS } from '@/features/help/lib/helpTasks';
import { HelpMarkdown } from '@/features/help/components/HelpMarkdown';

export function HelpTaskArticle({ task }) {
  return (
    <article className="flex min-w-0 flex-col gap-4 rounded-card border border-border bg-surface p-5">
      <header className="flex flex-col gap-2">
        <h2 className="font-heading text-xl font-bold tracking-tight text-ink">{task.title}</h2>
        <div className="flex flex-wrap items-center gap-1.5">
          <StatusPill tone="accent">{task.sectionLabel}</StatusPill>
          {task.roles.map((role) => (
            <StatusPill key={role}>{HELP_ROLE_LABELS[role] ?? role}</StatusPill>
          ))}
          {task.status !== 'publicado' && <StatusPill tone="warning">Borrador</StatusPill>}
        </div>
      </header>
      <HelpMarkdown blocks={task.blocks} />
    </article>
  );
}
