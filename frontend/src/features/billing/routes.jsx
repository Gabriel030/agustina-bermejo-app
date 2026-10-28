import { AlumnoPagos } from '@/pages/AlumnoPagos';
import { AlumnoPagoDetalle } from '@/pages/AlumnoPagoDetalle';
import { AlumnoPagarSeleccion } from '@/pages/AlumnoPagarSeleccion';
import { AlumnoPagarTransferencia } from '@/pages/AlumnoPagarTransferencia';
import { AlumnoPagarOnline } from '@/pages/AlumnoPagarOnline';
import { AlumnoPagoEstado } from '@/pages/AlumnoPagoEstado';
import { AdministrativoFacturacion } from '@/pages/AdministrativoFacturacion';
import { RequestRefundPage } from '@/features/billing/refunds/RequestRefundPage';
import { AuthorizationInboxPage } from '@/features/billing/authorization/AuthorizationInboxPage';
import { AuthorizationRequestDetailPage } from '@/features/billing/authorization/AuthorizationRequestDetailPage';
import { CreateCreditNotePage } from '@/features/billing/credit-notes/CreateCreditNotePage';
import { MyCreditNotesPage } from '@/features/billing/credit-notes/MyCreditNotesPage';
import { FinancialDashboardPage } from '@/features/billing/dashboard/FinancialDashboardPage';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['alumno'], path: 'pagos', element: <AlumnoPagos /> },
    { roles: ['alumno'], path: 'pagos/estado', element: <AlumnoPagoEstado /> },
    { roles: ['alumno'], path: 'pagos/notas-de-credito', element: <MyCreditNotesPage /> },
    { roles: ['alumno'], path: 'pagos/curso/:enrollmentId', element: <AlumnoPagoDetalle /> },
    { roles: ['alumno'], path: 'pagos/carrera/:enrollmentId', element: <AlumnoPagoDetalle /> },
    { roles: ['alumno'], path: 'pagos/:invoiceId/seleccion', element: <AlumnoPagarSeleccion /> },
    { roles: ['alumno'], path: 'pagos/:invoiceId/transferencia', element: <AlumnoPagarTransferencia /> },
    { roles: ['alumno'], path: 'pagos/:invoiceId/online', element: <AlumnoPagarOnline /> },
    { roles: ['administrativo', 'director'], path: 'facturacion', element: <AdministrativoFacturacion /> },
    { roles: ['administrativo', 'director'], path: 'facturacion/cuotas/:invoiceId/nota-de-credito', element: <CreateCreditNotePage /> },
    { roles: ['administrativo', 'director'], path: 'alumnos/:id/reembolsos/nuevo', element: <RequestRefundPage /> },
    { roles: ['director'], path: 'aprobaciones/facturacion', element: <AuthorizationInboxPage /> },
    { roles: ['director'], path: 'aprobaciones/facturacion/:id', element: <AuthorizationRequestDetailPage /> },
    { roles: ['administrativo'], path: 'aprobaciones', element: <AuthorizationInboxPage /> },
    { roles: ['administrativo'], path: 'aprobaciones/:id', element: <AuthorizationRequestDetailPage /> },
    { roles: ['administrativo', 'director'], path: 'financiero', element: <FinancialDashboardPage /> },
  ],
};
