import test from 'node:test';
import assert from 'node:assert/strict';
import {CharacterController} from '../character.js';

function setup({autoStart=true,noAudio=false}={}){
  let time=0,pose;const audios=[];let cancellations=0;
  class MockAudio {
    constructor(src) {
      this.src = src;
      this.currentTime = 0;
      this.paused = true;
      audios.push(this);
      if (autoStart) {
        setTimeout(() => { if (this.onplaying) this.onplaying(); else if (this.onplay) this.onplay(); }, 5);
      }
    }
    load() {}
    play() {
      this.paused = false;
      return Promise.resolve();
    }
    pause() {
      this.paused = true;
      cancellations++;
    }
  }
  const controller=new CharacterController({now:()=>time,AudioClass:noAudio?null:MockAudio,paint:p=>pose=p});
  let input={state:'READY',remaining:90000,muted:false,visible:true};
  const sync=changes=>{Object.assign(input,changes);controller.sync(input);};
  return {controller,sync,audios,advance:ms=>{time+=ms;sync();},end:()=>{audios.at(-1).onended?.();},get pose(){return pose;},get cancellations(){return cancellations;}};
}

test('no READY speech; first line begins on Start; lips move without retriggering speech',()=>{
  const s=setup();s.sync();assert.equal(s.audios.length,0);s.sync({state:'PLAYING'});assert.equal(s.audios.length,1);assert.equal(s.pose.talking,true);const mouth=s.pose.mouth;s.advance(240);assert.notEqual(s.pose.mouth,mouth);assert.equal(s.audios.length,1);
});
test('natural silent gap after end; final ten seconds shorten the next gap',()=>{
  const s=setup();s.sync({state:'PLAYING'});s.end();assert.equal(s.pose.talking,false);s.advance(7000);assert.equal(s.audios.length,1);s.advance(2500);assert.equal(s.audios.length,2);s.end();s.sync({remaining:10000});s.advance(2000);assert.equal(s.audios.length,3);
});
for(const state of ['SUCCESS','FAILED'])test(`${state} immediately cancels audio, clears lips/caption and all motion`,()=>{
  const s=setup();s.sync({state:'PLAYING'});s.sync({state});assert.equal(s.cancellations,1);assert.equal(s.pose.active,false);assert.equal(s.pose.talking,false);assert.equal(s.pose.caption,'');s.advance(30000);assert.equal(s.audios.length,1);assert.equal(s.pose.active,false);
});
test('mute and hidden-tab transitions cancel audio immediately; resume never queues old calls',()=>{
  const s=setup();s.sync({state:'PLAYING'});s.sync({muted:true});assert.equal(s.cancellations,1);s.advance(2600);assert.equal(s.audios.length,1);assert.equal(s.pose.talking,true);s.sync({visible:false});assert.equal(s.pose.active,false);s.advance(15000);assert.equal(s.audios.length,1);s.sync({visible:true,muted:false});assert.equal(s.audios.length,2);
});
test('unsupported audio keeps bounded visual help calls with silent gaps',()=>{
  const s=setup({noAudio:true});s.sync({state:'PLAYING'});assert.equal(s.pose.talking,true);assert.equal(s.pose.voiceMode,'unavailable');s.advance(2500);assert.equal(s.pose.talking,false);assert.equal(s.pose.caption,'');s.advance(1000);assert.equal(s.pose.talking,false);
});
test('missing end event cannot leave voice or lips running forever',()=>{
  const s=setup();s.sync({state:'PLAYING'});s.advance(7100);assert.equal(s.cancellations,1);assert.equal(s.pose.talking,false);assert.equal(s.audios.length,1);
});
test('replay resets speech scheduling and does not keep stale callbacks',()=>{
  const s=setup();s.sync({state:'PLAYING'});s.sync({state:'FAILED'});s.sync({state:'READY'});s.advance(10000);s.sync({state:'PLAYING',remaining:90000});assert.equal(s.audios.length,2);assert.equal(s.audios[0].src,s.audios[1].src);
});
