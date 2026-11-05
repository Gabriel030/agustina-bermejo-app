import { RoleShell } from '@/layouts/RoleShell';
import { menuItemsFor } from '@/routes/roleMenu';
import { visibleMenuItems } from '@/routes/menuAccess';

const ITEMS = menuItemsFor('docente');

export function DocenteLayout() {
  return <RoleShell titulo="Docente" items={visibleMenuItems('docente', ITEMS)} />;
}
