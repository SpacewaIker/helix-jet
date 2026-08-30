- HJPicker: additional mappings (A-Enter for open in background, C-t toggle preview)
    - show number of entries (shown/total)
    - Show file name above file preview
- settings.gradle.kts: clone repos in parallel
- settings.gradle.kts: don't reset repo if it's already at the right commit
- plugin/src/main/resources/runtime: have list of files be tied to something, not hardcoded
- workspace syntax symbol picker: parsing 1500 files ~ 2s. Not bad if done in background thread.
    - Could check last file update time to see if a file needs reparsing to keep up to date?
    - How to determine which directories to search? -> use .gitignore/.p4ignore + custom ignore
- workspace symbol syntax picker: don't re-parse files if they haven't changed
- Make HJPrompt, autocomplete dialog prettier
- Remove empty info box when using HJPrompt
- Fix autocomplete in HJPrompt
- Look into HJPicker fuzzy finder, it's not giving results in the order I expect (exact matches first)

Fix:

- n after search in one editor instance triggers clearing readonly in other instance
- Indent still not great (keeps selection, doesn't indent selected lines only)
- Word/back is broken again sometimes. Looks like it doesn't correctly update the cursor
- Replace doesn't work (well) either
- inline hints (e.g. function param name) counts as inlay text and prevents esc to exit insert mode
- open does not set the right indentation
- Make gw faster/run on another thread
- HJRange is not updated after accepting inline autocomplete
- file picker takes a long time to load a file
    - Improved a bit but still needs work. Preview update seems ok. Typing in search field is very slow still.
    - Looks like it could be stalling the EDT? See stack trace sc

- HJJumpLabelOverlay: folds in editor means text isn't visible and shouldn't be considered
- check that wherever i use visualposition doesn't mess things up with folds



- HJActions, HJCommands, HJSyntaxLoader are still objects, not services. Is this okay?
