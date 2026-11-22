import { useState } from 'react';
import {
  useCalendarFeed,
  useIssueCalendarFeedToken,
  useRevokeCalendarFeedToken,
} from '@/features/workspace/hooks/useCalendarFeed';

export function useCalendarFeedPanel() {
  const feed = useCalendarFeed();
  const [issued, setIssued] = useState(null);
  const [pendingAction, setPendingAction] = useState(null);
  const [revoked, setRevoked] = useState(false);
  const isActive = feed.data?.active === true;

  const issueToken = useIssueCalendarFeedToken({
    onIssued: (url) => setIssued({ url, regenerated: isActive }),
  });
  const revokeToken = useRevokeCalendarFeedToken();

  function generate() {
    setPendingAction(null);
    setRevoked(false);
    issueToken.mutate();
  }

  function revoke() {
    revokeToken.mutate(undefined, {
      onSuccess: () => {
        setIssued(null);
        setPendingAction(null);
        setRevoked(true);
      },
    });
  }

  return {
    feed,
    isActive,
    issued,
    revoked,
    pendingAction,
    issueToken,
    revokeToken,
    generate,
    revoke,
    askToRegenerate: () => setPendingAction('regenerate'),
    askToRevoke: () => setPendingAction('revoke'),
    cancelPendingAction: () => setPendingAction(null),
  };
}
