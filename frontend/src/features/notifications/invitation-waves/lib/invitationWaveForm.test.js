import { describe, expect, it } from 'vitest';
import { buildWaveSchema, defaultFormValues, toCreatePayload } from './invitationWaveForm';
import { IMPORT_BATCHES, SETTINGS } from '../test/fixtures';

const VALID = { name: ' Ingresantes 2027 ', importBatchId: '12', batchSize: '30', intervalMinutes: '60', dailyCap: '120' };

function issuesOf(values, settings = SETTINGS) {
  const result = buildWaveSchema(settings, IMPORT_BATCHES).safeParse({ ...VALID, ...values });
  if (result.success) return {};
  return Object.fromEntries(result.error.issues.map((issue) => [issue.path[0], issue.message]));
}

describe('invitationWaveForm', () => {
  it('acceptsAValidForm', () => {
    expect(issuesOf({})).toEqual({});
  });

  it('requiresANameAndABatch', () => {
    expect(issuesOf({ name: '   ', importBatchId: '' })).toEqual({
      name: 'Ponele un nombre a la oleada.',
      importBatchId: 'Elegí un lote de importación.',
    });
  });

  it('limitsTheNameLength', () => {
    expect(issuesOf({ name: 'a'.repeat(121) }).name).toContain('120');
  });

  it('capsTheDailyCapAtTheInstituteMaximum', () => {
    expect(issuesOf({ dailyCap: '301' }).dailyCap).toContain('(300)');
    expect(issuesOf({ dailyCap: '300' }).dailyCap).toBeUndefined();
  });

  it('keepsTheBatchSizeUnderTheDailyCapAndTheMaximum', () => {
    expect(issuesOf({ batchSize: '90', dailyCap: '60' }).batchSize).toContain('(60)');
    expect(issuesOf({ batchSize: '101', dailyCap: '300' }).batchSize).toContain('(100)');
  });

  it.each(['', '0', '-3', '2.5', 'abc'])('rejectsTheWholeNumberField %s', (value) => {
    const issues = issuesOf({ batchSize: value, intervalMinutes: value, dailyCap: value });
    expect(Object.keys(issues).sort()).toEqual(['batchSize', 'dailyCap', 'intervalMinutes']);
  });

  it('keepsTheIntervalBetweenOneAndAFullDay', () => {
    expect(issuesOf({ intervalMinutes: '1440' }).intervalMinutes).toBeUndefined();
    expect(issuesOf({ intervalMinutes: '1441' }).intervalMinutes).toContain('1440');
  });

  it('rejectsABatchWithMorePeopleThanTheMaximumOfRecipients', () => {
    expect(issuesOf({ importBatchId: '13' }).importBatchId).toContain('5.000');
  });

  it('defaultsRespectTheLimitsFromTheSettings', () => {
    expect(defaultFormValues(SETTINGS)).toMatchObject({ batchSize: '30', intervalMinutes: '60', dailyCap: '120' });
    expect(defaultFormValues({ ...SETTINGS, maxBatchSize: 10, globalDailyCap: 50 })).toMatchObject({ batchSize: '10', dailyCap: '50' });
  });

  it('buildsTheCreatePayloadWithNumbersAndATrimmedName', () => {
    expect(toCreatePayload(VALID)).toEqual({ name: 'Ingresantes 2027', importBatchId: 12, batchSize: 30, intervalMinutes: 60, dailyCap: 120 });
  });
});
