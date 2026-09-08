// SpeechSynthesisVoice has no standard gender field. Accept identifiable male
// system voices only; fall back to available English/default voices if preferred male voices are absent.
const MALE_NAME=/\b(male|David|Mark|Daniel|Alex|Fred|George|James|Guy|Ryan|Christopher|Eric|Roger|Steffan|Andrew|Brian|Thomas|Gordon|Arthur|Oliver|Aaron|Albert|Bruce|Rishi|Ravi)\b/i;
export function selectMaleVoice(voices){
  if(!voices||!voices.length)return null;
  const english=voices.filter(v=>/^en(?:[-_]|$)/i.test(v.lang)&&!/\bfemale\b/i.test(v.name));

  // 1. Preferred male-name matches scored by US/GB preference and localService
  const preferred=english.filter(v=>MALE_NAME.test(v.name))
    .map((voice,index)=>({voice,index,score:(/^en[-_](US|GB)$/i.test(voice.lang)?100:0)+(voice.localService?10:0)}))
    .sort((a,b)=>b.score-a.score||a.index-b.index)[0]?.voice;
  if(preferred)return preferred;

  // 2. Any en-US voice (preferring non-default)
  const enUs=[...english].sort((a,b)=>(a.default?1:0)-(b.default?1:0)).find(v=>/^en[-_]US/i.test(v.lang));
  if(enUs)return enUs;

  // 3. Any en-GB voice
  const enGb=english.find(v=>/^en[-_]GB/i.test(v.lang));
  if(enGb)return enGb;

  // 4. Any other English voice (lang starts with en)
  const anyEn=english[0];
  if(anyEn)return anyEn;

  // 5. Browser default voice as final fallback if SpeechSynthesis is available
  return voices[0]||null;
}
