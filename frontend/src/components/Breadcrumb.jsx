import { Link } from 'react-router-dom';
import { ChevronRight, Ellipsis } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { useMediaQuery } from '@/hooks/useMediaQuery';

const COMPACT_VIEWPORT_QUERY = '(width < 48rem)';
const COLLAPSE_FROM_LEVELS = 4;

function Separator() {
  return <ChevronRight aria-hidden="true" className="h-3.5 w-3.5 shrink-0 text-ink-faint" />;
}

function CollapsedLevelsMenu({ hiddenItems }) {
  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="ghost" size="icon" className="size-11 shrink-0 text-ink-faint" aria-label="Mostrar niveles anteriores">
          <Ellipsis aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-auto min-w-52 max-w-[calc(100vw-2rem)]">
        {hiddenItems.map((item, i) =>
          item.to ? (
            <DropdownMenuItem key={item.to} asChild>
              <Link to={item.to}>{item.label}</Link>
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem key={i} disabled>
              {item.label}
            </DropdownMenuItem>
          ),
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function BreadcrumbLevel({ item, isLast, truncate }) {
  const truncation = truncate ? 'min-w-0 truncate' : '';
  if (isLast || !item.to) {
    return <span className={`font-semibold text-ink ${truncation}`}>{item.label}</span>;
  }
  return (
    <Link to={item.to} className={`font-medium text-ink-faint hover:text-primary ${truncation}`}>
      {item.label}
    </Link>
  );
}

export function Breadcrumb({ items }) {
  const isCompactViewport = useMediaQuery(COMPACT_VIEWPORT_QUERY);
  if (!items || items.length === 0) return null;

  const isCollapsed = isCompactViewport && items.length >= COLLAPSE_FROM_LEVELS;
  const hiddenItems = isCollapsed ? items.slice(0, -2) : [];
  const visibleItems = isCollapsed ? items.slice(-2) : items;
  const offset = items.length - visibleItems.length;

  return (
    <nav aria-label="Ruta de navegación" className={`flex items-center gap-1.5 text-size-13 ${isCollapsed ? 'min-w-0 flex-nowrap' : ''}`}>
      {isCollapsed && <CollapsedLevelsMenu hiddenItems={hiddenItems} />}
      {visibleItems.map((item, i) => {
        const index = i + offset;
        return (
          <span key={item.to ?? index} className={`flex items-center gap-1.5 ${isCollapsed ? 'min-w-0' : ''}`}>
            {(index > 0) && <Separator />}
            <BreadcrumbLevel item={item} isLast={index === items.length - 1} truncate={isCollapsed} />
          </span>
        );
      })}
    </nav>
  );
}
