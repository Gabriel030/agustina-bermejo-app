import { describe, expect, it } from 'vitest'
import { isCriticalPrecacheEntry, selectCriticalEntries } from '../../pwa.precache.js'
import { isStaticAssetRequest } from './swMessages'

describe('critical precache selection', () => {
  it('keepsTheShellAndStaticRootFiles', () => {
    ;['index.html', 'icons/icon-192.png', 'assets/index-abc12345.js', 'assets/index-abc12345.css', 'assets/react-vendor-abc12345.js'].forEach((url) =>
      expect(isCriticalPrecacheEntry(url)).toBe(true),
    )
  })

  it('keepsRoleLayoutsSoTheOfflineShellRendersForEveryRole', () => {
    expect(isCriticalPrecacheEntry('assets/AlumnoLayout-abc12345.js')).toBe(true)
    expect(isCriticalPrecacheEntry('assets/AdministrativoLayout-abc12345.js')).toBe(true)
    expect(isCriticalPrecacheEntry('assets/RoleShell-abc12345.js')).toBe(true)
  })

  it('keepsStudentPagesAsCriticalRoutes', () => {
    expect(isCriticalPrecacheEntry('assets/AlumnoInicio-abc12345.js')).toBe(true)
  })

  it('leavesHeavyAndRoleSpecificChunksToRuntimeCache', () => {
    expect(isCriticalPrecacheEntry('assets/recharts-abc12345.js')).toBe(false)
    expect(isCriticalPrecacheEntry('assets/AdministrativoFacturacion-abc12345.js')).toBe(false)
    expect(isCriticalPrecacheEntry('assets/LegajoAlumno-abc12345.js')).toBe(false)
  })

  it('dropsExtendedLatinFonts', () => {
    expect(isCriticalPrecacheEntry('assets/manrope-latin-ext-wght-normal-abc12345.woff2')).toBe(false)
    expect(isCriticalPrecacheEntry('assets/manrope-latin-wght-normal-abc12345.woff2')).toBe(true)
  })

  it('filtersTheManifestWithoutWarnings', () => {
    const result = selectCriticalEntries([{ url: 'index.html' }, { url: 'assets/recharts-abc12345.js' }])
    expect(result.manifest).toEqual([{ url: 'index.html' }])
    expect(result.warnings).toEqual([])
  })
})

describe('runtime static asset matcher', () => {
  const origin = 'https://app.test'

  it('acceptsHashedAssetsFromTheSameOrigin', () => {
    expect(isStaticAssetRequest(new URL('/assets/recharts-abc.js', origin), origin)).toBe(true)
  })

  it('rejectsApiCallsAndOtherOrigins', () => {
    expect(isStaticAssetRequest(new URL('/api/me', origin), origin)).toBe(false)
    expect(isStaticAssetRequest(new URL('/assets/x.js', 'https://cdn.test'), origin)).toBe(false)
  })
})
