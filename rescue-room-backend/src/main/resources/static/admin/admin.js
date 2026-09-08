(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const state = {status:'ALL',search:'',page:0,size:25,totalPages:0};
  let timer, debounce, controller, generation=0, hasData=false;
  const dateFormat = new Intl.DateTimeFormat('en-GB',{timeZone:'UTC',day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'});
  function date(value) { return value ? dateFormat.format(new Date(value)) : '—'; }
  function cell(text, className='') { const td=document.createElement('td');td.textContent=text;td.className=className;return td; }
  function empty(title, description) {
    const row=document.createElement('tr'),td=cell('','empty');td.colSpan=7;
    const icon=document.createElement('span');icon.className='empty-icon';icon.textContent='◇';
    const heading=document.createElement('strong');heading.textContent=title;
    const sub=document.createElement('span');sub.textContent=description;
    td.append(icon,heading,sub);row.append(td);$('rows').replaceChildren(row);
  }
  function render(stats, page) {
    for (const [id,key] of Object.entries({total:'totalPlayers',playing:'playingNow',winners:'winners',losers:'losers',emails:'emailsSubmitted'})) $(id).textContent=stats[key].toLocaleString();
    $('threshold').textContent=`Seen within ${stats.inactivitySeconds} seconds`;
    if (!page.sessions.length) empty(stats.totalPlayers===0?'No sessions yet':'No matching sessions',stats.totalPlayers===0?'Sessions will appear when the session API is used.':'Try another filter or search term.');
    else {
      const rows=page.sessions.map(session=>{
        const row=document.createElement('tr');
        row.append(cell(session.sessionId,'session-id'),cell(session.email||'—'));
        const status=cell(''),badge=document.createElement('span');badge.className=`status ${session.status.toLowerCase()}`;badge.textContent=session.status;status.append(badge);
        if(session.status==='PLAYING'&&!session.activeNow){const note=document.createElement('small');note.className='inactive';note.textContent='Inactive';status.append(note);}
        row.append(status,cell(date(session.startedAt),'timestamp'),cell(date(session.finishedAt),'timestamp'),cell(session.remainingSeconds==null?'—':String(session.remainingSeconds)),cell(date(session.lastSeenAt),'timestamp'));return row;
      });$('rows').replaceChildren(...rows);
    }
    state.totalPages=page.totalPages;
    const start=page.totalElements===0?0:page.page*page.size+1;
    $('range').textContent=page.totalElements===0?'0 sessions':`${start}–${Math.min((page.page+1)*page.size,page.totalElements)} of ${page.totalElements} sessions`;
    $('page-label').textContent=`Page ${page.totalPages===0?1:page.page+1}${page.totalPages>0?` of ${page.totalPages}`:''}`;
    $('previous').disabled=state.page===0;$('next').disabled=state.page+1>=page.totalPages;
    $('refresh-state').textContent=`Updated ${new Date().toLocaleTimeString([], {hour:'2-digit',minute:'2-digit',second:'2-digit'})}`;
  }
  async function json(url,signal) { const response=await fetch(url,{signal,cache:'no-store',headers:{Accept:'application/json'}});if(!response.ok)throw new Error('Request failed');return response.json(); }
  async function refresh() {
    clearTimeout(timer);controller?.abort();const current=++generation;const activeController=new AbortController();controller=activeController;
    $('refresh').disabled=true;$('refresh-state').textContent=hasData?'Refreshing…':'Connecting…';
    const timeout=setTimeout(()=>activeController.abort(),8000);
    try {
      const params=new URLSearchParams({status:state.status,search:state.search,page:String(state.page),size:String(state.size)});
      const [stats,page]=await Promise.all([json('/api/admin/statistics',activeController.signal),json('/api/admin/sessions?'+params,activeController.signal)]);
      if(current!==generation)return;
      if(page.totalPages>0&&state.page>=page.totalPages){state.page=page.totalPages-1;clearTimeout(timeout);refresh();return;}
      render(stats,page);hasData=true;$('error').hidden=true;
    } catch(error) {
      if(current!==generation||document.hidden)return;
      $('error').hidden=false;$('error-message').textContent=hasData?'Could not refresh. Previously loaded data may be out of date.':'Unable to load dashboard data. Check that the backend is running, then retry.';
      $('refresh-state').textContent='Connection interrupted';
      if(!hasData)empty('Data unavailable','Use Try again to reconnect.');
    } finally {
      clearTimeout(timeout);
      if(current===generation){$('refresh').disabled=false;if(!document.hidden)timer=setTimeout(refresh,10000);}
    }
  }
  document.querySelectorAll('[data-filter]').forEach(button=>button.addEventListener('click',()=>{
    state.status=button.dataset.filter;state.page=0;
    document.querySelectorAll('[data-filter]').forEach(filter=>filter.setAttribute('aria-pressed',String(filter===button)));refresh();
  }));
  $('search').addEventListener('input',()=>{clearTimeout(debounce);debounce=setTimeout(()=>{state.search=$('search').value.trim();state.page=0;refresh();},250);});
  $('previous').addEventListener('click',()=>{if(state.page>0){state.page--;refresh();}});
  $('next').addEventListener('click',()=>{if(state.page+1<state.totalPages){state.page++;refresh();}});
  $('refresh').addEventListener('click',refresh);$('retry').addEventListener('click',refresh);
  document.addEventListener('visibilitychange',()=>{if(document.hidden){clearTimeout(timer);generation++;controller?.abort();}else refresh();});
  refresh();
})();
