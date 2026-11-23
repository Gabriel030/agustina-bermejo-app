import { lazyPage } from '@/routes/lazyPage';
const BroadcastWizard = lazyPage(() => import('@/features/broadcasts/pages/BroadcastWizard'), 'BroadcastWizard');
const InvitationWaveListPage = lazyPage(() => import('@/features/notifications/invitation-waves/pages/InvitationWaveListPage'), 'InvitationWaveListPage');
const InvitationWaveCreatePage = lazyPage(() => import('@/features/notifications/invitation-waves/pages/InvitationWaveCreatePage'), 'InvitationWaveCreatePage');
const InvitationWavePreviewPage = lazyPage(() => import('@/features/notifications/invitation-waves/pages/InvitationWavePreviewPage'), 'InvitationWavePreviewPage');
const InvitationWaveDetailPage = lazyPage(() => import('@/features/notifications/invitation-waves/pages/InvitationWaveDetailPage'), 'InvitationWaveDetailPage');

const INVITATION_ROLES = ['administrativo', 'director'];

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['administrativo', 'director'], path: 'comunicaciones/nuevo', element: <BroadcastWizard /> },
    { roles: INVITATION_ROLES, excludeSubdirector: true, path: 'invitaciones', element: <InvitationWaveListPage /> },
    { roles: INVITATION_ROLES, excludeSubdirector: true, path: 'invitaciones/nueva', element: <InvitationWaveCreatePage /> },
    { roles: INVITATION_ROLES, excludeSubdirector: true, path: 'invitaciones/:waveId', element: <InvitationWaveDetailPage /> },
    { roles: INVITATION_ROLES, excludeSubdirector: true, path: 'invitaciones/:waveId/vista-previa', element: <InvitationWavePreviewPage /> },
  ],
};
