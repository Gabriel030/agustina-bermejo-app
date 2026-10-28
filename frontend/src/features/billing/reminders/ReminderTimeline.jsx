import { useState } from 'react';
import { Plus } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { NoticeEditor } from './NoticeEditor';
import { MESSAGE_TYPES, MILESTONES, describeNotice } from './reminderSchedule';

function ReminderNoticeCard({ notice, readOnly, onEdit, onRemove }) {
  const label = MESSAGE_TYPES[notice.messageType].label;
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 rounded-2xl border border-border bg-surface px-4 py-3">
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="font-semibold text-ink">{label}</span>
        <span className="text-sm text-ink-soft">{describeNotice(notice)}</span>
      </div>
      {!readOnly && (
        <div className="flex gap-1">
          <Button variant="ghost" size="sm" aria-label={`Editar ${label}`} onClick={onEdit}>
            Editar
          </Button>
          <Button variant="ghost" size="sm" aria-label={`Quitar ${label}`} onClick={onRemove}>
            Quitar
          </Button>
        </div>
      )}
    </li>
  );
}

export function ReminderTimeline({ notices, readOnly, onChange }) {
  const [editing, setEditing] = useState(null);

  const usedIssuance = notices.some((notice) => notice.messageType === 'ISSUANCE_NOTICE');
  const availableTypes = Object.keys(MESSAGE_TYPES).filter((type) => !(type === 'ISSUANCE_NOTICE' && usedIssuance));

  function save(notice) {
    const exists = notices.some((item) => item.noticeKey === notice.noticeKey);
    onChange(exists ? notices.map((item) => (item.noticeKey === notice.noticeKey ? notice : item)) : [...notices, notice]);
    setEditing(null);
  }

  if (!notices.length && editing === null) {
    return (
      <EmptyState
        title="Esta política no tiene avisos"
        description="Agregá el primero para empezar a recordarle los pagos a los alumnos."
        action={
          !readOnly && (
            <Button variant="outline" onClick={() => setEditing('new')}>
              <Plus className="size-4" /> Agregar aviso
            </Button>
          )
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <ol className="flex flex-col gap-5">
        {MILESTONES.map((milestone) => {
          const items = notices.filter((notice) => notice.milestone === milestone.key).sort((a, b) => a.offsetDays - b.offsetDays);
          return (
            <li key={milestone.key} className="flex flex-col gap-2">
              <h3 className="font-heading text-sm font-semibold uppercase tracking-wide text-ink-faint">{milestone.label}</h3>
              {items.length === 0 ? (
                <p className="text-sm text-ink-faint">Sin avisos en este hito.</p>
              ) : (
                <ul className="flex flex-col gap-2">
                  {items.map((notice) =>
                    editing === notice.noticeKey ? (
                      <li key={notice.noticeKey}>
                        <NoticeEditor notice={notice} availableTypes={availableTypes} onSave={save} onCancel={() => setEditing(null)} />
                      </li>
                    ) : (
                      <ReminderNoticeCard
                        key={notice.noticeKey}
                        notice={notice}
                        readOnly={readOnly}
                        onEdit={() => setEditing(notice.noticeKey)}
                        onRemove={() => onChange(notices.filter((item) => item.noticeKey !== notice.noticeKey))}
                      />
                    ),
                  )}
                </ul>
              )}
            </li>
          );
        })}
      </ol>
      {editing === 'new' && <NoticeEditor availableTypes={availableTypes} onSave={save} onCancel={() => setEditing(null)} />}
      {!readOnly && editing === null && (
        <div>
          <Button variant="outline" onClick={() => setEditing('new')}>
            <Plus className="size-4" /> Agregar aviso
          </Button>
        </div>
      )}
    </div>
  );
}
