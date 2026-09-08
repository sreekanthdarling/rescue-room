import {Game, STATES} from './engine.js';
import {room} from './room.js';
import {CharacterController} from './character.js';
import {InspectionView} from './inspection.js';
const game = new Game(room);
const $ = id => document.getElementById(id);
const characterElement=document.querySelector('.character-motion');
const character=new CharacterController({paint:pose=>{
  for(const key of ['active','talking','mouth','urgent','shake','voiceMode','spokenLines','voiceName','availableVoices']){const value=String(pose[key]??0);if(characterElement.dataset[key]!==value)characterElement.dataset[key]=value;}
  const caption=$('character-caption');caption.hidden=!pose.caption;if(caption.textContent!==pose.caption)caption.textContent=pose.caption;
}});
const auxiliaryPanels={
  hint:{tag:'ROOM 1 / HOW TO PLAY',title:'Look. Think. Combine.',body:'Explore the room. Combine three clues. Unlock the exit before time runs out.',hint:'Tap the books, framed picture and drawers. Revisit inspected clues in the inventory.'},
  settings:{tag:'ROOM 1 / SETTINGS',title:'Sound',body:'',hint:''}
};
const inventoryLabels={note:'FIELD NOTES',chart:'SYMBOL CHART',drawer:'MAINTENANCE CARD'};
const inventoryIcon='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3h10l4 4v14H5z M15 3v5h4 M8 12h8 M8 16h6"/></svg>';
const segmentPaths=['M3 1h14l-3 4H6z','M18 2v14l-4-3V6z','M18 18v14l-4-4v-7z','M3 33h14l-3-4H6z','M2 18v14l4-4v-7z','M2 2v14l4-3V6z','M3 17l3-2h8l3 2-3 2H6z'];
const digitSegments=['012345','12','01346','01236','1256','02356','023456','012','0123456','012356'];
function displayDigits(id,value){const el=$(id);if(el.dataset.value===value)return;el.dataset.value=value;el.innerHTML=/^\d\d:\d\d$/.test(value)?[...value].map(d=>d===':'?'<svg class="segment-colon" viewBox="0 0 4 34"><rect y="10" width="4" height="4"/><rect y="22" width="4" height="4"/></svg>':`<svg class="segment-digit" viewBox="0 0 20 34">${[...digitSegments[+d]].map(s=>`<path d="${segmentPaths[+s]}"/>`).join('')}</svg>`).join(''):`<span class="device-state">${value}</span>`;}
const inspectionTimer=document.createElement('div');
inspectionTimer.className='inspection-timer';
inspectionTimer.setAttribute('aria-label','Inspection time remaining');
$('modal').prepend(inspectionTimer);
let audio, sound=true, lastBeep=-1, lastState=STATES.READY, lastPanel=null, previousFocus;
function tone(frequency=660,duration=.07){if(!sound || !audio)return;const osc=audio.createOscillator(), gain=audio.createGain();osc.connect(gain);gain.connect(audio.destination);osc.frequency.value=frequency;gain.gain.setValueAtTime(.045,audio.currentTime);gain.gain.exponentialRampToValueAtTime(.001,audio.currentTime+duration);osc.start();osc.stop(audio.currentTime+duration);}
function enableAudio(){const Context=window.AudioContext||window.webkitAudioContext;if(!Context)return;audio ||= new Context();audio.resume().catch(()=>{});}
function close(){game.panel=null;render();previousFocus?.focus({preventScroll:true});}
const inspection=new InspectionView({modal:$('modal'),close});

const apiBase = window.location.port === '4173'
  ? `${window.location.protocol}//${window.location.hostname}:8081`
  : '';

let sessionGeneration = 0;
let sessionId = null;
let starting = false;
let pendingOutcome = null; // 'WIN' | 'LOSE' | null
let heartbeatTimer = null;
let emailSubmitted = false;

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}

function startHeartbeat(gen, sid) {
  stopHeartbeat();
  heartbeatTimer = setInterval(async () => {
    if (sessionGeneration !== gen || sessionId !== sid || game.state !== STATES.PLAYING) {
      stopHeartbeat();
      return;
    }
    try {
      await fetch(apiBase + '/api/sessions/' + sid + '/heartbeat', { method: 'POST', cache: 'no-store' });
    } catch {
      // Fail-open
    }
  }, 10000);
}

async function reportOutcome(gen, sid, outcome, remainingMs) {
  if (sessionGeneration !== gen || !sid) return;
  try {
    if (outcome === 'WIN') {
      const remainingSeconds = Math.ceil(remainingMs / 1000);
      await fetch(apiBase + '/api/sessions/' + sid + '/win', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ remainingSeconds })
      });
    } else if (outcome === 'LOSE') {
      await fetch(apiBase + '/api/sessions/' + sid + '/lose', {
        method: 'POST'
      });
    }
  } catch {
    // Fail-open
  }
}

async function handleSessionStart(gen) {
  if (starting || sessionId) return;
  starting = true;
  try {
    const res = await fetch(apiBase + '/api/sessions/start', { method: 'POST', cache: 'no-store' });
    if (sessionGeneration !== gen) {
      starting = false;
      return;
    }
    if (res.ok) {
      const data = await res.json();
      if (sessionGeneration === gen) {
        sessionId = data.sessionId;
        starting = false;
        if (game.state === STATES.PLAYING) {
          startHeartbeat(gen, sessionId);
        }
        if (pendingOutcome && sessionId) {
          const outcome = pendingOutcome;
          pendingOutcome = null;
          reportOutcome(gen, sessionId, outcome, game.remaining);
        }
      } else {
        starting = false;
      }
    } else {
      starting = false;
    }
  } catch {
    if (sessionGeneration === gen) {
      starting = false;
    }
  }
}

function onGameStart() {
  sessionGeneration++;
  sessionId = null;
  starting = false;
  pendingOutcome = null;
  stopHeartbeat();
  emailSubmitted = false;
  const emailContainer = $('email-container');
  if (emailContainer) emailContainer.hidden = true;
  const emailInput = $('email-input');
  if (emailInput) { emailInput.value = ''; emailInput.disabled = false; }
  const emailFeedback = $('email-feedback');
  if (emailFeedback) emailFeedback.textContent = '';
  const emailSubmit = $('email-submit');
  if (emailSubmit) emailSubmit.disabled = false;

  character.warmupAudio?.();
  handleSessionStart(sessionGeneration);
}

function onStateChanged(state, remaining) {
  const gen = sessionGeneration;
  if (state === STATES.PLAYING) {
    if (sessionId && !heartbeatTimer) {
      startHeartbeat(gen, sessionId);
    }
  } else {
    stopHeartbeat();
    if (state === STATES.SUCCESS || state === STATES.FAILED) {
      try {
        localStorage.setItem('rescueroom_has_played', 'true');
      } catch {}
      const outcome = state === STATES.SUCCESS ? 'WIN' : 'LOSE';
      if (!pendingOutcome) {
        pendingOutcome = outcome;
        if (sessionId) {
          const sid = sessionId;
          pendingOutcome = null;
          reportOutcome(gen, sid, outcome, remaining);
        }
      }
      if (state === STATES.SUCCESS) {
        const emailContainer = $('email-container');
        if (emailContainer) emailContainer.hidden = false;
      }
    }
  }
}

function onReset() {
  sessionGeneration++;
  sessionId = null;
  starting = false;
  pendingOutcome = null;
  stopHeartbeat();
  emailSubmitted = false;
  const emailContainer = $('email-container');
  if (emailContainer) emailContainer.hidden = true;
  const emailInput = $('email-input');
  if (emailInput) { emailInput.value = ''; emailInput.disabled = false; }
  const emailFeedback = $('email-feedback');
  if (emailFeedback) emailFeedback.textContent = '';
  const emailSubmit = $('email-submit');
  if (emailSubmit) emailSubmit.disabled = false;
}

const emailForm = $('email-form');
if (emailForm) {
  emailForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!sessionId || emailSubmitted) return;
    const emailInput = $('email-input');
    const emailVal = emailInput ? emailInput.value.trim() : '';
    if (!emailVal) return;
    const sid = sessionId;
    const gen = sessionGeneration;
    const emailSubmit = $('email-submit');
    const emailFeedback = $('email-feedback');
    if (emailSubmit) emailSubmit.disabled = true;
    if (emailFeedback) emailFeedback.textContent = 'Saving email…';
    try {
      const res = await fetch(apiBase + '/api/sessions/' + sid + '/email', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: emailVal })
      });
      if (sessionGeneration !== gen || sessionId !== sid) return;
      if (res.ok) {
        emailSubmitted = true;
        if (emailFeedback) emailFeedback.textContent = 'Email saved successfully!';
        if (emailInput) emailInput.disabled = true;
      } else {
        const err = await res.json().catch(() => ({}));
        if (emailFeedback) emailFeedback.textContent = err.message || 'Could not save email. Please try again.';
        if (emailSubmit) emailSubmit.disabled = false;
      }
    } catch {
      if (sessionGeneration === gen && sessionId === sid) {
        if (emailFeedback) emailFeedback.textContent = 'Network error. Rescue result unaffected.';
        if (emailSubmit) emailSubmit.disabled = false;
      }
    }
  });
}

function render(){
  const seconds=Math.ceil(game.remaining/1000), state=game.state;
  $('game').dataset.state=state;$('game').classList.toggle('urgent',state===STATES.PLAYING&&game.remaining<=10000);
  character.sync({state,remaining:game.remaining,muted:!sound,visible:!document.hidden});
  $('timer').textContent=`${String(Math.floor(seconds/60)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}`;
  displayDigits('timer-digits',$('timer').textContent);
  inspectionTimer.textContent=`RESCUE WINDOW  ${$('timer').textContent}`;
  $('start-screen').hidden=state!==STATES.READY;
  $('status-caption').textContent=state===STATES.READY?'AWAITING YOUR SIGNAL':state===STATES.SUCCESS?'RESCUE WINDOW SECURED':state===STATES.FAILED?'SIGNAL LOST':'SEARCH THE ROOM · EVERY SECOND COUNTS';
  $('clue-count').textContent=`${game.seen.size} / 3 CLUES INSPECTED`;
  document.querySelectorAll('[data-object]').forEach(el=>{el.disabled=state!==STATES.PLAYING;});
  document.querySelectorAll('[data-inventory]').forEach(el=>{const id=el.dataset.inventory,seen=game.seen.has(id);el.disabled=!seen||state!==STATES.PLAYING;el.setAttribute('aria-label',`${inventoryLabels[id]} — ${seen?'inspect again':'not inspected'}`);if(el.dataset.seen!==String(seen)){el.dataset.seen=seen;el.innerHTML=seen?inventoryIcon+`<span>${inventoryLabels[id]}</span>`:'';}});
  $('inspect-last').disabled=game.seen.size===0||state!==STATES.PLAYING;
  const panel=game.panel;
  $('modal').hidden=!panel || state!==STATES.PLAYING;
  inspection.update({panel,source:previousFocus,seen:game.seen});
  if(panel){
    const isKey=panel==='keypad', clue=room.clues.find(c=>c.id===panel)||auxiliaryPanels[panel];
    $('keypad').hidden=!isKey;$('clue-content').hidden=isKey||panel==='settings';$('settings-content').hidden=panel!=='settings';
    $('modal-tag').textContent=isKey?'EXIT CONTROL / FOUR DIGITS':clue.tag;
    $('modal-title').textContent=isKey?'Unlock the rescue':clue.title;
    if(!isKey){$('clue-body').textContent=clue.body;$('clue-hint').textContent=clue.hint;}
    $('code-display').textContent=(game.input+'____').slice(0,4).split('').join(' ');
    $('feedback').textContent=game.message;
    if(panel!==lastPanel)$('close').focus({preventScroll:true});
  }
  lastPanel=panel;
  const ended=state===STATES.SUCCESS||state===STATES.FAILED;
  $('result').hidden=!ended;
  $('device-text').textContent=state===STATES.SUCCESS?'DISABLED':state===STATES.FAILED?'OFFLINE':$('timer').textContent;
  displayDigits('device-digits',$('device-text').textContent);
  if(state!==lastState){
    onStateChanged(state, game.remaining);
    if(ended){const won=state===STATES.SUCCESS;$('result-tag').textContent=won?'EXIT OPEN · RESTRAINTS RELEASED':'RESCUE WINDOW CLOSED';$('result-title').textContent=won?'RESCUE SUCCESS':'MISSION FAILED';$('result-body').textContent=won?`You brought them home with ${seconds} seconds to spare. The device is disabled.`:'The room went dark. Reset the room and try again.';$('restart').textContent=won?'PLAY AGAIN ↗':'TRY AGAIN ↗';$('announcement').textContent=$('result-title').textContent;$('restart').focus({preventScroll:true});tone(won?880:100,.4);}
    lastState=state;
  }
  if(state===STATES.PLAYING&&game.remaining<=10000&&seconds!==lastBeep){lastBeep=seconds;tone(920,.12);if(seconds===10)$('announcement').textContent='Ten seconds remaining';}
}
$('start').addEventListener('click',()=>{if(game.start()){enableAudio();window.speechSynthesis?.resume?.();tone();render();onGameStart();document.querySelector('[data-object="note"]').focus({preventScroll:true});}});
$('sound').addEventListener('click',()=>{sound=!sound;enableAudio();$('sound').textContent=sound?'SOUND ON':'SOUND OFF';$('sound').setAttribute('aria-label',sound?'Mute sound':'Enable sound');tone();render();});
document.querySelectorAll('[data-object]').forEach(el=>el.addEventListener('click',()=>{previousFocus=el;game.inspect(el.dataset.object);tone(400,.035);render();}));
document.querySelectorAll('[data-inventory]').forEach(el=>el.addEventListener('click',()=>{previousFocus=el;game.inspect(el.dataset.inventory);render();}));
$('inspect-last').addEventListener('click',()=>{const id=[...game.seen].at(-1);if(id){previousFocus=$('inspect-last');game.inspect(id);render();}});
function key(value){if(value==='CLEAR')game.clear();else if(value==='ENTER'){game.submit();tone(game.message?150:500,.1);}else game.digit(value);render();}
['1','2','3','4','5','6','7','8','9','CLEAR','0','ENTER'].forEach(value=>{const b=document.createElement('button');b.textContent=value;if(value.length>1)b.className='action';b.addEventListener('click',()=>key(value));$('keys').append(b);});
$('close').addEventListener('click',close);
let followCtaClicked = false;
let returnListenerAttached = false;

function checkReturnFromFacebook() {
  if (followCtaClicked && !$('follow-modal').hidden) {
    const continueBtn = $('follow-continue');
    if (continueBtn && continueBtn.hidden) {
      continueBtn.hidden = false;
      continueBtn.focus({preventScroll:true});
      detachReturnListeners();
    }
  }
}

function handleVisibilityOrFocus() {
  if (document.visibilityState === 'visible') {
    checkReturnFromFacebook();
  }
}

function attachReturnListeners() {
  if (returnListenerAttached) return;
  document.addEventListener('visibilitychange', handleVisibilityOrFocus);
  window.addEventListener('focus', checkReturnFromFacebook);
  window.addEventListener('pageshow', checkReturnFromFacebook);
  returnListenerAttached = true;
}

function detachReturnListeners() {
  if (!returnListenerAttached) return;
  document.removeEventListener('visibilitychange', handleVisibilityOrFocus);
  window.removeEventListener('focus', checkReturnFromFacebook);
  window.removeEventListener('pageshow', checkReturnFromFacebook);
  returnListenerAttached = false;
}

function executeRestart() {
  const resultOverlay = $('result');
  if (resultOverlay) resultOverlay.hidden = true;
  const followModal = $('follow-modal');
  if (followModal) followModal.hidden = true;
  detachReturnListeners();
  followCtaClicked = false;
  game.reset();
  onReset();
  document.getElementById('announcement').textContent='';
  lastBeep=-1;
  render();
  $('start').focus({preventScroll:true});
}

$('restart').addEventListener('click',()=>{
  let hasPlayed = false;
  let followAck = false;
  try {
    hasPlayed = localStorage.getItem('rescueroom_has_played') === 'true';
    followAck = localStorage.getItem('rescueroom_follow_acknowledged') === 'true';
  } catch {}

  if (hasPlayed && !followAck) {
    followCtaClicked = false;
    const resultOverlay = $('result');
    if (resultOverlay) resultOverlay.hidden = true;
    const followModal = $('follow-modal');
    if (followModal) followModal.hidden = false;
    const continueBtn = $('follow-continue');
    if (continueBtn) continueBtn.hidden = true;
    const followFb = $('follow-fb');
    if (followFb) followFb.focus({preventScroll:true});
  } else {
    executeRestart();
  }
});

const followFbBtn = $('follow-fb');
if (followFbBtn) {
  followFbBtn.addEventListener('click', () => {
    followCtaClicked = true;
    attachReturnListeners();
  });
}

const followContinueBtn = $('follow-continue');
if (followContinueBtn) {
  followContinueBtn.addEventListener('click', () => {
    try {
      localStorage.setItem('rescueroom_follow_acknowledged', 'true');
    } catch {}
    executeRestart();
  });
}
document.addEventListener('keydown',e=>{if(e.repeat)return;if(e.key==='Escape'&&game.panel){close();return;}if(game.panel==='keypad'&&(/^\d$/.test(e.key)||e.key==='Backspace'||e.key==='Enter')){e.preventDefault();key(e.key==='Backspace'?'CLEAR':e.key==='Enter'?'ENTER':e.key);}
  if(e.key==='Tab'&&game.panel){const buttons=[...$('modal').querySelectorAll('button')].filter(b=>b.getClientRects().length);const first=buttons[0],last=buttons.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus({preventScroll:true});}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus({preventScroll:true});}}
});
document.addEventListener('visibilitychange',()=>{game.tick();render();});
window.addEventListener('pagehide',()=>character.stop());
function frame(){game.tick();render();requestAnimationFrame(frame);}render();requestAnimationFrame(frame);
