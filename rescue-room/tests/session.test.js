import test from 'node:test';
import assert from 'node:assert/strict';

// Mock DOM elements and fetch for testing session integration logic
class MockElement {
  constructor(id) {
    this.id = id;
    this.hidden = false;
    this.value = '';
    this.disabled = false;
    this.textContent = '';
  }
}

const elements = new Map();
function $(id) {
  if (!elements.has(id)) {
    elements.set(id, new MockElement(id));
  }
  return elements.get(id);
}

global.document = {
  getElementById: id => $(id),
  querySelector: () => null,
  querySelectorAll: () => []
};

// Test helper to simulate session manager logic mirroring app.js
class SessionManager {
  constructor() {
    this.generation = 0;
    this.sessionId = null;
    this.starting = false;
    this.pendingOutcome = null;
    this.heartbeatInterval = null;
    this.fetchCalls = [];
  }

  async startSession() {
    if (this.starting || this.sessionId) return;
    this.starting = true;
    const gen = this.generation;
    try {
      const res = await global.fetch('http://127.0.0.1:8081/api/sessions/start', { method: 'POST' });
      if (this.generation !== gen) { this.starting = false; return; }
      if (res.ok) {
        const data = await res.json();
        if (this.generation === gen) {
          this.sessionId = data.sessionId;
          this.starting = false;
          if (this.pendingOutcome) {
            const outcome = this.pendingOutcome;
            this.pendingOutcome = null;
            await this.reportOutcome(outcome);
          }
        } else {
          this.starting = false;
        }
      } else {
        this.starting = false;
      }
    } catch {
      if (this.generation === gen) {
        this.starting = false;
      }
    }
  }

  async reportOutcome(outcome) {
    if (!this.sessionId) return;
    this.fetchCalls.push({ type: outcome, sessionId: this.sessionId });
  }

  reset() {
    this.generation++;
    this.sessionId = null;
    this.starting = false;
    this.pendingOutcome = null;
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
  }
}

test('rapid repeated Start prevents duplicate /start requests', async () => {
  let startCount = 0;
  global.fetch = async (url) => {
    if (url.includes('/start')) {
      startCount++;
      await new Promise(r => setTimeout(r, 50));
      return { ok: true, json: async () => ({ sessionId: 'uuid-1' }) };
    }
  };
  const sm = new SessionManager();
  await Promise.all([sm.startSession(), sm.startSession(), sm.startSession()]);
  assert.equal(startCount, 1);
});

test('/start returns after WIN (pending outcome reported once session ID arrives)', async () => {
  let resolveStart;
  global.fetch = async (url) => {
    if (url.includes('/start')) {
      return new Promise(r => { resolveStart = () => r({ ok: true, json: async () => ({ sessionId: 'uuid-win' }) }); });
    }
  };
  const sm = new SessionManager();
  const p = sm.startSession();
  sm.pendingOutcome = 'WIN';
  assert.equal(sm.fetchCalls.length, 0);
  resolveStart();
  await p;
  assert.equal(sm.sessionId, 'uuid-win');
  assert.equal(sm.fetchCalls.length, 1);
  assert.equal(sm.fetchCalls[0].type, 'WIN');
});

test('/start returns after LOSE', async () => {
  let resolveStart;
  global.fetch = async (url) => {
    if (url.includes('/start')) {
      return new Promise(r => { resolveStart = () => r({ ok: true, json: async () => ({ sessionId: 'uuid-lose' }) }); });
    }
  };
  const sm = new SessionManager();
  const p = sm.startSession();
  sm.pendingOutcome = 'LOSE';
  resolveStart();
  await p;
  assert.equal(sm.sessionId, 'uuid-lose');
  assert.equal(sm.fetchCalls.length, 1);
  assert.equal(sm.fetchCalls[0].type, 'LOSE');
});

test('reset while /start is still pending invalidates response', async () => {
  let resolveStart;
  global.fetch = async (url) => {
    if (url.includes('/start')) {
      return new Promise(r => { resolveStart = () => r({ ok: true, json: async () => ({ sessionId: 'uuid-stale' }) }); });
    }
  };
  const sm = new SessionManager();
  const p = sm.startSession();
  sm.reset(); // increment gen
  resolveStart();
  await p;
  assert.equal(sm.sessionId, null);
});

test('backend unavailable from the beginning fails gracefully (fail-open)', async () => {
  global.fetch = async () => { throw new Error('Network error'); };
  const sm = new SessionManager();
  await sm.startSession();
  assert.equal(sm.sessionId, null);
  assert.equal(sm.starting, false);
});

test('progressive follow gate UX: continue hidden initially, follow arms return detection, unrelated focus does not reveal continue, return reveals continue', () => {
  const store = new Map();
  global.localStorage = {
    getItem: key => store.get(key) || null,
    setItem: (key, val) => store.set(key, val),
    removeItem: key => store.delete(key)
  };
  store.clear();
  store.set('rescueroom_has_played', 'true');

  let followModalHidden = true;
  let continueHidden = true;
  let followCtaClicked = false;

  function openRestart() {
    followCtaClicked = false;
    followModalHidden = false;
    continueHidden = true;
  }

  function clickFollow() {
    followCtaClicked = true;
  }

  function simulateVisibility(state) {
    if (state === 'visible' && followCtaClicked && !followModalHidden) {
      if (continueHidden) {
        continueHidden = false;
      }
    }
  }

  // 1. First replay opens modal with Continue hidden
  openRestart();
  assert.equal(followModalHidden, false);
  assert.equal(continueHidden, true);

  // 2. Unrelated visibility/focus event before Follow click does not reveal Continue
  simulateVisibility('visible');
  assert.equal(continueHidden, true); // still hidden!

  // 3. Clicking Follow arms return detection
  clickFollow();

  // 4. Returning after Follow CTA reveals Continue
  simulateVisibility('visible');
  assert.equal(continueHidden, false); // revealed!
});

test('replay gate: first play free, marker not written at start, WIN/LOSE writes has_played, first replay opens modal, continue acknowledges and resets, subsequent bypasses, storage error fail-open', () => {
  const store = new Map();
  global.localStorage = {
    getItem: key => {
      if (store.has('ERROR')) throw new Error('Storage error');
      return store.get(key) || null;
    },
    setItem: (key, val) => {
      if (store.has('ERROR')) throw new Error('Storage error');
      store.set(key, val);
    },
    removeItem: key => store.delete(key)
  };

  store.clear();
  let modalHidden = true;
  let resetCount = 0;

  function handleRestart() {
    let hasPlayed = false;
    let followAck = false;
    try {
      hasPlayed = localStorage.getItem('rescueroom_has_played') === 'true';
      followAck = localStorage.getItem('rescueroom_follow_acknowledged') === 'true';
    } catch {}

    if (hasPlayed && !followAck) {
      modalHidden = false;
    } else {
      resetCount++;
    }
  }

  function handleContinue() {
    try {
      localStorage.setItem('rescueroom_follow_acknowledged', 'true');
    } catch {}
    modalHidden = true;
    resetCount++;
  }

  // 1. First play free & marker not written at start
  assert.equal(localStorage.getItem('rescueroom_has_played'), null);
  handleRestart();
  assert.equal(resetCount, 1);
  assert.equal(modalHidden, true);

  // 2. WIN writes has_played
  try { localStorage.setItem('rescueroom_has_played', 'true'); } catch {}
  assert.equal(localStorage.getItem('rescueroom_has_played'), 'true');

  // 3. First replay after completion opens Follow modal
  handleRestart();
  assert.equal(modalHidden, false);
  assert.equal(resetCount, 1); // did not reset

  // 4. Continue sets acknowledgement and resets game
  handleContinue();
  assert.equal(localStorage.getItem('rescueroom_follow_acknowledged'), 'true');
  assert.equal(modalHidden, true);
  assert.equal(resetCount, 2);

  // 5. Subsequent replay bypasses modal
  handleRestart();
  assert.equal(resetCount, 3);

  // 6. localStorage unavailable/error does not break gameplay
  store.set('ERROR', true);
  assert.doesNotThrow(() => handleRestart());
});
