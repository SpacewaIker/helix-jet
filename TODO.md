- editor actions "slow actions" on EDT
- Try to figure out if I need all of the logic within moveVertically & VerticallyVisual
- Might actually need "views" into an editor, otherwise if you have the same doc opened twice, it might conflict
- open empty buffer when all are closed to maintain functionality?
- HJPicker: additional mappings (A-Enter for open in background, C-t toggle preview)
- file explorer?
- settings.gradle.kts: clone repos in parallel
- settings.gradle.kts: don't reset repo if it's already at the right commit
- plugin/src/main/resources/runtime: have list of files be tied to something, not hardcoded
- workspace syntax symbol picker: parsing 1500 files ~ 2s. Not bad if done in background thread.
    - Could check last file update time to see if a file needs reparsing to keep up to date?
    - How to determine which directories to search? -> use .gitignore/.p4ignore + custom ignore
- language configuration class
    - parse file types, comment tokens?
- jumplist: should be one jumplist per view, not one
- configuration: some kind of ignore for file analysis. Per project?
    - extending other configuration?
- workspace symbol syntax picker: don't re-parse files if they haven't changed

Fix:

- b not working??
- gw not working

- HJJumpLabelOverlay: folds in editor means text isn't visible and shouldn't be considered
- ctrl shift U in picker with preview doesn't scroll up
    - check that wherever i use visualposition doesn't mess things up with folds
