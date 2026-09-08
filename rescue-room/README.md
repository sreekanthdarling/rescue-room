# Rescue Room — Room 1

A standalone HTML/CSS/JavaScript mobile escape-room prototype. All project files live in this directory; no archery project files are used or changed.

## Run

From this directory, run `node server.js`, then open http://127.0.0.1:4173 in a browser. Node 18+ is sufficient; no installation or npm dependencies are needed. ES modules require serving the page over HTTP rather than opening the HTML with a file URL.

Run the state-engine tests with `node --test tests/engine.test.js`.

## Puzzle (spoiler)

The field notes give the order MOON → STAR → EYE → SUN. The wall chart maps these to 2, 7, 3, 5. The maintenance card in the drawer says to add one to each value. The unique rescue code is **3846**. The complete code is never displayed as a clue.

## Behavior

- READY waits indefinitely. Start establishes a 90-second deadline using `performance.now()`.
- PLAYING computes remaining time from the deadline each frame. Clues and keypad do not pause time; returning to a backgrounded tab catches up immediately.
- A wrong four-digit entry clears the input, displays WRONG CODE and moves the deadline five seconds earlier. An incomplete or duplicate ENTER has no effect.
- A correct code freezes the timer immediately, disables the device, removes the restraints, opens the door and displays RESCUE SUCCESS.
- The last ten seconds pulse red and sound warning beeps when sound is enabled. Audio is initialized by the Start gesture and can be muted.
- At zero, input is disabled, the room flashes/shakes with smoke, fades to black, and MISSION FAILED fades in. Reduced-motion preferences suppress animated effects.
- Play Again/Try Again returns to READY and requires a new Start tap.
- Native buttons support mouse, touch, and keyboard activation. Keypad also supports number keys, Backspace and Enter; Escape closes inspection.

## Structure

- `engine.js`: isolated state transitions, deadline, input and inspection state.
- `room.js`: room duration, solution and reusable clue records.
- `app.js`: DOM rendering, input and Web Audio effects.
- `index.html`, `style.css`, `reference.css`: responsive 9:16 reference composition, live clock overlays, aligned object targets, inventory and inspection panels.
- `assets/room-1-reference.png`: unchanged copy of the user-supplied reference artwork. Rigid clipped layers reuse the same artwork for subtle character/chair and shoulder movement during PLAYING, with reduced-motion support. Both red clock displays are live SVG segments covering the illustrated static readouts.
- `server.js`, `package.json`: dependency-free local development server and commands.
- `tests/engine.test.js`: deterministic tests with an injected clock.

## Scope and limitations

### Character performance

`character.js` is an isolated presentation controller; `character.css` and a small inline SVG mouth overlay add breathing, head turns, shoulder effort and occasional chair/body shakes. The approved background asset and object coordinates are unchanged. Three mouth poses switch only during help calls; actual speech start/end and optional word-boundary events drive approximate lip timing. They are not phoneme-accurate lip sync.

The first browser speech call is requested from the Start gesture. Subsequent lines have 8–11 seconds of silence between them, shortened to about 2.5 seconds in the last ten seconds. No lines overlap. Success, failure, muting, page exit and hiding the tab cancel the active utterance. Missing speech events have bounded watchdogs. If speech is unsupported, muted, or rejected, short captions and mouth poses remain available. Actual voice selection and audible output depend on the browser/OS speech engine and device audio settings. Reduced motion disables body/head animations. Run all checks with `node --test tests/*.test.js`.

The visual composition uses the supplied 2D reference artwork directly. The original three clue records, code and state engine are unchanged. Books/clock open the existing notes; the framed picture opens the existing symbol chart; the desk drawer/chest open the existing maintenance card. Inventory revisits inspected clues, Hint repeats exploration instructions, and Settings exposes the existing sound toggle. Google Fonts are optional; system fonts are used if unavailable. No backend, accounts, tracking, prizes or additional rooms. Browser audio depends on platform settings. Physical-device testing remains outstanding. As a client-only puzzle, the solution can be discovered by reading its source.
