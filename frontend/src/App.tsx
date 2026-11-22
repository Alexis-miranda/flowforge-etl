import { useMemo, useState } from 'react';
import {
  Activity, ArrowUpRight, Bell, ChevronDown, CircleHelp,
  Clock3, Database, FileClock, Filter, LayoutDashboard, Menu, Play,
  Plus, Search, Settings2, ShieldCheck, Sparkles, Workflow, X
} from 'lucide-react';
import { Metric } from './components/Metric';
import { Status } from './components/Status';

type Run = { id: string; pipeline: string; source: string; started: string; duration: string; status: 'Succeeded'|'Running'|'Failed'; rows: string; };
const seedRuns: Run[] = [
  { id:'EX-2048', pipeline:'Cash reconciliation', source:'cash_2026-11-22.csv', started:'Today, 10:42:18', duration:'02m 14s', status:'Succeeded', rows:'24,891' },
  { id:'EX-2047', pipeline:'Cash reconciliation', source:'cash_2026-11-22_b.csv', started:'Today, 10:38:05', duration:'01m 52s', status:'Running', rows:'18,340' },
  { id:'EX-2046', pipeline:'Cash reconciliation', source:'cash_2026-11-21.csv', started:'Today, 09:56:32', duration:'03m 08s', status:'Failed', rows:'—' },
  { id:'EX-2045', pipeline:'Cash reconciliation', source:'cash_2026-11-21_a.csv', started:'Today, 09:24:11', duration:'02m 03s', status:'Succeeded', rows:'22,016' },
  { id:'EX-2044', pipeline:'Cash reconciliation', source:'cash_2026-11-20.csv', started:'Yesterday, 17:12:49', duration:'01m 44s', status:'Succeeded', rows:'19,602' },
  { id:'EX-2043', pipeline:'Cash reconciliation', source:'cash_2026-11-20_b.csv', started:'Yesterday, 16:02:20', duration:'02m 31s', status:'Succeeded', rows:'26,104' },
];
const steps=[{name:'Extract source file',detail:'Read input and detect format',time:'00:12',done:true},{name:'Validate records',detail:'Schema, required fields and amount rules',time:'00:18',done:true},{name:'Prepare cash data',detail:'Normalize values and map fields',time:'00:36',done:true},{name:'Persist to database',detail:'SQL Server transactional write',time:'00:42',done:true},{name:'Publish output',detail:'Write output artifact',time:'00:16',done:true}];
export default function App(){
  const [runs,setRuns]=useState(seedRuns); const [filter,setFilter]=useState('All runs'); const [query,setQuery]=useState(''); const [modal,setModal]=useState(false); const [selected,setSelected]=useState<Run | null>(seedRuns[0]);
  const [source,setSource]=useState(''); const [backOffice,setBackOffice]=useState('BNYM'); const [output,setOutput]=useState('/data/output'); const [notice,setNotice]=useState('');
  const filtered=useMemo(()=>runs.filter(r=>(filter==='All runs'||r.status===filter)&&[r.id,r.source,r.pipeline].join(' ').toLowerCase().includes(query.toLowerCase())),[runs,filter,query]);
  const launch=()=>{if(!source.trim()){setNotice('Choose an input file path to continue.');return;}const run:Run={id:'EX-'+(2050+runs.length),pipeline:'Cash reconciliation',source:source.split('/').pop()||source,started:'Just now',duration:'—',status:'Running',rows:'—'};setRuns([run,...runs]);setSelected(run);setModal(false);setNotice('Demo execution created locally. API integration is the next step.');setSource('');};
  return <div className="shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Workflow size={19}/></div><span>flowforge</span><small>ETL</small></div>
      <div className="workspace"><div className="workspace-icon">F</div><div><strong>Data Operations</strong><span>Workspace</span></div><ChevronDown size={14}/></div>
      <div className="nav-label">WORKSPACE</div>
      <nav><a className="active"><LayoutDashboard size={17}/>Overview</a><a><Activity size={17}/>Pipeline runs<span className="nav-count">{runs.length}</span></a><a><Workflow size={17}/>Pipelines</a><a><Database size={17}/>Data sources</a></nav>
      <div className="nav-label second">MANAGE</div>
      <nav><a><FileClock size={17}/>Schedules</a><a><Settings2 size={17}/>Settings</a></nav>
      <div className="sidebar-bottom"><div className="connection"><span className="online-dot"/><div><b>Local demo mode</b><small>API not connected</small></div><ShieldCheck size={15}/></div><div className="profile"><div className="avatar">AM</div><div><b>Alex Miranda</b><small>Backend developer</small></div><ChevronDown size={15}/></div></div>
    </aside>
    <main className="main">
      <header className="topbar"><div className="crumb">Workspace <span>/</span> <b>Overview</b></div><div className="top-actions"><button className="icon-btn"><CircleHelp size={17}/></button><button className="icon-btn notification"><Bell size={17}/><i/></button><div className="avatar small">AM</div></div></header>
      <div className="page">
        <div className="welcome"><div><div className="eyebrow"><Sparkles size={13}/> OPERATIONS OVERVIEW</div><h1>Good morning, Alex<span>.</span></h1><p>Here's what's happening across your data pipelines.</p></div><button className="primary" onClick={()=>{setModal(true);setNotice('')}}><Plus size={16}/> New pipeline run</button></div>
        <div className="metric-grid">
          <Metric label="Total executions" value="1,284" change="+12.8%" up icon={<Activity size={17}/>} caption="vs. previous 7 days"/>
          <Metric label="Success rate" value="98.4%" change="+2.4%" up icon={<ShieldCheck size={17}/>} caption="vs. previous 7 days"/>
          <Metric label="Avg. duration" value="2m 18s" change="-8.2%" up icon={<Clock3 size={17}/>} caption="vs. previous 7 days"/>
          <Metric label="Records processed" value="2.48M" change="+18.6%" up icon={<Database size={17}/>} caption="vs. previous 7 days"/>
        </div>
        <div className="content-grid">
          <section className="panel chart-panel"><div className="panel-head"><div><h2>Execution activity</h2><p>Pipeline runs over the last 7 days</p></div><button className="select">Last 7 days <ChevronDown size={14}/></button></div><div className="chart"><div className="chart-y"><span>40</span><span>30</span><span>20</span><span>10</span><span>0</span></div><div className="chart-body"><div className="grid-lines"><i/><i/><i/><i/><i/></div><div className="bars">{[28,36,22,44,32,48,39].map((h,i)=><div className="bar-pair" key={i}><div className="bar success-bar" style={{height:(h*1.5)+'%'}}/><div className="bar fail-bar" style={{height:(i===2?10:4)+'%'}}/></div>)}</div><div className="days"><span>Mon</span><span>Tue</span><span>Wed</span><span>Thu</span><span>Fri</span><span>Sat</span><span>Sun</span></div></div></div><div className="chart-legend"><span><i className="legend-success"/>Successful</span><span><i className="legend-fail"/>Failed</span></div></section>
          <section className="panel pipeline-panel"><div className="panel-head"><div><h2>Pipeline health</h2><p>Current pipeline status</p></div><button className="icon-btn"><ArrowUpRight size={16}/></button></div><div className="health-card"><div className="health-ring"><strong>98.4<small>%</small></strong></div><div><b>All systems operational</b><span>Across 3 active pipelines</span></div></div><div className="pipeline-row"><span className="pipeline-icon"><Workflow size={16}/></span><div><b>Cash reconciliation</b><small>Daily · Last run 12 min ago</small></div><span className="health-pill">Healthy</span></div><div className="pipeline-row"><span className="pipeline-icon blue"><Database size={16}/></span><div><b>Reference data sync</b><small>Hourly · Last run 38 min ago</small></div><span className="health-pill">Healthy</span></div><div className="pipeline-row"><span className="pipeline-icon violet"><Activity size={16}/></span><div><b>Archive processor</b><small>Weekly · Last run 2 days ago</small></div><span className="health-pill">Healthy</span></div></section>
        </div>
        <section className="panel runs-panel"><div className="panel-head"><div><h2>Recent executions</h2><p>Monitor your latest pipeline activity</p></div><button className="text-button">View all runs <ArrowUpRight size={15}/></button></div><div className="run-toolbar"><div className="tabs">{['All runs','Succeeded','Running','Failed'].map(t=><button key={t} className={filter===t?'chosen':''} onClick={()=>setFilter(t)}>{t}</button>)}</div><div className="search"><Search size={15}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search executions..."/></div><button className="filter-btn"><Filter size={15}/> Filters</button></div><div className="table-wrap"><table><thead><tr><th>EXECUTION</th><th>PIPELINE</th><th>INPUT SOURCE</th><th>STARTED</th><th>DURATION</th><th>RECORDS</th><th>STATUS</th></tr></thead><tbody>{filtered.map(r=><tr key={r.id} onClick={()=>setSelected(r)} className={selected?.id===r.id?'selected-row':''}><td><b className="run-id">{r.id}</b></td><td>{r.pipeline}</td><td><span className="file-cell"><FileClock size={14}/>{r.source}</span></td><td>{r.started}</td><td>{r.duration}</td><td>{r.rows}</td><td><Status status={r.status}/></td></tr>)}</tbody></table>{filtered.length===0&&<div className="empty">No executions match this filter.</div>}</div></section>
        <footer>FlowForge ETL <span>•</span> Dashboard prototype <span>•</span> Data shown is illustrative</footer>
      </div>
    </main>
    {selected&&<aside className="detail-drawer"><button className="close-detail" onClick={()=>setSelected(null)}><X size={16}/></button><div className="eyebrow">EXECUTION DETAIL</div><h2>{selected.id}</h2><Status status={selected.status}/><div className="detail-source"><small>INPUT FILE</small><b>{selected.source}</b></div><div className="detail-title">Pipeline steps</div>{steps.map((s,i)=><div className="detail-step" key={s.name}><div className={'step-check '+(selected.status==='Failed'&&i===2?'fail':'')}>{selected.status==='Failed'&&i===2?'!':'✓'}</div><div><b>{s.name}</b><small>{s.detail}</small></div><span>{selected.status==='Running'&&i===4?'…':s.time}</span></div>)}<div className="detail-info"><ShieldCheck size={15}/> Execution details are sample data until connected to the REST API.</div></aside>}
    {modal&&<div className="modal-backdrop" onClick={()=>setModal(false)}><div className="modal" onClick={e=>e.stopPropagation()}><div className="modal-head"><div><h2>New pipeline run</h2><p>Configure and start a Cash reconciliation run.</p></div><button className="icon-btn" onClick={()=>setModal(false)}><X size={17}/></button></div><label>Pipeline</label><div className="pipeline-select"><Workflow size={16}/> Cash reconciliation <span>Active</span></div><label>Input file path</label><input className="form-input" value={source} onChange={e=>setSource(e.target.value)} placeholder="/shared/input/cash-file.csv"/><label>Back office code</label><input className="form-input" value={backOffice} onChange={e=>setBackOffice(e.target.value)} placeholder="BNYM"/><label>Output directory</label><input className="form-input" value={output} onChange={e=>setOutput(e.target.value)}/><p className="modal-note">Demo only — this form creates a local sample execution. It does not submit to the backend yet.</p>{notice&&<div className="notice">{notice}</div>}<div className="modal-actions"><button className="secondary" onClick={()=>setModal(false)}>Cancel</button><button className="primary" onClick={launch}><Play size={15}/> Start demo run</button></div></div></div>}
  </div>
}
