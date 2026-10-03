import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';

export function CatalogDetailError({ message, onBack }) {
  return (
    <PageContainer>
      <button
        type="button"
        onClick={onBack}
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← Catálogo
      </button>
      <GlassCard className="p-6 text-sm text-ink-soft">{message}</GlassCard>
    </PageContainer>
  );
}
