import { RoleShell } from '@/layouts/RoleShell';
import { buildNavModel } from '@/routes/navModel';
import { menuItemsFor } from '@/routes/roleMenu';
import { useVisibleMenuItems } from '@/routes/useVisibleMenuItems';

const ITEMS = menuItemsFor('subdirector');

export function SubdirectorLayout() {
  const items = useVisibleMenuItems('subdirector', ITEMS);
  return <RoleShell titulo="Subdirector" items={items} groups={buildNavModel('subdirector', items).groups} topNav="wide" />;
}
