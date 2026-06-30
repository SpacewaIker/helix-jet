- editor actions "slow actions" on EDT
- Try to figure out if I need all of the logic within moveVertically & VerticallyVisual
- Might actually need "views" into an editor, otherwise if you have the same doc opened twice, it might conflict
- maybe add a widget to show whether the current file has AST support?
- should probably use TS after all. AST is unusable in some languages/IDEs. Look at TS integration for textobjects
  and other places that currently use JB AST.
- open empty buffer when all are closed to maintain functionality?
- HJPicker: additional mappings (A-Enter for open in background, C-t toggle preview)
- xp doesn't set the selection on the right line
- bc in a split on last buffer doesn't close split and instead opens another buffer
- Jumplist: add to jumplist when necessary, picker, forward/backward, manual add
- last picker
- goto last accessed file, last modified file, last modification
- file explorer?
- scroll with cursor
- settings.gradle.kts: clone repos in parallel
- settings.gradle.kts: don't reset repo if it's already at the right commit
- plugin/src/main/resources/runtime: have list of files be tied to something, not hardcoded
- workspace syntax symbol picker: parsing 1500 files ~ 2s. Not bad if done in background thread.
    - Could check last file update time to see if a file needs reparsing to keep up to date?
    - How to determine which directories to search? -> use .gitignore/.p4ignore + custom ignore
- file type detector from extension glob

Fix:

- HJJumpLabelOverlay: folds in editor means text isn't visible and shouldn't be considered
- ctrl shift U in picker with preview doesn't scroll up
    - check that wherever i use visualposition doesn't mess things up with folds