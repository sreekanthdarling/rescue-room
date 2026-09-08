export const STATES = Object.freeze({READY:'READY',PLAYING:'PLAYING',SUCCESS:'SUCCESS',FAILED:'FAILED'});
export class Game {
  constructor(room, now = () => performance.now()) { this.room=room; this.now=now; this.reset(); }
  reset() { this.state=STATES.READY; this.deadline=null; this.remaining=this.room.duration; this.input=''; this.seen=new Set(); this.panel=null; this.message=''; }
  start() { if(this.state!==STATES.READY)return false; this.deadline=this.now()+this.room.duration; this.state=STATES.PLAYING; return true; }
  tick() { if(this.state===STATES.PLAYING){this.remaining=Math.max(0,this.deadline-this.now()); if(this.remaining===0){this.state=STATES.FAILED;this.panel=null;}} return this.remaining; }
  inspect(id) { this.tick(); if(this.state!==STATES.PLAYING)return; this.panel=id; this.message=''; if(this.room.clues.some(c=>c.id===id))this.seen.add(id); }
  digit(value) { this.tick(); if(this.state===STATES.PLAYING && this.panel==='keypad' && /^\d$/.test(value) && this.input.length<4){this.input+=value;this.message='';} }
  clear() { if(this.state===STATES.PLAYING){this.input='';this.message='';} }
  submit() {
    this.tick();
    if(this.state!==STATES.PLAYING || this.panel!=='keypad' || this.input.length!==4)return false;
    const code=this.input; this.input='';
    if(code===this.room.code){this.state=STATES.SUCCESS;this.panel=null;this.message='';return true;}
    this.deadline-=5000;this.message='WRONG CODE';this.tick();return false;
  }
}
