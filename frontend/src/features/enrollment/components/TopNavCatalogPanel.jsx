import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Pin, Search } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { StatusPill } from '@/components/StatusPill';
import { MENU_ROW } from '@/components/TopNavMenu';
import { GRID_ROW_BASE, GRID_ROW_PINNED, GRID_ZONE_TITLE } from '@/components/topNavGridStyles';
import { useGridPointing } from '@/hooks/useGridPointing';
import { useCatalogOffering } from '@/features/enrollment/hooks/useCatalogOffering';
import { AREA_LABELS, CATALOG_PATH } from '@/features/enrollment/lib/catalogOffering';
import { axisId, countOfferingBy, describeAxis, PANEL_LEVELS, panelListItems } from '@/features/enrollment/lib/catalogPanel';

const NO_FILTERS = {};

function AxisRow({ id, label, count, pointing }) {
  const pinned = pointing.pinnedId === id;
  return (
    <Button
      type="button"
      variant="ghost"
      size="inline"
      data-catalog-row={id}
      aria-pressed={pinned}
      onPointerEnter={(event) => pointing.pointAfterDelay(id, event)}
      onFocus={() => pointing.pointNow(id)}
      onClick={() => pointing.togglePin(id)}
      className={cn(GRID_ROW_BASE, pinned ? GRID_ROW_PINNED : pointing.shownId === id && 'bg-neutral-bg')}
    >
      <span>{label}</span>
      {count !== undefined && <span className="tabular-nums text-ink-faint">({count})</span>}
    </Button>
  );
}

function ListBody({ offering, items, axis, query, menus }) {
  if (offering.isLoading) {
    return (
      <div aria-busy="true" className="flex flex-col gap-1.5 px-3">
        <span className="sr-only">Cargando oferta</span>
        {[0, 1, 2, 3].map((index) => (
          <Skeleton key={index} className="h-9 w-full" />
        ))}
      </div>
    );
  }
  if (offering.isError) return <LoadErrorState bare resource="la oferta académica" error={offering.error} onRetry={offering.refetch} />;
  if (offering.items.length === 0) return <EmptyState title="Todavía no hay oferta publicada" className="py-4" />;
  if (!axis && !query.trim()) return <p className="px-3 text-size-13 text-ink-soft">Elegí un área o un tipo para ver la oferta.</p>;
  if (items.length === 0) return <p className="px-3 text-size-13 text-ink-soft">No hay resultados para esa búsqueda.</p>;
  return (
    <ul aria-label={axis?.title ?? 'Resultados de la búsqueda'} className="flex flex-col gap-0.5">
      {items.map((item) => (
        <li key={item.key}>
          <Link to={item.to} data-catalog-item onClick={menus.close} className={cn(MENU_ROW, 'justify-between')}>
            <span className="truncate">{item.name}</span>
            <span className="flex shrink-0 items-center gap-1.5">
              <StatusPill tone="neutral">{item.typeLabel}</StatusPill>
              {AREA_LABELS[item.area] && <StatusPill tone="accent">{AREA_LABELS[item.area]}</StatusPill>}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function TopNavCatalogPanel({ menus }) {
  const offering = useCatalogOffering(NO_FILTERS);
  const pointing = useGridPointing();
  const [query, setQuery] = useState('');
  const rootRef = useRef(null);

  const axis = describeAxis(pointing.shownId);
  const pinnedAxis = describeAxis(pointing.pinnedId);
  const items = panelListItems(offering.items, axis, query);
  const loaded = !offering.isLoading && !offering.isError;
  const areaCounts = countOfferingBy(offering.items, 'area');
  const levelCounts = countOfferingBy(offering.items, 'level');
  const areas = Object.keys(AREA_LABELS).filter((area) => !loaded || offering.items.length === 0 || areaCounts.has(area));

  function queryAll(selector) {
    return Array.from(rootRef.current?.querySelectorAll(selector) ?? []);
  }

  function rowsOf(prefix) {
    return queryAll('[data-catalog-row]').filter((row) => row.dataset.catalogRow.startsWith(prefix));
  }

  function shownRow(prefix) {
    return rowsOf(prefix).find((row) => row.dataset.catalogRow === pointing.shownId) ?? rowsOf(prefix)[0];
  }

  function handleSearchKeyDown(event) {
    if (event.key === 'Escape') return;
    if (event.key === 'ArrowDown') {
      const first = queryAll('[data-catalog-item]')[0];
      if (first) {
        event.preventDefault();
        first.focus();
      }
    }
    event.stopPropagation();
  }

  function handleKeyDown(event) {
    const target = event.target;
    const rowId = target.dataset.catalogRow;
    const inItems = target.hasAttribute('data-catalog-item');
    const prefix = rowId?.startsWith('area:') ? 'area:' : 'level:';
    let zone = [];
    if (inItems) zone = queryAll('[data-catalog-item]');
    else if (rowId) zone = rowsOf(prefix);
    const index = zone.indexOf(target);
    if (index < 0) return;
    const moves = {
      ArrowDown: zone[(index + 1) % zone.length],
      ArrowUp: zone[(index - 1 + zone.length) % zone.length],
      Home: zone[0],
      End: zone[zone.length - 1],
    };
    if (prefix === 'area:' && rowId) moves.ArrowRight = shownRow('level:');
    if (prefix === 'level:' && rowId) {
      moves.ArrowLeft = shownRow('area:');
      moves.ArrowRight = queryAll('[data-catalog-item]')[0];
    }
    if (inItems) moves.ArrowLeft = shownRow(pointing.shownId?.startsWith('area:') ? 'area:' : 'level:');
    const next = moves[event.key];
    if (!next) return;
    event.preventDefault();
    event.stopPropagation();
    next.focus();
  }

  let announcement = '';
  if (pinnedAxis) announcement = `${pinnedAxis.title}. Fijado`;
  else if (axis) announcement = axis.title;

  return (
    <div ref={rootRef} onKeyDown={handleKeyDown} className="grid grid-cols-1 lg:grid-cols-[14.375rem_16.875rem_1fr]">
      <div className="border-b border-border p-2 lg:border-b-0 lg:border-r">
        <p className={cn(GRID_ZONE_TITLE, 'px-3 pb-2 pt-1')}>Áreas académicas</p>
        <div className="flex flex-col gap-0.5">
          {areas.map((area) => (
            <AxisRow
              key={area}
              id={axisId('area', area)}
              label={AREA_LABELS[area]}
              count={loaded ? (areaCounts.get(area) ?? 0) : undefined}
              pointing={pointing}
            />
          ))}
        </div>
      </div>
      <div className="flex flex-col gap-2 border-b border-border p-2 lg:border-b-0 lg:border-r">
        <p className={cn(GRID_ZONE_TITLE, 'px-3 pt-1')}>Tipo de oferta</p>
        <div className="flex flex-col gap-0.5">
          {PANEL_LEVELS.map((level) => (
            <AxisRow
              key={level.id}
              id={axisId('level', level.id)}
              label={level.label}
              count={loaded ? (levelCounts.get(level.id) ?? 0) : undefined}
              pointing={pointing}
            />
          ))}
        </div>
        <div className="relative px-1">
          <Search aria-hidden="true" className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <Input
            type="search"
            aria-label="Buscar carrera o curso"
            placeholder="Buscar por nombre"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={handleSearchKeyDown}
            className="min-h-11 pl-9"
          />
        </div>
        <Link to={CATALOG_PATH} onClick={menus.close} className={MENU_ROW}>
          Ver toda la oferta
        </Link>
      </div>
      <div className="flex min-w-0 flex-col p-2">
        <div className="flex items-center gap-2 px-3 pb-2 pt-1">
          <p className={GRID_ZONE_TITLE}>{axis ? axis.title : 'Oferta'}</p>
          {pinnedAxis && (
            <span className="inline-flex items-center gap-1 rounded-full bg-accent-soft px-2 py-0.5 text-size-11 font-bold text-(--accent)">
              <Pin aria-hidden="true" className="h-3 w-3" />
              Fijado
            </span>
          )}
        </div>
        <ListBody offering={offering} items={items} axis={axis} query={query} menus={menus} />
        {axis && (
          <Link to={axis.path} data-catalog-item onClick={menus.close} className={cn(MENU_ROW, 'mt-1 text-(--accent)')}>
            {axis.seeAll}
          </Link>
        )}
      </div>
      <div role="status" className="sr-only">
        {announcement}
      </div>
    </div>
  );
}
