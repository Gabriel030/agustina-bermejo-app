export const RAW_BUTTON_EXCEPTIONS = [
  'src/components/ChoiceChip.jsx',
  'src/components/KpiFilterChip.jsx',
  'src/components/MobileMenu.jsx',
  'src/features/academic-structure/components/MonthGrid.jsx',
  'src/features/billing/components/MonthPicker.jsx',
  'src/components/NavGroups.jsx',
  'src/components/RoleSwitcher.jsx',
  'src/components/Sidebar.jsx',
  'src/components/Stepper.jsx',
  'src/components/ThemeToggle.jsx',
  'src/features/evaluation/components/SortableColumnHeader.jsx',
  'src/features/billing/components/InvoiceCoverageList.jsx',
  'src/components/charts/InfoTip.jsx',
  'src/components/table/ColumnFilter.jsx',
  'src/components/table/DataTable.jsx',
  'src/features/attendance/components/SeccionAsistencia.jsx',
  'src/features/billing/credit-notes/ReasonCards.jsx',
  'src/features/notifications/components/NotificationBell.jsx',
]

export const noRawButtonRule = {
  meta: { type: 'problem', schema: [] },
  create(context) {
    return {
      JSXOpeningElement(node) {
        if (node.name.type !== 'JSXIdentifier' || node.name.name !== 'button') return
        context.report({
          node,
          message: 'No uses <button> crudo: usá Button (@/components/ui/button) para que un cambio de diseño aplique en todos los usos.',
        })
      },
    }
  },
}
