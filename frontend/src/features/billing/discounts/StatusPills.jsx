import { StatusPill } from '@/components/StatusPill';
import {
  COHORT_PROMOTION_STATUS,
  DEFINITION_STATUS,
  STUDENT_DISCOUNT_STATUS,
  definitionStatusKey,
} from './discountLabels';

export function DiscountStatusPill({ entry }) {
  const info = DEFINITION_STATUS[definitionStatusKey(entry)];
  return <StatusPill tone={info.tone}>{info.label}</StatusPill>;
}

export function PromotionStatusPill({ status }) {
  const info = COHORT_PROMOTION_STATUS[status] ?? { label: status, tone: 'neutral' };
  return <StatusPill tone={info.tone}>{info.label}</StatusPill>;
}

export function StudentDiscountStatusPill({ status, labels = STUDENT_DISCOUNT_STATUS }) {
  const info = labels[status] ?? { label: status, tone: 'neutral' };
  return <StatusPill tone={info.tone}>{info.label}</StatusPill>;
}
