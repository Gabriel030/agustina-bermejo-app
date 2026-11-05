import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { cwd } from 'node:process'
import { describe, expect, it } from 'vitest'
import { NAVIGATION_DENYLIST, SKIP_WAITING_MESSAGE, isSkipWaitingMessage } from './swMessages'

const source = readFileSync(resolve(cwd(), 'src/sw.js'), 'utf8')

const isCachedNavigation = (path) => !NAVIGATION_DENYLIST.some((pattern) => pattern.test(path))

describe('sw.js', () => {
  it('precachesTheBuildManifestAndRoutesNavigationsToTheShell', () => {
    expect(source).toContain('precacheAndRoute(self.__WB_MANIFEST)')
    expect(source).toContain("createHandlerBoundToURL('/index.html')")
    expect(source).toContain('NavigationRoute')
  })

  it('neverSkipsWaitingWithoutAUserMessage', () => {
    const calls = source.match(/skipWaiting\(\)/g) ?? []
    expect(calls).toHaveLength(1)
    expect(source).toMatch(/if \(isSkipWaitingMessage\(event\.data\)\) self\.skipWaiting\(\)/)
  })

  it('declaresNoRuntimeCachingForApiOrSessionData', () => {
    expect(source).not.toContain('StaleWhileRevalidate')
    expect(source).not.toContain('NetworkFirst')
    expect(source).not.toContain('CacheFirst')
    expect(source).not.toContain('fetch')
  })
})

describe('navigation denylist', () => {
  it('servesTheShellForAppRoutes', () => {
    expect(isCachedNavigation('/')).toBe(true)
    expect(isCachedNavigation('/alumno/horarios')).toBe(true)
  })

  it('excludesApiPathsAndStaticFiles', () => {
    expect(isCachedNavigation('/api/me')).toBe(false)
    expect(isCachedNavigation('/icons/icon-192.png')).toBe(false)
    expect(isCachedNavigation('/sw.js')).toBe(false)
  })
})

describe('isSkipWaitingMessage', () => {
  it('acceptsOnlyTheSkipWaitingType', () => {
    expect(isSkipWaitingMessage({ type: SKIP_WAITING_MESSAGE })).toBe(true)
    expect(isSkipWaitingMessage({ type: 'OTHER' })).toBe(false)
    expect(isSkipWaitingMessage(null)).toBe(false)
  })
})
