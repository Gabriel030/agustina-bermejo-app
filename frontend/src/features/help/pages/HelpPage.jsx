import { ArrowLeft } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Buscador } from '@/components/Buscador';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { HelpResultItem } from '@/features/help/components/HelpResultItem';
import { HelpSectionCard } from '@/features/help/components/HelpSectionCard';
import { HelpTaskArticle } from '@/features/help/components/HelpTaskArticle';
import { HelpToc } from '@/features/help/components/HelpToc';
import { useHelpBrowser } from '@/features/help/hooks/useHelpBrowser';

const isDraft = (task) => task.status !== 'publicado';

function BackButton({ onClick, children }) {
  return (
    <Button type="button" variant="ghost" size="sm" onClick={onClick} className="w-fit gap-1.5">
      <ArrowLeft aria-hidden="true" className="h-4 w-4" />
      {children}
    </Button>
  );
}

function TaskView({ task, onBack }) {
  const headings = task.blocks.filter((block) => block.type === 'heading' && block.level === 2);
  const scrollToHeading = (id) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  return (
    <div className="flex flex-col gap-3">
      <BackButton onClick={onBack}>Volver</BackButton>
      <div className="grid gap-4 lg:grid-cols-[14rem_minmax(0,1fr)]">
        <HelpToc headings={headings} onSelect={scrollToHeading} />
        <HelpTaskArticle task={task} />
      </div>
    </div>
  );
}

function SectionBlock({ section, results, onOpenTask }) {
  return (
    <section aria-labelledby={`help-results-${section.id}`} className="flex flex-col gap-1">
      <h2 id={`help-results-${section.id}`} className="px-3 text-size-11 font-bold uppercase tracking-[0.08em] text-ink-faint">
        {section.label}
      </h2>
      <div className="rounded-card border border-border bg-surface p-1">
        {results.map(({ task, snippet }) => (
          <HelpResultItem key={task.id} title={task.title} snippet={snippet} draft={isDraft(task)} onOpen={() => onOpenTask(task.id)} />
        ))}
      </div>
    </section>
  );
}

function SearchResults({ sections, results, onOpenTask }) {
  if (results.length === 0) {
    return <EmptyState icon="🔎" title="Sin resultados" description="Probá con otra palabra o activá «Ver todos los roles»." />;
  }
  return (
    <div className="flex flex-col gap-4">
      {sections
        .map((section) => ({ section, items: results.filter(({ task }) => task.section === section.id) }))
        .filter(({ items }) => items.length > 0)
        .map(({ section, items }) => (
          <SectionBlock key={section.id} section={section} results={items} onOpenTask={onOpenTask} />
        ))}
    </div>
  );
}

function SectionTasks({ section, onBack, onOpenTask }) {
  return (
    <div className="flex flex-col gap-3">
      <BackButton onClick={onBack}>Todas las secciones</BackButton>
      <h2 className="font-heading text-size-18 font-semibold text-ink">{section.label}</h2>
      <div className="rounded-card border border-border bg-surface p-1">
        {section.tasks.map((task) => (
          <HelpResultItem key={task.id} title={task.title} draft={isDraft(task)} onOpen={() => onOpenTask(task.id)} />
        ))}
      </div>
    </div>
  );
}

function SectionGrid({ sections, onOpenSection }) {
  const shown = sections.filter((section) => section.tasks.length > 0 || section.underRedesign);
  return (
    <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {shown.map((section) => (
        <li key={section.id} className="flex">
          <HelpSectionCard
            label={section.label}
            taskCount={section.tasks.length}
            underRedesign={section.underRedesign}
            onOpen={() => onOpenSection(section.id)}
          />
        </li>
      ))}
    </ul>
  );
}

function HelpBody({ browser }) {
  if (browser.isPending) return <Skeleton className="h-40 w-full" />;
  if (browser.error) return <LoadErrorState error={browser.error} onRetry={browser.retry} resource="la ayuda" />;
  if (browser.selectedTask) return <TaskView task={browser.selectedTask} onBack={browser.closeTask} />;
  if (browser.searching) {
    return <SearchResults sections={browser.sections} results={browser.results} onOpenTask={browser.openTask} />;
  }
  if (browser.activeSection) {
    return <SectionTasks section={browser.activeSection} onBack={browser.closeSection} onOpenTask={browser.openTask} />;
  }
  if (!browser.hasTasks) {
    return <EmptyState icon="📖" title="Todavía no hay tareas para tu rol" description="Activá «Ver todos los roles» para ver el resto." />;
  }
  return <SectionGrid sections={browser.sections} onOpenSection={browser.openSection} />;
}

export function HelpPage() {
  const browser = useHelpBrowser();

  if (!browser.allowed) {
    return (
      <PageContainer title="Ayuda">
        <EmptyState icon="🔒" title="La ayuda es para el personal del instituto" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="Ayuda">
      <div className="flex flex-wrap items-center gap-3">
        <Buscador
          value={browser.query}
          onChange={browser.setQuery}
          placeholder="Buscar en la ayuda…"
          ariaLabel="Buscar en la ayuda"
          className="w-full md:max-w-sm"
        />
        <label className="flex min-h-11 items-center gap-2 text-size-14 text-ink-soft md:min-h-0">
          <Switch checked={browser.showAllRoles} onCheckedChange={browser.setShowAllRoles} aria-label="Ver todos los roles" />
          Ver todos los roles
        </label>
      </div>
      <HelpBody browser={browser} />
    </PageContainer>
  );
}
