# Using your own icons

Every icon in the app is a file in `app/src/main/res/drawable/`:

| File name        | Where it is used                 |
|------------------|----------------------------------|
| ic_nav_home      | bottom bar: Home                 |
| ic_nav_tasks     | bottom bar: Tasks                |
| ic_nav_stages    | bottom bar: Stages               |
| ic_nav_lock      | bottom bar: Lock                 |
| ic_nav_apps      | bottom bar: Apps                 |
| ic_play / ic_stop| audio preview play / stop        |
| ic_close         | remove / delete                  |
| ic_star / ic_star_outline | top-priority task       |
| ic_timer         | focus on this task               |
| ic_arrow         | move task to today               |
| ic_audio         | audio row                        |
| ic_check         | granted / selected               |
| ic_lock          | lock popup                       |
| ic_sun           | morning gate                     |
| ic_target, ic_add| spare                            |

## How to replace one
1. In Android Studio: right-click `res/drawable` > New > Vector Asset (or Image Asset), pick your icon,
   and give it EXACTLY the same name as above (for example `ic_nav_home`).
2. If Android Studio complains about a duplicate, delete my `ic_nav_home.xml` first (or tick "Overwrite").
   A `.png` and an `.xml` with the same name cannot both exist.
3. Rebuild. Nothing else to change.

## Sizing
You don't need to resize anything. Every icon is drawn into a fixed box (20-24dp, 44dp for the lock/sun)
and scaled to fit automatically, so a huge PNG or a 512px vector shrinks to the right size.

## Colour
Icons are tinted by the app (white, accent colour, red for delete...). Use single-colour artwork on a
transparent background. Multi-colour icons will be flattened to one colour.
