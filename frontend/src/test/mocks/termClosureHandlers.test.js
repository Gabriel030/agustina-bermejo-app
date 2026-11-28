import { describe, expect, it } from 'vitest';

const API = 'http://localhost:8080';

describe('term closure default handlers', () => {
  it('pendingTermClosuresExposeSectionsExamBoardsAndTheGraceWindow', async () => {
    const response = await fetch(`${API}/commission-closures/pending-term-closures`);
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.graceDays).toBe(14);
    expect(body.sections).toHaveLength(2);
    expect(body.sections[1]).toMatchObject({ graceOver: true, closureState: 'TEACHER_CONFIRMED' });
    expect(body.examBoards[0]).toMatchObject({ examBoardId: 55, graceOver: true });
  });
});
