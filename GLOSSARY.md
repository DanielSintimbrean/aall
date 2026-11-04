# Glossary

- **Routine**: one of the fixed automation kinds: app access, focus, bedtime or grayscale.
- **Routine kind**: what a routine requires and does. `RoutineKind` holds its app rule, DND use and grayscale use.
- **Window**: hours and weekdays. An overnight window belongs to its starting weekday.
- **Draft**: the unsaved copy of a routine and focus duration that the editor changes. `src/draft.ts` holds its edits, validation and discard check.
- **Override**: an "only today" copy of a routine. It lasts until midnight or the end of its last window, whichever is later. Saving weekly settings ends it sooner.
- **Day off**: a local calendar date with no restrictions, booked at least 72 elapsed hours ahead.
- **Pause**: 15 minutes without restrictions, earned by solving the arithmetic challenge.
- **Decision**: what `Reconcile.decide` returns for a state and environment: running routines, next state, wanted effects, next change, errors and statuses.
- **Reconcile**: load state, get a decision, then save it and apply its effects to Android. `Engine` does this.
- **Snapshot**: the read-only view the UI polls. Reading it never changes phone state.
