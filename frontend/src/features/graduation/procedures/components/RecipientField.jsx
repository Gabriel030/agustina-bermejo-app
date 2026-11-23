import { ChoiceChip } from '@/components/ChoiceChip';
import { Input } from '@/components/ui/input';
import { RECIPIENT_MAX_LENGTH, RECIPIENT_OPTIONS, sanitizeRecipientText } from '../lib/procedures';

export function RecipientField({ recipientType, recipientText, onTypeChange, onTextChange, disabled }) {
  const selected = RECIPIENT_OPTIONS.find((option) => option.type === recipientType);
  return (
    <div className="flex flex-col gap-2">
      <span id="recipient-label" className="text-size-13 font-semibold text-ink-soft">
        ¿Para quién es? <span className="font-normal text-ink-faint">(opcional)</span>
      </span>
      <div role="group" aria-labelledby="recipient-label" className="flex flex-wrap gap-2">
        {RECIPIENT_OPTIONS.map((option) => (
          <ChoiceChip
            key={option.type}
            size="md"
            selected={recipientType === option.type}
            aria-pressed={recipientType === option.type}
            disabled={disabled}
            className="min-h-10"
            onClick={() => {
              onTypeChange(recipientType === option.type ? null : option.type);
              onTextChange('');
            }}
          >
            {option.label}
          </ChoiceChip>
        ))}
      </div>
      {selected && (
        <div className="flex flex-col gap-1.5">
          <label htmlFor="recipient-text" className="text-size-13 font-semibold text-ink-soft">
            {selected.fieldLabel} <span className="font-normal text-ink-faint">(opcional)</span>
          </label>
          <Input
            id="recipient-text"
            value={recipientText}
            maxLength={RECIPIENT_MAX_LENGTH}
            disabled={disabled}
            onChange={(event) => onTextChange(sanitizeRecipientText(event.target.value))}
          />
          <span className="text-size-12 text-ink-faint" aria-live="polite">
            {recipientText.length}/{RECIPIENT_MAX_LENGTH}
          </span>
        </div>
      )}
    </div>
  );
}
