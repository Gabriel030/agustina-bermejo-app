import process from 'node:process'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const featuresDir = join(process.cwd(), 'src', 'features')

function sourceFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return sourceFiles(path)
    return /\.(js|jsx)$/.test(name) && !/\.test\./.test(name) ? [path] : []
  })
}

describe('offline whitelist guard', () => {
  it('billingAndGradesQueriesAreNeverMarkedOffline', () => {
    const offenders = ['billing', 'evaluation', 'graduation']
      .flatMap((feature) => sourceFiles(join(featuresDir, feature)))
      .filter((file) => /offline\s*:\s*true/.test(readFileSync(file, 'utf8')))
    expect(offenders).toEqual([])
  })

  it('onlyWhitelistedHooksAreMarkedOffline', () => {
    const marked = sourceFiles(featuresDir)
      .filter((file) => /offline\s*:\s*true/.test(readFileSync(file, 'utf8')))
      .map((file) => file.slice(featuresDir.length + 1))
      .sort()
    expect(marked).toEqual(['announcements/hooks/useNovedades.js', 'notifications/hooks/useNotifications.js'])
  })

  it('onlyAuthContextWritesTheOfflineProfileAndItStoresOnlyNameAndRoles', () => {
    const srcDir = join(process.cwd(), 'src')
    const writers = sourceFiles(srcDir)
      .filter((file) => /storeOfflineProfile\(/.test(readFileSync(file, 'utf8')) && !/offlinePersistence\.js$/.test(file))
      .map((file) => file.slice(srcDir.length + 1))
      .sort()
    expect(writers).toEqual(['context/AuthContext.jsx'])
    const persistence = readFileSync(join(srcDir, 'api', 'offlinePersistence.js'), 'utf8')
    const body = persistence.slice(persistence.indexOf('export async function storeOfflineProfile'), persistence.indexOf('export async function readOfflineProfile'))
    const fields = [...body.matchAll(/^\s+(\w+):/gm)].map((match) => match[1])
    expect(fields).toEqual(['nombre', 'apellido', 'roles'])
  })
})
