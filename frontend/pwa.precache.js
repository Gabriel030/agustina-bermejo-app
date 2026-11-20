const NON_CRITICAL_CHUNK = /^assets\/(recharts|Director|Administrativo|Preceptor|Staff|Platform|Billing|Financial|Legajo|Padron|Wizard|Carrera|Curso|Discount|Credit|Closure|Aviso|Annual|Authorization)/
const ROLE_LAYOUT = /^assets\/[A-Za-z]*(Layout|RoleShell)-/
const EXTENDED_FONT = /-latin-ext-/

export function isCriticalPrecacheEntry(url) {
  if (!url.startsWith('assets/')) return true
  if (EXTENDED_FONT.test(url)) return false
  if (ROLE_LAYOUT.test(url)) return true
  return !NON_CRITICAL_CHUNK.test(url)
}

export function selectCriticalEntries(entries) {
  return { manifest: entries.filter((entry) => isCriticalPrecacheEntry(entry.url)), warnings: [] }
}
