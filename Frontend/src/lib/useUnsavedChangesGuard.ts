import { useEffect, useRef } from "react";
import { useBlocker } from "react-router";

// Leaving with unsaved changes: in-app navigation asks first, reload and tab close too.
// `allowLeaving` lets a new record's move to its stored page through: that navigation runs
// before React re-renders, so `dirty` is still the value from before the save.
//
// `dialog` goes on the page's ConfirmDialog, which adds its own title and message.
export function useUnsavedChangesGuard(dirty: boolean) {
  const leaving = useRef(false);
  const blocker = useBlocker(() => dirty && !leaving.current);

  useEffect(() => {
    if (!dirty) return;
    function warn(event: BeforeUnloadEvent) {
      event.preventDefault();
      event.returnValue = ""; // still needed by some browsers
    }
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  return {
    allowLeaving: () => {
      leaving.current = true;
    },
    dialog: {
      open: blocker.state === "blocked",
      onConfirm: () => blocker.proceed?.(),
      onCancel: () => blocker.reset?.(),
    },
  };
}
