import { RoleShell } from '@/layouts/RoleShell';
import { menuItemsFor } from '@/routes/roleMenu';
import { useVisibleMenuItems } from '@/routes/useVisibleMenuItems';

const ITEMS = menuItemsFor('administrativo');

export function AdministrativoLayout() {
  const items = useVisibleMenuItems('administrativo', ITEMS);
  return <RoleShell titulo="Administrativo" items={items} />;
}
