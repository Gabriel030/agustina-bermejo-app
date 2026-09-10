import { Check } from 'lucide-react';

export function Stepper({ steps, currentStep }) {
  return (
    <div className="flex items-center gap-0 w-full mb-8">
      {steps.map((step, index) => {
        const isComplete = index < currentStep;
        const isActive = index === currentStep;

        return (
          <div key={index} className="flex items-center flex-1">
            <div className="flex flex-col items-center flex-shrink-0">
              <div
                className={[
                  'w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold transition-colors',
                  isComplete
                    ? 'bg-green-500 text-white'
                    : isActive
                      ? 'bg-blue-600 text-white'
                      : 'bg-muted text-muted-foreground',
                ].join(' ')}
              >
                {isComplete ? <Check size={14} strokeWidth={2.5} /> : index + 1}
              </div>
              <span
                className={[
                  'mt-1.5 text-[11px] font-medium text-center whitespace-nowrap',
                  isComplete
                    ? 'text-green-600'
                    : isActive
                      ? 'text-blue-600'
                      : 'text-muted-foreground',
                ].join(' ')}
              >
                {step}
              </span>
            </div>
            {index < steps.length - 1 && (
              <div
                className={[
                  'flex-1 h-[2px] mx-2 mb-5 transition-colors',
                  isComplete ? 'bg-green-400' : 'bg-border',
                ].join(' ')}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}
