import { GlassCard } from '@/components/GlassCard'
import { EmptyState } from '@/components/EmptyState'
import { Skeleton } from '@/components/Skeleton'
import { StatusPill } from '@/components/StatusPill'
import { LoadErrorState } from '@/components/LoadErrorState'
import { StaleStamp } from '@/components/offline/StaleStamp'
import { findNextClass, isScheduledToday } from '@/lib/schedule'
import { useMySchedule } from '@/features/schedule/hooks/useMySchedule'

function dayLabel(next) {
  const weekday = next.start.toLocaleDateString('es-AR', { weekday: 'long' })
  const label = next.offset === 0 ? 'Hoy' : next.offset === 1 ? 'Mañana' : weekday.charAt(0).toUpperCase() + weekday.slice(1)
  if (!next.hasTime) return label
  const time = `${String(next.hour).padStart(2, '0')}:${String(next.minute).padStart(2, '0')}`
  return `${label} ${time}`
}

export function ScheduleCard() {
  const { data, isLoading, isError, isFetching, refetch, dataUpdatedAt } = useMySchedule()
  const items = data?.items ?? []
  const now = new Date()
  const next = findNextClass(items, now)
  const loadFailed = isError && data === undefined

  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <div className="flex items-center gap-2">
        <div className="font-heading text-size-15 font-semibold text-ink">Tu horario</div>
        <StaleStamp dataUpdatedAt={dataUpdatedAt} isFetching={isFetching} isError={isError} hasData={data !== undefined} />
      </div>
      {isLoading ? (
        <Skeleton className="h-16 w-full" />
      ) : loadFailed ? (
        <LoadErrorState bare resource="tu horario" onRetry={refetch} />
      ) : items.length === 0 ? (
        <EmptyState icon="📅" title="Sin horarios cargados" className="py-4" />
      ) : (
        <>
          {next && (
            <div className="rounded-xl bg-accent-soft/40 px-4 py-3">
              <div className="text-xs font-semibold text-ink-faint">Próxima clase</div>
              <div className="text-size-13-5 font-semibold text-ink">{next.item.courseSubjectName}</div>
              <div className="text-xs text-ink-soft">{dayLabel(next)}</div>
            </div>
          )}
          <ul className="flex flex-col gap-2">
            {items.map((item) => (
              <li key={item.sectionEnrollmentId} className="flex flex-col gap-0.5 rounded-lg border border-border px-3 py-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-size-13 font-semibold text-ink">{item.courseSubjectName}</span>
                  {isScheduledToday(item.schedule, now) && <StatusPill tone="accent">Hoy</StatusPill>}
                </div>
                <span className="text-xs text-ink-faint">
                  {item.courseName} · {item.sectionName}
                </span>
                {item.schedule && <span className="text-xs text-ink-soft">{item.schedule}</span>}
              </li>
            ))}
          </ul>
        </>
      )}
    </GlassCard>
  )
}
