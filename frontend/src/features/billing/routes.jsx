import { lazyPage } from '@/routes/lazyPage';
import { RequiresConnection } from '@/components/offline/RequiresConnection';
const AlumnoPagos = lazyPage(() => import('@/pages/AlumnoPagos'), 'AlumnoPagos');
const AlumnoPagoDetalle = lazyPage(() => import('@/pages/AlumnoPagoDetalle'), 'AlumnoPagoDetalle');
const AdvanceEnrollmentFeePage = lazyPage(() => import('@/features/billing/advance-enrollment-fee/AdvanceEnrollmentFeePage'), 'AdvanceEnrollmentFeePage');
const AlumnoPagarSeleccion = lazyPage(() => import('@/pages/AlumnoPagarSeleccion'), 'AlumnoPagarSeleccion');
const AlumnoPagarTransferencia = lazyPage(() => import('@/pages/AlumnoPagarTransferencia'), 'AlumnoPagarTransferencia');
const AlumnoPagarOnline = lazyPage(() => import('@/pages/AlumnoPagarOnline'), 'AlumnoPagarOnline');
const AlumnoPagoEstado = lazyPage(() => import('@/pages/AlumnoPagoEstado'), 'AlumnoPagoEstado');
const AdministrativoFacturacion = lazyPage(() => import('@/pages/AdministrativoFacturacion'), 'AdministrativoFacturacion');
const AdministrativoCargarComprobante = lazyPage(() => import('@/pages/AdministrativoCargarComprobante'), 'AdministrativoCargarComprobante');
const RequestRefundPage = lazyPage(() => import('@/features/billing/refunds/RequestRefundPage'), 'RequestRefundPage');
const AuthorizationInboxPage = lazyPage(() => import('@/features/billing/authorization/AuthorizationInboxPage'), 'AuthorizationInboxPage');
const AuthorizationRequestDetailPage = lazyPage(() => import('@/features/billing/authorization/AuthorizationRequestDetailPage'), 'AuthorizationRequestDetailPage');
const CreateCreditNotePage = lazyPage(() => import('@/features/billing/credit-notes/CreateCreditNotePage'), 'CreateCreditNotePage');
const MyCreditNotesPage = lazyPage(() => import('@/features/billing/credit-notes/MyCreditNotesPage'), 'MyCreditNotesPage');
const FinancialDashboardPage = lazyPage(() => import('@/features/billing/dashboard/FinancialDashboardPage'), 'FinancialDashboardPage');
const DiscountsPage = lazyPage(() => import('@/features/billing/discounts/DiscountsPage'), 'DiscountsPage');
const DiscountDefinitionFormPage = lazyPage(() => import('@/features/billing/discounts/DiscountDefinitionFormPage'), 'DiscountDefinitionFormPage');
const DiscountDefinitionDetailPage = lazyPage(() => import('@/features/billing/discounts/DiscountDefinitionDetailPage'), 'DiscountDefinitionDetailPage');
const GrantDiscountPage = lazyPage(() => import('@/features/billing/discounts/GrantDiscountPage'), 'GrantDiscountPage');

const MonthlyRunPage = lazyPage(() => import('@/features/billing/monthly-run/MonthlyRunPage'), 'MonthlyRunPage');
const DraftInvoicesPage = lazyPage(() => import('@/features/billing/monthly-run/DraftInvoicesPage'), 'DraftInvoicesPage');
const BillingRunsPage =lazyPage(() => import('@/features/billing/runs/BillingRunsPage'), 'BillingRunsPage');
const SimulatedMailsPage = lazyPage(() => import('@/features/billing/reminders/SimulatedMailsPage'), 'SimulatedMailsPage');
const ReminderSettingsPage = lazyPage(() => import('@/features/billing/reminders/ReminderSettingsPage'), 'ReminderSettingsPage');
const PaymentMethodsPage = lazyPage(() => import('@/features/billing/payment-methods/PaymentMethodsPage'), 'PaymentMethodsPage');

const AnnualTermsPage = lazyPage(() => import('@/features/billing/annual-upfront/terms/AnnualTermsPage'), 'AnnualTermsPage');
const AnnualTermsAdminPage = lazyPage(() => import('@/features/billing/annual-upfront/terms/AnnualTermsAdminPage'), 'AnnualTermsAdminPage');

export const routes = {
  public: [],
  shared: [
    { path: '/terminos-pago-anual', element: <AnnualTermsPage /> },
    { path: '/terminos-pago-anual/:versionNumber', element: <AnnualTermsPage /> },
  ],
  role: [
    { roles: ['alumno'], path: 'pagos', element: <RequiresConnection><AlumnoPagos /></RequiresConnection> },
    { roles: ['alumno'], path: 'pagos/estado', element: <RequiresConnection><AlumnoPagoEstado /></RequiresConnection> },
    { roles: ['alumno'], path: 'pagos/notas-de-credito', element: <RequiresConnection><MyCreditNotesPage /></RequiresConnection> },
    { roles: ['alumno'], path: 'pagos/curso/:enrollmentId', element: <RequiresConnection><AlumnoPagoDetalle /></RequiresConnection> },
    { roles: ['alumno'], path: 'pagos/carrera/:enrollmentId', element: <RequiresConnection><AlumnoPagoDetalle /></RequiresConnection> },
    { roles: ['alumno'], path: 'pagos/carrera/:enrollmentId/matricula-anticipada', element: <RequiresConnection><AdvanceEnrollmentFeePage /></RequiresConnection> },
    { roles: ['alumno'], path: 'pagos/:invoiceId/seleccion', element: <RequiresConnection><AlumnoPagarSeleccion /></RequiresConnection> },
    { roles: ['alumno'], path: 'pagos/:invoiceId/transferencia', element: <RequiresConnection><AlumnoPagarTransferencia /></RequiresConnection> },
    { roles: ['alumno'], path: 'pagos/:invoiceId/online', element: <RequiresConnection><AlumnoPagarOnline /></RequiresConnection> },
    { roles: ['administrativo', 'director'], path: 'facturacion', element: <AdministrativoFacturacion /> },
    { roles: ['administrativo', 'director'], path: 'facturacion/alumnos/:studentId/cargar-comprobante', element: <AdministrativoCargarComprobante /> },
    { roles: ['administrativo', 'director'], path: 'facturacion/cuotas/:invoiceId/nota-de-credito', element: <CreateCreditNotePage /> },
    { roles: ['administrativo', 'director'], path: 'alumnos/:id/reembolsos/nuevo', element: <RequestRefundPage /> },
    { roles: ['administrativo', 'director'], path: 'alumnos/:id/descuentos/nuevo', element: <GrantDiscountPage /> },
    { roles: ['administrativo', 'director'], path: 'descuentos', element: <DiscountsPage /> },
    { roles: ['administrativo', 'director'], path: 'descuentos/nuevo', element: <DiscountDefinitionFormPage mode="discount" /> },
    { roles: ['administrativo', 'director'], path: 'descuentos/promociones/nueva', element: <DiscountDefinitionFormPage mode="promotion" /> },
    { roles: ['administrativo', 'director'], path: 'descuentos/:id', element: <DiscountDefinitionDetailPage /> },
    { roles: ['administrativo', 'director'], path: 'corridas-de-facturacion', element: <BillingRunsPage /> },
    { roles: ['administrativo', 'director'], path: 'emision-mensual', element: <MonthlyRunPage /> },
    { roles: ['administrativo', 'director'], path: 'emision-mensual/:runId/borradores', element: <DraftInvoicesPage /> },
    { roles: ['administrativo', 'director'], path: 'recordatorios-de-pago',element: <ReminderSettingsPage /> },
    { roles: ['administrativo', 'director'], path: 'recordatorios-de-pago/mails-simulados', element: <SimulatedMailsPage /> },
    { roles: ['administrativo', 'director'], path: 'medios-de-pago', element: <PaymentMethodsPage /> },
    { roles: ['director'], path: 'terminos-del-pago-anual', element: <AnnualTermsAdminPage /> },
    { roles: ['director'], path: 'aprobaciones/facturacion', element: <AuthorizationInboxPage /> },
    { roles: ['director'], path: 'aprobaciones/facturacion/:id', element: <AuthorizationRequestDetailPage /> },
    { roles: ['administrativo'], path: 'aprobaciones', element: <AuthorizationInboxPage /> },
    { roles: ['administrativo'], path: 'aprobaciones/:id', element: <AuthorizationRequestDetailPage /> },
    { roles: ['administrativo', 'director'], path: 'financiero', element: <FinancialDashboardPage /> },
  ],
};
