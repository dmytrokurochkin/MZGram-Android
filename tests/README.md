# Plugin tests

Static checks of the MZGram plugin runtime and of a plugin corpus. They need neither Java
nor a device and run on plain CPython.

```
python3 -m pytest tests/ -q
```

A full run takes about five minutes; most of it is parsing the R8 `mapping.txt` and the
AST of every plugin in the corpus.

## Corpus

The corpus is not part of the repository. By default the tests look for it in
`~/mzgram-plugin-corpus`; environment variables override the paths:

| variable | contents |
|---|---|
| `MZGRAM_PLUGIN_CORPUS` | folder with `*.plugin` files, one Python file per plugin |
| `MZGRAM_PLUGIN_ARCHIVES` | folder with `*.eaf` files, ZIP archives of multi-file plugins |

If the folder is missing, the tests are skipped instead of failing.

To build a corpus, collect `.plugin` files (for example from Telegram chat exports,
`ChatExport_*` folders) and deduplicate them by the sha256 of their content. ZIP archives
(`.eaf`) go to a separate folder, since they are not Python.

## Baselines

`tests/baselines/*.json` lists known gaps at the time of writing. A test fails only on a gap
that is not in the list; otherwise the 104 unported Java classes would keep it red forever.
When a gap is closed, its entry is removed from the list: the test shows such leftovers in
its error text but does not fail because of them.

## R8

The shrinking checks read `TMessagesProj/build/outputs/mapping/release/`. If those outputs
are missing or older than `proguard-rules.pro`, the checks are skipped and name the rule
that was not in that build: a green result on a stale mapping means nothing.
