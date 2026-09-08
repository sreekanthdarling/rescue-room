// Presentation only. Never reads or changes the rescue deadline, code, or input.
const AUDIO_MAP={
  'Somebody help me!':'somebody-help-me.mp3',
  'Please help!':'please-help.mp3',
  'Can anybody hear me?':'can-anybody-hear-me.mp3',
  'Please... get me out!':'please-get-me-out.mp3',
  'Hurry!':'hurry.mp3'
};
const CALLS=['Somebody help me!','Please help!','Can anybody hear me?','Please... get me out!'];
const URGENT_CALLS=['Hurry!','Please help!','Please... get me out!'];
export class CharacterController {
  constructor({now=()=>performance.now(),AudioClass=globalThis.Audio,paint=()=>{}}={}) {
    Object.assign(this,{now,AudioClass,paint});
    this.active=false;this.call=null;this.sequence=0;this.generation=0;this.nextCall=Infinity;this.lastEnd=-Infinity;
    this.urgent=false;this.muted=false;this.visible=true;this.voiceMode='ready';this.spokenLines=0;
    this.currentAudio=null;
  }
  getAudio(text){
    const filename=AUDIO_MAP[text];
    if(!filename||!this.AudioClass)return null;
    try{
      const audio=new this.AudioClass(`assets/voice/${filename}`);
      audio.preload='auto';
      return audio;
    }catch(e){
      console.warn('[Audio] failed to create Audio instance:', e);
      return null;
    }
  }
  warmupAudio(){
    for(const text of Object.keys(AUDIO_MAP)){
      const audio=this.getAudio(text);
      if(audio?.load)try{audio.load();}catch{}
    }
  }
  sync({state,remaining,muted=false,visible=true}) {
    const now=this.now(),active=state==='PLAYING';
    if(!active){if(this.active||this.call)this.stop();return;}
    if(!this.active){
      this.active=true;this.started=now;this.sequence=0;this.spokenLines=0;this.nextCall=now;this.lastEnd=-Infinity;this.urgent=false;
    }
    if((muted&&!this.muted)||(!visible&&this.visible))this.cancelCall();
    this.muted=muted;this.visible=visible;
    if(!visible){this.paint({active:false,talking:false,mouth:'rest',caption:'',urgent:false,shake:false,voiceMode:this.voiceMode});return;}
    if(remaining<=10000&&!this.urgent){this.urgent=true;this.nextCall=Math.min(this.nextCall,Math.max(now+200,this.lastEnd+1800));}
    if(this.call){
      if(this.call?.phase==='audio'&&now-this.call.started>7000)this.finish(this.call,true);
      if(this.call?.phase==='visual'&&now>=this.call.ends)this.finish(this.call);
    }
    if(!this.call&&now>=this.nextCall)this.speak();
    this.draw();
  }
  speak(){
    const now=this.now(),list=this.urgent?URGENT_CALLS:CALLS;
    const text=list[this.sequence++%list.length];
    const call={text,requested:now,started:now,phase:'pending',generation:++this.generation};
    this.call=call;
    if(this.muted){this.fallback(call);return;}
    const audio=this.getAudio(text);
    if(!audio){this.fallback(call);return;}
    this.playVoice(call,audio);
  }
  playVoice(call,audio){
    if(!this.isCurrent(call))return;
    call.phase='pending';call.requested=this.now();
    if(this.currentAudio&&this.currentAudio!==audio){
      try{
        this.currentAudio.pause();
        this.currentAudio.currentTime=0;
      }catch{}
    }
    this.currentAudio=audio;
    try{
      audio.currentTime=0;
      const playPromise=audio.play();
      call.phase='audio';call.started=this.now();this.spokenLines++;this.voiceMode='speaking';this.draw();

      if(playPromise!==undefined){
        playPromise.catch(err=>{
          console.warn('[Audio] play rejected:', err);
          if(this.isCurrent(call)){this.voiceMode='unavailable';this.fallback(call);}
        });
      }
      audio.onended=()=>{if(this.isCurrent(call))this.finish(call);};
      audio.onerror=err=>{
        console.warn('[Audio] error:', err);
        if(this.isCurrent(call)){this.voiceMode='unavailable';this.fallback(call);}
      };
    }catch(e){
      console.warn('[Audio] exception:', e);
      this.voiceMode='unavailable';this.fallback(call);
    }
  }
  isCurrent(call){return this.active&&this.visible&&this.call===call&&call.generation===this.generation;}
  detach(call){if(call?.audio){call.audio.onended=null;call.audio.onerror=null;}}
  fallback(call){
    if(!this.isCurrent(call))return;
    this.detach(call);
    if(this.currentAudio){try{this.currentAudio.pause();this.currentAudio.currentTime=0;}catch{}this.currentAudio=null;}
    if(!this.muted)this.voiceMode='unavailable';
    call.phase='visual';call.started=this.now();call.ends=call.started+Math.max(1200,call.text.length*(this.urgent?60:75));
  }
  finish(call,cancel=false){
    if(this.call!==call)return;
    this.detach(call);this.call=null;
    if(cancel&&this.currentAudio){try{this.currentAudio.pause();this.currentAudio.currentTime=0;}catch{}this.currentAudio=null;}
    this.lastEnd=this.now();this.nextCall=this.lastEnd+(this.urgent?2400:8000)+(this.sequence%3)*(this.urgent?250:1300);
    if(this.voiceMode==='speaking')this.voiceMode='ready';
    this.draw();
  }
  cancelCall(){
    const call=this.call;this.generation++;this.call=null;this.detach(call);
    if(this.currentAudio){try{this.currentAudio.pause();this.currentAudio.currentTime=0;}catch{}this.currentAudio=null;}
    this.lastEnd=this.now();this.nextCall=this.lastEnd+2500;
    if(this.voiceMode==='speaking')this.voiceMode='ready';
    this.draw();
  }
  draw(){
    const now=this.now(),call=this.call,talking=!!call&&(call.phase==='audio'||call.phase==='visual');
    const poses=['open','narrow','closed','open','narrow','open','closed'];
    const elapsed=call?now-call.started:0;
    const mouth=talking?poses[Math.floor(elapsed/115)%poses.length]:'rest';
    const cycle=(now-this.started)%11300;
    this.paint({active:this.active&&this.visible,talking,mouth,caption:talking?call.text:'',urgent:this.urgent,shake:cycle>6400&&cycle<6950,voiceMode:this.voiceMode,spokenLines:this.spokenLines,voiceName:'Bundled MP3',availableVoices:''});
  }
  stop(){
    this.cancelCall();this.active=false;this.urgent=false;this.nextCall=Infinity;
    this.paint({active:false,talking:false,mouth:'rest',caption:'',urgent:false,shake:false,voiceMode:this.voiceMode});
  }
}
