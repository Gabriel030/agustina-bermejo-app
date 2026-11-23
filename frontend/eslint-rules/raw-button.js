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
  'src/features/academic-structure/components/AttendanceRegimeField.jsx',
  'src/features/academic-structure/components/ClassroomModuleSelector.jsx',
  'src/features/academic-structure/components/HolidayList.jsx',
  'src/features/academic-structure/components/HolidayPanelContent.jsx',
  'src/features/academic-structure/pages/PanelCorrelativas.jsx',
  'src/features/academic-structure/pages/PreceptorNuevoCiclo.jsx',
  'src/features/announcements/components/EnvioYCaducidad.jsx',
  'src/features/announcements/components/FilaAviso.jsx',
  'src/features/announcements/components/ParametrosDeSegmento.jsx',
  'src/features/announcements/components/SeccionMensaje.jsx',
  'src/features/attendance/components/SeccionAsistencia.jsx',
  'src/features/billing/credit-notes/ReasonCards.jsx',
  'src/features/billing/credit-notes/StudentCreditNotesSection.jsx',
  'src/features/billing/pricing/PricesPage.jsx',
  'src/features/billing/reminders/ReminderSettingsPage.jsx',
  'src/features/enrollment/components/InscripcionMaterias.jsx',
  'src/features/enrollment/components/InscripcionWizard.jsx',
  'src/features/identity/pages/MiPerfil.jsx',
  'src/features/notifications/components/NotificationBell.jsx',
  'src/features/workspace/pages/AlumnoInicio.jsx',
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
