import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { CHANNEL_LABELS, MAX_NOTICE_DAYS, MESSAGE_TYPES, UNTIL_PHRASES, buildNotice, newNoticeKey, noticeDays } from './reminderSchedule';

const REPEAT_UNTIL = ['DUE_DATE', 'SUSPENSION'];

export function NoticeEditor({ notice, availableTypes, onSave, onCancel }) {
  const [messageType, setMessageType] = useState(notice?.messageType ?? availableTypes[0]);
  const [days, setDays] = useState(notice ? noticeDays(notice) : 1);
  const [channels, setChannels] = useState(notice?.channels ?? ['IN_APP', 'EMAIL']);
  const [repeat, setRepeat] = useState(Boolean(notice?.repetition?.everyDays));
  const [everyDays, setEveryDays] = useState(notice?.repetition?.everyDays ?? 7);
  const [until, setUntil] = useState(notice?.repetition?.until ?? 'SUSPENSION');
  const isIssuance = messageType === 'ISSUANCE_NOTICE';

  function toggleChannel(channel) {
    setChannels((current) => (current.includes(channel) ? current.filter((item) => item !== channel) : [...current, channel]));
  }

  function submit(event) {
    event.preventDefault();
    onSave(
      buildNotice({
        noticeKey: notice?.noticeKey ?? newNoticeKey(),
        messageType,
        days,
        channels: isIssuance ? ['IN_APP'] : channels,
        repetition: !isIssuance && repeat ? { everyDays: Number(everyDays), until } : null,
      }),
    );
  }

  return (
    <form onSubmit={submit} aria-label={notice ? 'Editar aviso' : 'Agregar aviso'} className="flex flex-col gap-4 rounded-2xl border border-border bg-neutral-bg/40 p-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="notice-type">Tipo de aviso</Label>
          <Select value={messageType} onValueChange={setMessageType} disabled={Boolean(notice)}>
            <SelectTrigger id="notice-type">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(notice ? [notice.messageType] : availableTypes).map((type) => (
                <SelectItem key={type} value={type}>
                  {MESSAGE_TYPES[type].label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="notice-days">Días</Label>
          <Input
            id="notice-days"
            type="number"
            min={0}
            max={MAX_NOTICE_DAYS}
            value={isIssuance ? 0 : days}
            disabled={isIssuance}
            onChange={(event) => setDays(event.target.value)}
          />
        </div>
      </div>
      <fieldset className="flex flex-wrap items-center gap-4">
        <legend className="mb-1.5 text-sm font-medium text-ink">Canales</legend>
        {Object.entries(CHANNEL_LABELS).map(([channel, label]) => {
          const locked = isIssuance;
          const checked = locked ? channel === 'IN_APP' : channels.includes(channel);
          return (
            <Label key={channel} className="flex items-center gap-2 font-normal">
              <Checkbox
                checked={checked}
                disabled={locked && channel !== 'IN_APP'}
                onCheckedChange={() => !locked && toggleChannel(channel)}
              />
              {label}
            </Label>
          );
        })}
        {isIssuance && <span className="text-xs text-ink-faint">El aviso de emisión sale solo por la app.</span>}
      </fieldset>
      {!isIssuance && (
        <div className="flex flex-wrap items-center gap-3">
          <Label className="flex items-center gap-2 font-normal">
            <Checkbox checked={repeat} onCheckedChange={(value) => setRepeat(value === true)} />
            Repetir
          </Label>
          {repeat && (
            <>
              <Label htmlFor="notice-every" className="font-normal">
                cada
              </Label>
              <Input id="notice-every" type="number" min={1} max={MAX_NOTICE_DAYS} className="w-20" value={everyDays} onChange={(event) => setEveryDays(event.target.value)} />
              <Label htmlFor="notice-until" className="font-normal">
                días hasta
              </Label>
              <Select value={until} onValueChange={setUntil}>
                <SelectTrigger id="notice-until" className="w-44">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {REPEAT_UNTIL.map((milestone) => (
                    <SelectItem key={milestone} value={milestone}>
                      {UNTIL_PHRASES[milestone]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </>
          )}
        </div>
      )}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" variant="outline">
          Aplicar al borrador
        </Button>
      </div>
    </form>
  );
}
