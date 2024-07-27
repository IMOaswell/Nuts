<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher.png" style="width: 30%;" />
</p>

# Nuts
[![GitHub last commit](https://img.shields.io/github/last-commit/IMOitself/Nuts)](https://github.com/IMOitself/Nuts/commits/)
[![Repository Size](https://img.shields.io/github/repo-size/IMOitself/Nuts)](https://www.google.com/search?q=llamas)


an android code editor, compatible with AIDE :D

> [!NOTE]
> this repo may have come in contact or contain peanuts

# feats
- uses [Rosemoe's sora-editor version 0.3.0](https://github.com/Rosemoe/sora-editor/tree/0.3.0) library that I ported to AIDE:D

> thats pretty much it for now:v

### feats i wanna add
- code editor
- [ ```fold code blocks``` ]
- file manager 
- [ ```all the basic file managing system plus...``` ```file lists ui like github,``` ```navigate folders as u type the path,``` ```view md files directly on file lists``` ]
- git system
-[  ```ui for git status,``` ```view staged and unstaged diffs,``` ```list commits,``` ```view commit diffs,``` ```and maybe a terminal ``` ]
- project management
- [ ```connect different files together in a directory ``` ]
- interpreter
- [ ```translate java lambda to lower java versions``` ]

# if u wanna contribute
- no androidx and appcompat
- no java 8 or above
- no stuffs only android studio supports

### dont know how?
> [!TIP]
> make an [issue](https://github.com/IMOitself/Nuts/issues/new/choose), ill help u somehow:D

### project structure
``` bash
app
- src
  - main
    - java
      - imo
        - nuts
          - app.java # handles crash
          - debug.java # handles crash
          - MainActivity.java
    - res
    - AndroidManifest.xml


assets   # not connected to project
libs   # connected using build.gradle
```

### commit message guides i use
<details markdown='1'><summary>Expand / Collapse</summary>

prefixes:
  - `feat:` add, remove or improve a feature
  - `fix:` fix a bug or something unwanted, obviously
  - `refactor:` for only improving code readability.
  
 i also add these before prefixes:
 - `●` meaning 'its stable at this point in time'
 - `!` means breaking change
 
 and probably this after commit message:
 - `;` noting theres more description for the commit message
 
 Examples:
- `feat: add chop() method for potato`
- `fix: crash when chopping a potatoes`
- `refactor: organize imports and format Potato class`
- `● feat: edit ReadMe.md as for my last commit`
- `!feat: replace all java files with kotlin`
- `feat: nothing just a long ahh message that cant fit as commit title;\n\n refactor: organize imports`

 
 i might also use other prefixes like `docs:`, `style:`, `test:` <br>
 but for the sake of simplicity i mainly use those:D


</details>

