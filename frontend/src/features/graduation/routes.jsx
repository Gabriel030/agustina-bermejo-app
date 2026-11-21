import { lazyPage } from '@/routes/lazyPage';
const DirectorAprobacionesEgresos = lazyPage(() => import('@/features/graduation/pages/DirectorAprobacionesEgresos'), 'DirectorAprobacionesEgresos');
const DirectorEgresoDetalle = lazyPage(() => import('@/features/graduation/pages/DirectorEgresoDetalle'), 'DirectorEgresoDetalle');
const AdministrativoEgresosPorVerificar = lazyPage(() => import('@/features/graduation/pages/AdministrativoEgresosPorVerificar'), 'AdministrativoEgresosPorVerificar');
const AdministrativoEgresoDetalle = lazyPage(() => import('@/features/graduation/pages/AdministrativoEgresoDetalle'), 'AdministrativoEgresoDetalle');

const StaffRegularCertificates = lazyPage(() => import('@/features/graduation/certificates/pages/StaffRegularCertificates'), 'StaffRegularCertificates');
const StaffGraduationCertificates = lazyPage(() => import('@/features/graduation/certificates/pages/StaffGraduationCertificates'), 'StaffGraduationCertificates');
const CertificateTemplateEditor = lazyPage(() => import('@/features/graduation/certificates/pages/CertificateTemplateEditor'), 'CertificateTemplateEditor');
const CertificateSignatories = lazyPage(() => import('@/features/graduation/signatories/pages/CertificateSignatories'), 'CertificateSignatories');
const VerifyCertificate = lazyPage(() => import('@/features/graduation/certificates/pages/VerifyCertificate'), 'VerifyCertificate');

export const routes = {
  public: [{ path: '/verificar/:code', element: <VerifyCertificate /> }],
  shared: [],
  role: [
    { roles: ['director'], path: 'aprobaciones/egresos', element: <DirectorAprobacionesEgresos /> },
    { roles: ['director'], path: 'aprobaciones/egresos/:id', element: <DirectorEgresoDetalle /> },
    { roles: ['administrativo', 'director'], path: 'constancias', element: <StaffRegularCertificates /> },
    { roles: ['administrativo', 'director'], path: 'egresos', element: <StaffGraduationCertificates /> },
    { roles: ['administrativo', 'director'], path: 'plantilla-de-certificados', element: <CertificateTemplateEditor /> },
    { roles: ['administrativo', 'director'], path: 'firmantes', element: <CertificateSignatories /> },
    { roles: ['administrativo'], path: 'egresos-por-verificar', element: <AdministrativoEgresosPorVerificar /> },
    { roles: ['administrativo'], path: 'egresos-por-verificar/:id', element: <AdministrativoEgresoDetalle /> },
  ],
};
