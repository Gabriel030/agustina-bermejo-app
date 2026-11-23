import { describe, expect, it } from 'vitest';
import { grownEntries, inheritRenamedBaseline, renamedKeysFrom } from './unhandledBaselineGuard';

const oldPath = 'src/pages/AdministrativoFacturacion.test.jsx';
const newPath = 'src/features/billing/pages/AdministrativoFacturacion.test.jsx';
const renamedDiff = `R094\tfrontend/${oldPath}\tfrontend/${newPath}\n`;

describe('unhandled baseline guard', () => {
  it('renamedKeysFromMapsNewPathToOldPathAndIgnoresOtherStatuses', () => {
    const diff = `${renamedDiff}M\tfrontend/src/test/unhandled-baseline.json\nA\tfrontend/src/new/Nuevo.test.jsx\n`;
    expect(renamedKeysFrom(diff)).toEqual({ [newPath]: oldPath });
  });

  it('renameWithSameCountsPassesInheritingOldCounts', () => {
    const base = { [oldPath]: { 'GET /students': 2 } };
    const current = { [newPath]: { 'GET /students': 2 } };
    const expected = inheritRenamedBaseline(base, renamedKeysFrom(renamedDiff));
    expect(grownEntries(current, expected)).toEqual([]);
  });

  it('renameWithHigherCountFails', () => {
    const base = { [oldPath]: { 'GET /students': 2 } };
    const current = { [newPath]: { 'GET /students': 3 } };
    const expected = inheritRenamedBaseline(base, renamedKeysFrom(renamedDiff));
    expect(grownEntries(current, expected)).toEqual([`${newPath} GET /students 3 > 2`]);
  });

  it('newKeyWithoutRenameFails', () => {
    const base = { [oldPath]: { 'GET /students': 2 } };
    const current = { [newPath]: { 'GET /students': 1 } };
    const expected = inheritRenamedBaseline(base, renamedKeysFrom(''));
    expect(grownEntries(current, expected)).toEqual([`${newPath} GET /students 1 > 0`]);
  });

  it('inheritDoesNotOverwriteAKeyThatAlreadyExistsInDevelop', () => {
    const base = { [oldPath]: { 'GET /students': 5 }, [newPath]: { 'GET /students': 1 } };
    const expected = inheritRenamedBaseline(base, renamedKeysFrom(renamedDiff));
    expect(expected[newPath]).toEqual({ 'GET /students': 1 });
  });

  it('inheritDoesNotMutateTheDevelopBaseline', () => {
    const base = { [oldPath]: { 'GET /students': 2 } };
    inheritRenamedBaseline(base, renamedKeysFrom(renamedDiff));
    expect(base).toEqual({ [oldPath]: { 'GET /students': 2 } });
  });
});
