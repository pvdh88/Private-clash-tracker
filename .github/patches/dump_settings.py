import pathlib

source = pathlib.Path("app/src/main/java/org/clashledger/app/MainActivity.java").read_text(encoding="utf-8")
start = source.find("private void settingsMenu")
out = pathlib.Path("docs/settings_menu_dump.txt")
out.parent.mkdir(exist_ok=True)
if start < 0:
    out.write_text("settingsMenu not found\n", encoding="utf-8")
else:
    out.write_text(source[start:start + 4000] + "\n", encoding="utf-8")
print("Settings menu dump written")
