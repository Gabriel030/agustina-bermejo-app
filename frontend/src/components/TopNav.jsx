import { Link, NavLink, useLocation } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { RoleSwitcher } from '@/components/RoleSwitcher';
import { ThemeToggle } from '@/components/ThemeToggle';
import { MENU_ROW, MenuLink, TOP_NAV_BUTTON, TOP_NAV_BUTTON_ACTIVE, TopNavMenu } from '@/components/TopNavMenu';
import { TopNavGridPanel } from '@/components/TopNavGridPanel';
import { TopNavCatalogPanel } from '@/features/enrollment';
import { NotificationBell } from '@/features/notifications';
import { useAuth } from '@/context/AuthContext';
import { useActiveRole } from '@/hooks/useActiveRole';
import { useTopNavMenus } from '@/hooks/useTopNavMenus';
import { TOP_NAV_BREAKPOINTS } from '@/components/topNavBreakpoint';
import { buildTopNavEntries, isPathWithin } from '@/routes/navModel';

function initialsOf(user) {
  return `${user.nombre?.[0] ?? ''}${user.apellido?.[0] ?? ''}`.toUpperCase();
}

function BarLink({ item }) {
  return (
    <NavLink to={item.to} className={({ isActive }) => cn(TOP_NAV_BUTTON, isActive && TOP_NAV_BUTTON_ACTIVE)}>
      {item.label}
    </NavLink>
  );
}

function GroupMenu({ entry, pathname, menus }) {
  return (
    <TopNavMenu
      id={entry.id}
      menus={menus}
      open={menus.openId === entry.id}
      active={entry.items.some((item) => isPathWithin(item.to, pathname))}
      wide={entry.wide}
      trigger={entry.label}
    >
      {entry.sections ? (
        menus.openId === entry.id && <TopNavGridPanel title={entry.label} sections={entry.sections} menus={menus} />
      ) : (
        <ul aria-label={entry.label}>
          {entry.items.map((item) => (
            <li key={item.to}>
              <MenuLink item={item} menus={menus} />
            </li>
          ))}
        </ul>
      )}
    </TopNavMenu>
  );
}

function CatalogMenu({ entry, pathname, menus }) {
  return (
    <TopNavMenu
      id={entry.id}
      menus={menus}
      open={menus.openId === entry.id}
      active={isPathWithin(entry.item.to, pathname)}
      placement="bar"
      trigger={entry.item.label}
    >
      {menus.openId === entry.id && <TopNavCatalogPanel menus={menus} />}
    </TopNavMenu>
  );
}

function UserMenu({ titulo, usuario, menus, breakpoint }) {
  const { logout } = useAuth();
  const { canSwitch } = useActiveRole();
  return (
    <TopNavMenu
      id="user"
      menus={menus}
      open={menus.openId === 'user'}
      align="end"
      trigger={
        <>
          <span
            aria-hidden="true"
            className="flex h-8 w-8 items-center justify-center rounded-control bg-accent-soft font-heading text-size-13 font-bold text-(--accent)"
          >
            {initialsOf(usuario)}
          </span>
          <span className={cn('hidden max-w-40 truncate', breakpoint.nameVisible)}>
            {usuario.nombre} {usuario.apellido}
          </span>
          <span className={cn('sr-only', breakpoint.nameHidden)}>Mi cuenta</span>
        </>
      }
    >
      <div className="px-3 py-2">
        <div className="truncate font-heading text-size-14 font-semibold text-ink">
          {usuario.nombre} {usuario.apellido}
        </div>
        {!canSwitch && <div className="text-size-12 text-ink-faint">{titulo}</div>}
      </div>
      {canSwitch && <RoleSwitcher variant="inline" onNavigate={menus.close} />}
      <div className="mx-1 my-1.5 border-t border-border" />
      <Link to="/perfil" onClick={menus.close} className={MENU_ROW}>
        Mi perfil
      </Link>
      <Button type="button" variant="ghost" size="inline" onClick={logout} className={MENU_ROW}>
        Cerrar sesión
      </Button>
    </TopNavMenu>
  );
}

export function TopNav({ titulo, items, groups, breakpoint = TOP_NAV_BREAKPOINTS.standard }) {
  const { usuario } = useAuth();
  const { pathname } = useLocation();
  const menus = useTopNavMenus(pathname);
  const entries = buildTopNavEntries(items, groups);

  return (
    <div className={cn('relative z-30 mx-auto mt-4 hidden min-h-14 w-[calc(100%-2rem)] max-w-[1248px] shrink-0 items-center gap-0.5 rounded-card border border-border bg-surface px-3', breakpoint.barVisible)}>
      <Link
        to={items[0]?.to ?? '/'}
        aria-label="Ir al inicio"
        className="mr-3 shrink-0 rounded-control py-2 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring"
      >
        <InstitutionBrand layout="horizontal" size="md" className={breakpoint.brandCollapsed} />
      </Link>
      <nav aria-label="Principal" className="flex min-w-0 flex-1 items-center gap-0.5">
        {entries.map((entry) => {
          if (entry.type === 'link') return <BarLink key={entry.id} item={entry.item} />;
          if (entry.type === 'catalog') return <CatalogMenu key={entry.id} entry={entry} pathname={pathname} menus={menus} />;
          return <GroupMenu key={entry.id} entry={entry} pathname={pathname} menus={menus} />;
        })}
      </nav>
      <div className="ml-auto flex shrink-0 items-center gap-1">
        <ThemeToggle />
        <NotificationBell />
        {usuario && <UserMenu titulo={titulo} usuario={usuario} menus={menus} breakpoint={breakpoint} />}
      </div>
    </div>
  );
}
