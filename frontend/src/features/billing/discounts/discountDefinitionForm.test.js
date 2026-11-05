import { describe, expect, it } from 'vitest';
import { applyFieldChange, buildDefinitionPayload, initialFormValues, sampleOutcome, validateDefinitionForm } from './discountDefinitionForm';

describe('discountDefinitionForm', () => {
  it('resets dependent fields when the type, the fee or the scope change', () => {
    const promotion = { ...initialFormValues('promotion'), installmentsTotal: '3', scopeId: '2', cohortId: '9' };
    expect(applyFieldChange(promotion, 'applicableInvoiceType', 'ENROLLMENT_FEE').installmentsTotal).toBe('');
    expect(applyFieldChange(promotion, 'scopeId', '3').cohortId).toBe('');
    expect(applyFieldChange(promotion, 'scopeKind', 'COURSE')).toMatchObject({ scopeId: '', cohortId: '' });
    const discount = applyFieldChange({ ...initialFormValues('discount'), value: '10' }, 'discountType', 'SCHOLARSHIP');
    expect(discount).toMatchObject({ applicableInvoiceType: 'MONTHLY_INSTALLMENT', value: '' });
  });

  it('collects every rule of a promotion', () => {
    const values = { ...initialFormValues('promotion'), name: '', value: '0', priority: '0', quota: '0', installmentsTotal: '0', capPerInvoice: '-5' };
    expect(Object.keys(validateDefinitionForm(values).errors).sort()).toEqual(
      ['capPerInvoice', 'enrollmentWindowFrom', 'enrollmentWindowTo', 'installmentsTotal', 'name', 'priority', 'quota', 'scopeId', 'value'],
    );
  });

  it('builds the payload of a nominal promotion for a course', () => {
    const values = {
      ...initialFormValues('promotion'), name: ' Matrícula ', applicableInvoiceType: 'ENROLLMENT_FEE', calcKind: 'FIXED', value: '50000', scopeKind: 'COURSE', scopeId: '7',
      quota: '10', enrollmentWindowFrom: '2026-12-01', enrollmentWindowTo: '2027-02-28', capPerInvoice: '40000',
    };
    expect(buildDefinitionPayload(values)).toMatchObject({
      name: 'Matrícula', calculation: { type: 'NOMINAL', amount: { amount: '50000.00', currency: 'ARS' } }, courseId: 7, programId: null,
      programCohortId: null, quota: 10, capPerInvoice: { amount: '40000.00', currency: 'ARS' }, installmentsTotal: null,
    });
  });

  it('computes the example with the cap and without a value for scholarships', () => {
    const values = { ...initialFormValues('discount'), value: '50', capPerInvoice: '1000000' };
    expect(sampleOutcome(values)).toEqual({ price: 6000000, discount: 1000000, capped: true, total: 5000000 });
    expect(sampleOutcome({ ...values, discountType: 'SCHOLARSHIP' })).toBeNull();
  });
});
