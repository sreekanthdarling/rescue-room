// Object presentation is separate from the existing three puzzle clue records.
const objects={
  clock:{clue:'note',label:'CLOCK',crop:[53,259,180,247],detail:'A field note caught behind the clock.'},
  picture:{clue:'chart',label:'FRAMED PICTURE',crop:[270,313,238,329],detail:'A symbol reference on the back of the frame.'},
  books:{clue:'note',label:'BOOKS',crop:[28,772,217,101],detail:'A loose field note tucked between the books.'},
  drawer:{clue:'drawer',label:'DESK DRAWER',crop:[36,895,226,222],detail:'A maintenance card inside the drawer.'},
  chest:{clue:'drawer',label:'CHEST',crop:[757,1013,184,235],detail:'The same maintenance instructions inside the chest.'}
};
const defaults={note:'books',chart:'picture',drawer:'drawer'};
const magnifier='<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10" cy="10" r="5.5"/><path d="m14 14 6 6"/></svg>';
export class InspectionView {
  constructor({modal,close}){
    this.modal=modal;this.lastKey='';this.sources=new Map();
    this.figure=document.createElement('figure');this.figure.className='inspection-object';this.figure.hidden=true;
    modal.querySelector('#clue-content').prepend(this.figure);
    this.returnButton=document.createElement('button');this.returnButton.className='inspection-return';this.returnButton.textContent='RETURN TO ROOM';this.returnButton.hidden=true;
    this.returnButton.addEventListener('click',close);
    modal.querySelector('.panel').append(this.returnButton);
    modal.addEventListener('click',event=>{if(event.target===modal&&modal.classList.contains('clue-inspection'))close();});
    this.targets=[];
    for(const [key,object] of Object.entries(objects)){
      const target=document.querySelector(`.${key}-target`);if(!target)continue;
      target.dataset.inspection=key;
      const cue=document.createElement('span');cue.className='clue-cue';cue.setAttribute('aria-hidden','true');cue.innerHTML=magnifier;
      const label=document.createElement('span');label.className='clue-cue-label';label.textContent=key==='picture'?'PICTURE':key==='drawer'?'DRAWER':object.label;
      target.append(cue,label);this.targets.push(target);
    }
  }
  update({panel,source,seen}){
    const isClue=Object.hasOwn(defaults,panel);
    this.modal.classList.toggle('clue-inspection',isClue);
    this.figure.hidden=!isClue;this.returnButton.hidden=!isClue;
    for(const target of this.targets){target.dataset.inspected=String(seen.has(target.dataset.object));}
    if(!isClue){this.lastKey='';return;}
    const selected=source?.dataset.inspection;
    if(objects[selected]?.clue===panel)this.sources.set(panel,selected);
    const key=this.sources.get(panel)||defaults[panel];
    if(this.lastKey===key)return;
    this.lastKey=key;
    this.figure.dataset.object=key;
    const object=objects[key],[x,y,w,h]=object.crop;
    // SVG is only a viewport onto the unchanged reference image, not new artwork.
    this.figure.innerHTML=`<div class="inspection-image"><svg viewBox="${object.crop.join(' ')}" role="img" aria-label="Close-up of ${object.label.toLowerCase()}" preserveAspectRatio="xMidYMid meet"><defs><clipPath id="inspection-crop"><rect x="${x}" y="${y}" width="${w}" height="${h}"/></clipPath></defs><image href="assets/room-1-reference.png" width="941" height="1672" clip-path="url(#inspection-crop)"/></svg><span class="inspection-image-label">${object.label}</span></div><figcaption>${object.detail}</figcaption>`;
    this.modal.querySelector('.panel').scrollTop=0;
  }
}
