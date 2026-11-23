# Key Navigator

Accessibility service with per-app physical-key controls. Enabled apps use spatial
D-pad navigation; Swedish BankID (`com.bankid.bus`) uses specialised reading and
PIN mappings; Wise (`com.transferwise.android`) has specialised passcode controls.
Spatial navigation was validated in Swish (`se.bankgirot.swish`) on
Android 14. This is an independent helper, not an official BankID or Swish app.
The Android package is `com.dumbermini.keynavigator`; the ROM build module is
`KeyNavigator`. Version 0.18 renames the former `eu.dumbdroid.bankidkeys`
package and requires a ROM update to retain system-app status. Per-app choices
start from their defaults under the new package.

## App selection

Open **Settings → Apps → Key navigation** for a searchable, alphabetically sorted list of apps with
launcher entries. Multiple launcher activities for one package share one toggle.
Background services and system packages without launcher entries are excluded.
The app declares launcher-intent visibility rather than `QUERY_ALL_PACKAGES`.

Toggles take effect immediately and are saved in app-private preferences. Every
per-app switch, including BankID and Swish, defaults to off and controls
standard spatial navigation. Existing explicit choices are preserved. BankID’s
custom keypad, collapsed-keypad and reading controls, and Wise’s passcode controls,
remain active regardless of their app switches, as long as the master service is on. Enabling either app’s switch adds
spatial navigation for keys not handled by its custom controls. Custom PIN digits
(including 2/5), Back/Delete, confirmation and reading-screen actions take priority,
so one key press never triggers both handlers. The page uses the native Settings
theme, an action-bar back button,
a master switch, and app icons with individual toggles. It has no launcher entry.

The ROM installs Key Navigator as a privileged app with an allowlisted
`WRITE_SECURE_SETTINGS` permission. On its first boot (or first settings-page
opening), it enables its accessibility service once and removes the obsolete
service component from the enabled list. Other accessibility services are
preserved. Subsequent boots and APK updates respect a user disabling the service.
The master switch controls the service directly. Development installs without
the privileged permission open Android's Accessibility settings instead.
The accessibility XML declares window retrieval, key filtering, gestures,
structural-node retrieval and accessibility-tool status from the start.

**Device validation (2026-11-21):** After installing the adapter in the ROM and
updating it to version 0.2, the user confirmed that a physical digit appears in
BankID's PIN field and Back deletes it. Service diagnostics confirmed a recognised
12-button keypad and accepted clicks. The SDK and AOSP module builds pass, along
with 81 adapter checks, 45 spatial-navigation checks, 25 app-policy checks, and
56 scrolling checks, plus 13 accessibility configuration checks and 37 Wise keypad checks and 14 Wise window-identity checks. Confirmation/signing and held-key behaviour have not yet been
validated on the device. The earlier ordinary sideloaded install was blocked by
BankID; the system app is accepted in this test.

Version 0.3 reading-screen validation: the user confirmed that D-pad Down scrolls
and activates the initially greyed-out button, and D-pad centre opens PIN entry,
without touching the screen. A touch-style swipe was required; accessibility
scroll actions alone did not register completion with BankID.

Version 0.4 added read-only Swish inspection, subscribed to
`se.bankgirot.swish` as well as BankID, and requested
`flagRetrieveInteractiveWindows`. Before this change, Android's accessibility
active-window ID was stuck on Home while Swish was foreground, and active-root
queries returned null. After the update, active and focused window IDs agree on
Swish, and the inspector reads its view tree, bounds, and supported click actions.
The observed home screen exposed 162 nodes and eight visible enabled clickable
elements. Swish field text and content descriptions are never read by the inspector.

Version 0.5 adds spatial navigation. The user confirmed that the physical D-pad
moves the highlight and Centre activates controls on the device.

Version 0.7 adds ancestor-list scrolling. The user confirmed in Gmail that 5 scrolls
down, 2 scrolls up, and the highlight stays inside the message list in both directions.

Version 0.8 corrects scroll ownership for expanded collections, as found in
PostNord: its `recycler_view` reports `scrollable=false` and offers no up/down or
forward/backward actions, while its surrounding `nested_scroll_view` owns scrolling.
Collection metadata and class names alone no longer stop ancestor selection.
Actual scrollability or advertised scroll actions identify the owning container.
Text-only portions expose the scrollable region itself as a navigation target
when no visible actionable descendants remain, keeping scrolling available.

Version 0.9 moves automatic list scrolling to D-pad Up/Down, one visible target
before the edge. Keys 2/5 now provide movement without scrolling to leave a list.
Gmail diagnostics exposed tree scans lasting 700 ms, exceeding Android's 500 ms
key-filter response deadline. Spatial scans/actions now run off that response
thread, with cached foreground-window checks and bounded pending input.

Version 0.10 prefers a direct row-position action for single-column collections
with usable, ascending row metadata. Each jump advances the first visible row by
one, retaining overlap. Missing metadata, a single visible row, unsupported
actions, and rejected position requests fall back to ordinary scrolling.
Position jumps use a short settling interval instead of the former half-second
wait. The highlight stays hidden during settling, including animated fallbacks.
PostNord's observed outer ScrollView has no row-position action, so its animation
remains app-controlled.

Version 0.11 removes the fixed half-second animation settling floor and the
service's additional 100 ms event batching. Matching scroll events set a quiet
deadline from their original uptime timestamp (120 ms for animated scrolls,
40 ms for row jumps), so delayed delivery does not restart the wait. Without
events, the fallback is 300 ms for animated scrolling or 60 ms for row jumps.
The highlight is rebuilt once settling ends; diagnostics report total time from
the scroll request to requesting the restored highlight. Actual display time also
depends on UI-tree retrieval and rendering. Missing child subtrees are fetched
in depth-first batches on Android 13+, while the focused root is queried without
forcing a fresh descendant batch every time. Scrolling clears the outline without
removing its transparent, non-interactive overlay window, avoiding extra window
events. Gmail's collection row metadata groups row controls (star, avatar,
attachment) for edge detection, so they do not postpone scrolling to the final
control within the last row. The edge calculation also excludes rows whose
centres are behind a full-width external tab collection anchored to the top or
bottom of the viewport. This handles Gmail's bottom tabs: Gmail reports the
covered email row as visible even though only a thin strip is actually exposed.
Floating buttons and collections in the middle of the screen do not shorten the
viewport.

Version 0.12 reserves 2/5 for typing whenever a text field has input focus,
including when the navigation highlight is elsewhere. This replaces the earlier
highlight-overlap exception so T9 input can receive both keys normally.

Version 0.13 recognises the collapsed BankID keypad drawer on the sign screen.
Centre clicks its visible, enabled SecurityCodeEditText inside the known drawer
handle when neither a keypad grid nor an acknowledgement button is visible.
It does not inspect the field's text. The normal held-key guard prevents the
opening press from carrying through into confirmation on the newly opened keypad.

Version 0.14 targets the enclosing drawer handle rather than the code field.
On-device testing showed that clicking the field returns success without opening
anything, while clicking the recognised handle opens the keypad. A guarded
DrawerProbe confirmed handle-click acceptance and the appearance of ButtonGrid;
it never entered or confirmed a PIN.

Version 0.15 recognises the approval keypad's exact Approve/Godkänn confirmation
labels alongside Identify/Sign. The observed Approve button previously caused
the strict whole-keypad matcher to reject digit entry as well. A read-only
keycap probe confirmed the label without inspecting the PIN field; after the
update, on-device diagnostics recognised the twelve-button keypad. The user then
confirmed that a physical digit entered successfully and Back deleted it, without
submitting a code.

Version 0.16 adds a touch-scroll workaround scoped to
`ch.protonmail.android`'s `MailboxList`. Direct tests of both its Down and
Forward accessibility actions returned success but left rows 0–5 at unchanged
positions; a vertical touch swipe advanced to rows 5–10. The adapter therefore
uses one bounded 200 ms swipe for that list when the requested direction is
available, keeps the highlight hidden until settling, and suppresses navigation
during the gesture. Other lists retain their existing action-based scrolling.
For collections without row metadata, a clickable direct child groups its
avatar and other controls as one row for edge detection.

Version 0.17 routes Proton email-body scrolling to the nearest vertical page
outside its expanded WebView. The inner WebView advertised Down but no Up even
when the outer page was scrolled; direct tests showed that the outer page's
native Down and Up actions each moved the body by about 400 pixels. This path
uses normal accessibility actions, not the mailbox swipe workaround. Navigation
targets are clipped to ancestor scroll viewports so links outside the visible
message area do not capture the highlight. Other apps retain their existing
scroll-owner rules.

Version 0.18 renames the package, moves configuration from the launcher into
Settings → Apps → Key navigation, and adds the native-themed master-switch and
app-icon layout. SDK and AOSP module builds pass. An old-package preview on the
current ROM passed the device test for searching Swish, switching navigation
off/on, reopening the page, and restoring the original choice. The renamed
privileged app and its one-time boot activation require the next ROM flash;
first-boot activation has not yet been tested on the device.

Version 0.2 declares `isAccessibilityTool` and requests
`flagIncludeNotImportantViews`. Before these changes the service reported no
active window, then a tree without `ButtonGrid`: Android omitted that structural
container from the default accessibility tree. These settings address window
access and the strict keypad matcher's need for that container on this setup.

Static analysis of that installed APK subsequently identified the local service
filter: a blacklist match always blocks; otherwise, when the native
untrusted-installer policy is enabled, a service is blocked only if its installer
is untrusted **and** its application lacks `ApplicationInfo.FLAG_SYSTEM`.
On Android 30+, the installer checked is `getInitiatingPackageName()`. Google
Play (`com.android.vending`) is in the initial trusted-installer list; native
configuration can update the list. The full native blacklist and installer lists
have not been recovered. Future BankID versions or policy updates may change
compatibility. Analysis and evidence are saved in
`/tmp/bankid-adapter-review/findings.txt` and its sibling `java-evidence` directory.

Include the adapter in the ROM; Android manages the service after its one-time
initial activation. Configure it under **Settings → Apps → Key navigation**.

## Wise passcode controls

These controls work while the master service is on, even with Wise's app toggle
off. Its toggle adds standard spatial navigation; recognised PIN controls take
priority, including digits 2/5.

- **0–9 / numpad digits:** click the corresponding PIN key.
- **Back:** delete one digit, or pass through normally when Delete is disabled.
- **Delete / Forward Delete:** click Delete.
- **Centre / Enter / numpad Enter:** click OK only when Wise enables it.

The matcher is scoped to `com.transferwise.android`, the activity class
`com.wise.ui.app_security.pin.PinEntryActivity`, its known container and the
complete observed 12-key Compose layout. Android accessibility window-state
events supply the activity class; its identity is tied to that window ID and
invalidated when the window is removed or replaced. Another Wise activity with
an identical numeric keypad does not qualify. No additional permission is needed.

Keys 1–9 must match their numeric values, accepting Unicode decimal digits such
as Arabic-Indic, Devanagari and full-width digits. The final row is Delete, 0, OK.
At the phone's normal 210 dpi density, Compose clips the child labels/icons of
that row but keeps the parent accessibility click actions. The adapter targets
those parents without coordinate taps or persistent display changes.

Only numeric keycap text is read. Headings, Delete/OK labels, content descriptions,
the PIN indicator and editable values are not read or used for recognition.
Changing translated wording therefore does not affect the matcher. Unknown
activities, incomplete layouts and reordered digits are rejected. Held keys act once.

**Version 0.20:** replaces the English heading and action-label checks with window
identity. If the service is started/reconnected while the PIN screen is already
open, leave and reopen that screen once so it reports its activity identity.
Until then, custom Wise PIN handling is unavailable. The device diagnostic reports
`Wise PIN activity confirmed` separately from keypad recognition. Host regressions
cover translated headings/action labels, localized numerals, missing/wrong activity
identities, and removed windows. This version still needs an on-device language-change test.

**Version 0.19 validation:** temporarily fitting the entire screen at 160 dpi
identified the bottom-row labels; the original density was restored. At normal
size, a guarded single zero click enabled Delete, and a Delete click returned
it to the disabled/empty state. No PIN was submitted. The user also confirmed that a physical 0 entered a digit
and Back deleted it with Wise’s app toggle off.
The opt-in test can be repeated only with an empty field explicitly confirmed:

```sh
adb shell am instrument -w -e empty-field-confirmed true com.dumbermini.keynavigator.probe/com.dumbermini.keynavigator.WisePinProbe
```

## Spatial navigation

Enabled apps show a blue outline around the selected visible control. D-pad arrows move
to nearby controls in that direction, favouring aligned rows and columns. Centre
uses the selected element's accessibility click action (or focus action for an
editable field without a click action). Back and typing pass through normally.

D-pad **Up/Down** automatically scroll the highlighted control's nearest vertical
scroll container when at most one more visible target remains in that direction.
This includes the second-to-last and last targets. Direct row-position jumps are
preferred where supported; otherwise directional accessibility scroll actions are
used, with backward/forward actions for older lists. The known Proton Mail `MailboxList` uses a bounded touch swipe because its
scroll actions report success without moving. There is no general gesture
fallback for other lists. If the container has reached its actual boundary or rejects
scrolling, ordinary directional movement continues. Horizontal-only containers
and non-scrolling collections embedded in a scrolling page are skipped.

Keys **2** and **5** move the highlight up and down without scrolling, allowing
navigation beyond the list to surrounding controls. Numpad 2/5 work too.
Whenever any text field has input focus, 2/5 pass through for normal typing and
T9 regardless of the highlight's position. They also pass through when an
editable field is highlighted. Focus-change events suspend the numeric shortcuts
immediately until the new input focus has been checked. BankID’s specialised mappings take priority when its standard navigation is enabled.

After scrolling, selection stays inside that list near its previous screen
position as new rows appear. If no actionable children are visible, the highlight
outlines the scrollable region itself; Up/Down scroll, while Centre does nothing.
Keys 2/5 can move out of that region. Activation and directional navigation pause
briefly while the scroll settles. Tree reads and actions run on a dedicated worker;
the accessibility key filter acknowledges keys immediately. Only one navigation
action can be pending, so rapid presses during a slow scan do not build up a burst.
Centre also checks that selection has not changed since the key was received.

The outline uses a non-touchable, non-focusable accessibility overlay, so touch
continues to work. The selection follows surviving elements across layout changes.
Centre rechecks the current tree before acting; if its target disappeared or moved
since the last update, it highlights the new selection without clicking it. Missing,
disabled and off-screen targets are excluded, and equal-bounds clickable wrappers
are deduplicated. No coordinate-click fallback is used.

Directional navigation covers visible controls, with automatic vertical edge scrolling.
There is no wrapping, and held keys act once.
The overlay is removed when leaving an enabled app,
opening another focused window, locking the phone, or disabling the service. The
service receives window-change events from all packages to detect app transitions,
but only navigates apps enabled in its settings and applies the enabled BankID
mappings separately. No labels or field values are read by the spatial navigator.

## BankID controls

When the keypad is collapsed and the **Security code** bar is visible, D-pad
centre clicks the drawer handle to open the keypad. This applies only to the recognised BankID drawer
structure, with no visible keypad or acknowledgement button. Other keys retain
their normal handling until PIN entry is open.

Before PIN entry, D-pad Down performs one upward swipe inside the acknowledgement
text per press. This uses the current WebView bounds, clipped to the window and
screen. BankID needs a touch-style swipe to register completion: accessibility
scrolling alone reached the bottom without activating its button in device testing.
D-pad centre clicks the recognised security-code acknowledgement button; BankID's
own handler controls whether reading is complete and PIN entry can open. Its
visual greyed-out state does not reliably match the accessibility enabled flag,
and its WebView can keep advertising forward-scroll at the bottom. Down never
clicks the button automatically. No second action runs during a swipe, and held
Centre cannot carry through into PIN submission after the screen changes.

| Key | Action on a recognised PIN screen |
| --- | --- |
| 0–9 / numpad digits | Click the matching BankID digit |
| Back | Click Delete if enabled; otherwise normal Back navigation |
| Delete / Forward Delete | Click Delete |
| Enter / numpad Enter / centre | Click the enabled Identify, Sign or Approve button |

Held keys act once. Other keys pass through. The service checks the foreground
accessibility tree for BankID's visible PIN field and all twelve expected keypad
buttons on every new key press. Unknown layouts and apps pass through. Supported
confirmation labels are Identify, Sign, Approve, Identifiera, Skriv under and Godkänn. BankID
updates or other languages can require matcher changes.

No network permission, root or BankID modification is required. BankID only shows the spatial highlight overlay when its app switch is on. The service
requests accessibility gestures for BankID reading screens and the Proton Mail
list workaround; PIN keys still
use accessibility clicks without coordinate fallbacks. PIN-field text is not read,
stored or logged. Only currently held
key identities are tracked, to suppress repeats and consume the matching release.
If BankID declines an accessibility click, a short message is shown; no coordinate
fallback is attempted. Another key-filtering accessibility service may conflict.

## Build

Requires Java 17, Android SDK platform 34 and build-tools 34.0.0 already installed.
No Gradle, downloaded dependencies or network access are needed.

```sh
python3 device/phh/treble/app/BankIdKeyAdapter/build.py
```

Set `ANDROID_SDK_ROOT` if the SDK is not at `~/Android/Sdk`. The script runs host
regression checks, builds the APK and verifies its signature. Its development
signing key lives in `build/development.p12`; retain it for compatible updates.
Use a proper release signing key before distribution. The `Android.bp` module is
included by `device/phh/treble/lineage.mk`, so DumberOS images contain the adapter
under `system/priv-app/KeyNavigator`. Build it with `m KeyNavigator` after
selecting the product. The module also installs
`system/etc/permissions/privapp-permissions-com.dumbermini.keynavigator.xml`;
the app and allowlist must ship together. A platform certificate is not required.
The standalone SDK APK above remains useful for development, but a fresh ADB
install alone does not make it a system app.

For a phone with the **same package** already installed in its ROM, a correctly release-signed
`adb install -r` update retains its system-app status and avoids a ROM flash.
Version 0.2 was tested this way. Use the same signing certificate as the ROM;
the standalone development certificate cannot update a release-signed app.
The 0.18 package rename is not an in-place APK update: build and flash the ROM
using the device's normal signed-image workflow. Do not remount or alter verified
partitions on a locked device. A temporary old-package Settings-page preview can
be installed on the previous ROM, but the renamed package and automatic service
activation require the new ROM. Settings discovers the page through its system-app
`com.android.settings.action.EXTRA_SETTINGS` mechanism; no Settings APK patch is needed.

Read-only service diagnostics are available with:

```sh
adb shell dumpsys activity service com.dumbermini.keynavigator/.BankIdKeyService
```

This reports the last generic action result, current keypad recognition, and
Delete availability. An unrecognised BankID layout reports view IDs, classes,
and structural flags only; no screen text or PIN contents are printed.
When Swish is foreground, the same command reports navigation status, the selected
bounds, and its structure and clickability. Window diagnostics include IDs and focus/active status;
if the active root is unavailable, inspection tries the focused application
window from the interactive-window list.

For device validation, start with an empty PIN prompt, enter one dummy digit,
check that Delete becomes enabled, then press Back and check that Delete becomes
disabled again. Do not submit a dummy PIN. Check held-key behaviour and that
physical keys behave normally outside BankID. ADB `input keyevent` bypasses parts
of the hardware input path on Android; use actual hardware or evdev for a full
service test.

An opt-in UI test for per-app switches is included in the separate probe APK:

```sh
python3 device/phh/treble/app/BankIdKeyAdapter/tests/build_device_probe.py
adb install -r device/phh/treble/app/BankIdKeyAdapter/build/probe/DeviceProbe.apk
adb shell am instrument -w com.dumbermini.keynavigator.probe/.SettingsProbe
adb uninstall com.dumbermini.keynavigator.probe
```

It opens Key Navigator, searches for Swish, switches its controls off/on, verifies
the service state with Swish foreground, and checks the saved choice on reopening
settings. It restores the original Swish setting and never activates a Swish control.
Pass `-e app bankid` to the instrumentation command to check BankID instead:
its app switch must toggle standard navigation while custom controls remain
enabled. This test never enters or submits a PIN. The BankID variant passed on the
old-package preview: standard navigation activated only with its switch on,
custom controls remained enabled in both states, and the original choice was
restored. PIN entry was not repeated during this test.

The position-action implementation is based on AndroidX's
[LinearLayoutManager source](https://raw.githubusercontent.com/androidx/androidx/androidx-main/recyclerview/recyclerview/src/main/java/androidx/recyclerview/widget/LinearLayoutManager.java),
which handles the request using a direct layout-position change. Accessibility
does not provide a universal animation-duration setting for other apps' scroll
actions; custom implementations may still animate.
