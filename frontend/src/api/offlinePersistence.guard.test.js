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

const MARKS_OFFLINE = /offline\s*:\s*true|offlineQuery\(/

const OFFLINE_WHITELIST = {
  'academic-structure/hooks/useHolidays.js': 'N0c',
  'academic-structure/hooks/usePrograms.js': 'N0c',
  'academic-structure/hooks/useTerminos.js': 'N0c',
  'academic-structure/hooks/useTerms.js': 'N0c',
  'announcements/hooks/useNovedades.js': 'N0',
  'attendance/hooks/useStudentAttendance.js': 'N2',
  'notifications/hooks/useNotifications.js': 'N0',
  'schedule/hooks/useMySchedule.js': 'N1',
}

describe('offline whitelist guard', () => {
  it('billingAndGradesQueriesAreNeverMarkedOffline', () => {
    const offenders = ['billing', 'evaluation', 'graduation']
      .flatMap((feature) => sourceFiles(join(featuresDir, feature)))
      .filter((file) => MARKS_OFFLINE.test(readFileSync(file, 'utf8')))
    expect(offenders).toEqual([])
  })

  it('onlyWhitelistedHooksAreMarkedOffline', () => {
    const marked = sourceFiles(featuresDir)
      .filter((file) => MARKS_OFFLINE.test(readFileSync(file, 'utf8')))
      .map((file) => file.slice(featuresDir.length + 1))
      .sort()
    expect(marked).toEqual(Object.keys(OFFLINE_WHITELIST).sort())
  })

  it('whitelistedHooksDeclareTheirLevelAndNeverMarkOfflineByHand', () => {
    for (const [file, level] of Object.entries(OFFLINE_WHITELIST)) {
      const source = readFileSync(join(featuresDir, file), 'utf8')
      expect(source).toMatch(new RegExp(`offlineQuery\\('${level}'`))
      expect(source).not.toMatch(/offline\s*:\s*true/)
    }
  })

  it('whitelistedHooksNeverPersistRawEnrollmentsGradesOrBilling', () => {
    for (const file of Object.keys(OFFLINE_WHITELIST)) {
      const source = readFileSync(join(featuresDir, file), 'utf8')
      expect(source).not.toMatch(/['"`]\/me\/section-enrollments['"`]/)
      expect(source).not.toMatch(/grade-items|final-?score|finalScore|billing|payment|invoice|justificationReason|justificationRejectionReason/i)
    }
  })

  it('onlyTheModulesHookOutsideFeaturesIsMarkedOffline', () => {
    const srcDir = join(process.cwd(), 'src')
    const marked = sourceFiles(srcDir)
      .filter((file) => !file.startsWith(featuresDir) && !/src\/(api|test)\//.test(file))
      .filter((file) => MARKS_OFFLINE.test(readFileSync(file, 'utf8')))
      .map((file) => file.slice(srcDir.length + 1))
      .sort()
    expect(marked).toEqual(['hooks/useMyModules.js'])
    expect(readFileSync(join(srcDir, 'hooks', 'useMyModules.js'), 'utf8')).toMatch(/offlineQuery\('N0c'/)
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
