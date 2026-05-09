- editor actions "slow actions" on EDT
- Try to figure out if I need all of the logic within moveVertically & VerticallyVisual
- Might actually need "views" into an editor, otherwise if you have the same doc opened twice, it might conflict
- maybe add a widget to show whether the current file has AST support?
- textobject VCS change
- should probably use TS after all. AST is unusable in some languages/IDEs. Look at TS integration for textobjects
  and other places that currently use JB AST.
- open empty buffer when all are closed to maintain functionality?
- jumplist
- HJPicker: make column width max of text width up to max