import { AlumnoPagos } from '@/pages/AlumnoPagos';
import { AlumnoPagoDetalle } from '@/pages/AlumnoPagoDetalle';
import { AlumnoPagarSeleccion } from '@/pages/AlumnoPagarSeleccion';
import { AlumnoPagarTransferencia } from '@/pages/AlumnoPagarTransferencia';
import { AlumnoPagarOnline } from '@/pages/AlumnoPagarOnline';
import { AlumnoPagoEstado } from '@/pages/AlumnoPagoEstado';
import { AdministrativoFacturacion } from '@/pages/AdministrativoFacturacion';
import { DirectorFinanciero } from '@/pages/DirectorFinanciero';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['alumno'], path: 'pagos', element: <AlumnoPagos /> },
    { roles: ['alumno'], path: 'pagos/estado', element: <AlumnoPagoEstado /> },
    { roles: ['alumno'], path: 'pagos/curso/:enrollmentId', element: <AlumnoPagoDetalle /> },
    { roles: ['alumno'], path: 'pagos/carrera/:enrollmentId', element: <AlumnoPagoDetalle /> },
    { roles: ['alumno'], path: 'pagos/:invoiceId/seleccion', element: <AlumnoPagarSeleccion /> },
    { roles: ['alumno'], path: 'pagos/:invoiceId/transferencia', element: <AlumnoPagarTransferencia /> },
    { roles: ['alumno'], path: 'pagos/:invoiceId/online', element: <AlumnoPagarOnline /> },
    { roles: ['administrativo'], path: 'facturacion', element: <AdministrativoFacturacion /> },
    { roles: ['director'], path: 'financiero', element: <DirectorFinanciero /> },
  ],
};
