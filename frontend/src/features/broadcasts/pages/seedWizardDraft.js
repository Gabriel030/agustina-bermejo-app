export function seedWizardDraft(userId, criteria = {}) {
  window.localStorage.setItem(
    `broadcast-draft:${userId}`,
    JSON.stringify({ criteria: { programIds: [], subjectId: null, statuses: ['IN_COURSE'], overdueDebt: false, staffRoles: [], ...criteria } }),
  );
}
