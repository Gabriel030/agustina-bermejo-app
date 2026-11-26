import { RoleShell } from '@/layouts/RoleShell';
import { groupMenuItems, menuItemsFor } from '@/routes/roleMenu';
import { useVisibleMenuItems } from '@/routes/useVisibleMenuItems';

const ITEMS = menuItemsFor('alumno');

export function AlumnoLayout() {
  const items = useVisibleMenuItems('alumno', ITEMS);
  return <RoleShell titulo="Alumno" items={items} groups={groupMenuItems('alumno', items)} topNav />;
}
