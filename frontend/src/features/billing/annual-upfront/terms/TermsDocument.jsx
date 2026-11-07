import { termsBlocksOf } from './annualTerms';

export function TermsDocument({ text }) {
  return (
    <div className="flex flex-col gap-4 text-sm leading-relaxed text-ink-soft">
      {termsBlocksOf(text).map((block, index) => (
        <section key={index} className="flex flex-col gap-1.5">
          {block.type === 'heading' ? (
            <>
              <h2 className="font-heading text-base font-semibold text-ink">{block.text}</h2>
              {block.body && <p className="whitespace-pre-line">{block.body}</p>}
            </>
          ) : (
            <p className="whitespace-pre-line">{block.text}</p>
          )}
        </section>
      ))}
    </div>
  );
}
