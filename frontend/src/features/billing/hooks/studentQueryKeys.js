const REFUND_AFFECTED = ['refunds', 'refund-eligibility', 'billing-overview', 'credit-balance'];
const CASH_PAYMENT_AFFECTED = ['billing-overview', 'payments', 'credit-balance', 'refund-eligibility'];

function invalidateStudentResources(queryClient, studentId, resources) {
  return queryClient.invalidateQueries({
    predicate: ({ queryKey }) =>
      queryKey[0] === 'students' && String(queryKey[1]) === String(studentId) && resources.includes(queryKey[2]),
  });
}

export function invalidateRefundAffectedQueries(queryClient, studentId) {
  return invalidateStudentResources(queryClient, studentId, REFUND_AFFECTED);
}

export function invalidateCashPaymentAffectedQueries(queryClient, studentId) {
  return invalidateStudentResources(queryClient, studentId, CASH_PAYMENT_AFFECTED);
}
