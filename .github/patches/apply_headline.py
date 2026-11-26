import pathlib
import sys

path = pathlib.Path("app/src/main/java/org/clashledger/app/MainActivity.java")
text = path.read_text(encoding="utf-8")

edits = [
    (
        'TextView village=title(ledger.townHall>0 ? "Town Hall "+ledger.townHall : "Your village",29);',
        'TextView village=title(ledger.village.isEmpty()||ledger.village.startsWith("#")?(ledger.townHall>0?"Town Hall "+ledger.townHall:"Your village"):ledger.village,29);',
    ),
    (
        'TextView player=text(ledger.village,12,0xFFCDD6DC,true);',
        'TextView player=text((ledger.townHall>0?"Town Hall "+ledger.townHall+" \u00b7 ":"")+ledger.playerTag,12,0xFFCDD6DC,true);',
    ),
    (
        'hero.addView(button("Switch village \u00b7 "+Ledger.all(this).size()+" saved",this::accounts),lp(-1,40));content.addView(hero);',
        'hero.addView(button("Switch village \\u00b7 "+Ledger.all(this).size()+" saved",this::accounts),lp(-1,40));space(hero,6);hero.addView(button("Ranked & war attacks \\u00b7 API key",()->startActivity(new android.content.Intent(this,ApiSettingsActivity.class))),lp(-1,40));content.addView(hero);',
    ),
    (
        'new android.content.Intent(this,ApiSettingsActivity.class))),lp(-1,40));content.addView(hero);',
        'new android.content.Intent(this,ApiSettingsActivity.class))),lp(-1,40));space(hero,6);hero.addView(button("Backup & restore",()->startActivity(new android.content.Intent(this,BackupActivity.class))),lp(-1,40));content.addView(hero);',
    ),
]

changed = False
for old, new in edits:
    if old in text:
        text = text.replace(old, new, 1)
        changed = True
    elif new in text:
        print("Already applied: " + new[:60])
    else:
        print("WARNING: could not find the line to change: " + old[:60])

if changed:
    path.write_text(text, encoding="utf-8")
    print("Headline patch applied")
sys.exit(0)
