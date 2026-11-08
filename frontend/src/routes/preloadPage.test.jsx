import { describe, expect, it, vi } from 'vitest';
import { preloadPageForPath, preloadRolePage } from '@/routes/preloadPage';
import { roleRoutesFor } from '@/routes/featureRoutes';

function attendanceRoute() {
  return roleRoutesFor('docente').find((candidate) => candidate.path === 'tomar-asistencia');
}

describe('preloadPage', () => {
  it('preloadsTheChunkOfTheRoutePointedByThePath', async () => {
    const preload = vi.spyOn(attendanceRoute().element.type, 'preload').mockResolvedValue(null);

    await preloadRolePage('docente', '/docente/tomar-asistencia');

    expect(preload).toHaveBeenCalledTimes(1);
  });

  it('ignoresPathsOfAnotherRole', () => {
    const preload = vi.spyOn(attendanceRoute().element.type, 'preload').mockResolvedValue(null);
    preload.mockClear();

    expect(preloadRolePage('alumno', '/docente/tomar-asistencia')).toBeNull();
    expect(preload).not.toHaveBeenCalled();
  });

  it('doesNothingForPublicPaths', () => {
    expect(preloadPageForPath('/login')).toBeNull();
  });

  it('swallowsPreloadFailures', async () => {
    vi.spyOn(attendanceRoute().element.type, 'preload').mockRejectedValue(new Error('offline'));

    await expect(preloadRolePage('docente', '/docente/tomar-asistencia')).resolves.toBeNull();
  });
});
