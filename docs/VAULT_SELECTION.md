# Vault badge selection

The maintainer approved moving export selection inside each Vault card and combining it with the item's initial badge. The badge is a labelled native checkbox: its initial is visible when idle, a persistent corner indicator makes selection discoverable without hover, hover/focus shows the checkbox, and selected state shows a checkmark with a highlighted card.

The card border encloses both selection and expansion controls. The checkbox is a sibling of the Material expansion header, not an interactive child of a button-like header. Selecting never expands/collapses; expanding does not alter selection. The target is at least 44 pixels and keeps a visible keyboard focus outline. The existing ID-only selection, limit, paging/filter persistence and session clearing are unchanged.

Tests verify structural placement, accessible labelling/focus, independent selection/expansion and state styling. Human touch, keyboard, screen-reader and narrow-layout checks remain part of the UI walkthrough. No API/data migration is involved; revert this presentation slice to roll back.
