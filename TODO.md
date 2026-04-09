# Long-term todos

# Short-term todos

# Fixes

- editor actions "slow actions" on EDT
- Try to figure out if I need all of the logic within moveVertically & VerticallyVisual
- Might actually need "views" into an editor, otherwise if you have the same doc opened twice, it might conflict
- special cases of input keys <A-minus> for Alt+-, lt, gt, etc.
- moving with arrow keys in insert mode doesn't update the plugin's HJSelection
- undo changes the document, and thus what a caret offset corrsponds to, so HJSelection becomes out of date with doc
  state -> after undo/redo get back HJSelection from selectionModel?