import { useMemo, useState } from 'react';
import {
  Activity, ArrowUpRight, Bell, ChevronDown, CircleHelp, Clock3, Database,
  FileClock, LayoutDashboard, Moon, Plus, Settings2, ShieldCheck, Sparkles,
  Sun, Workflow
} from 'lucide-react';
import { Metric } from '../components/Metric';
import { ExecutionsTable } from '../features/executions/ExecutionsTable';
import type { ExecutionFilter, Run } from '../features/executions/types';
import { ExecutionDetails } from '../features/executions/ExecutionDetails';
import { NewExecutionModal, type NewExecutionInput } from '../features/executions/NewExecutionModal';
import { useApp } from '../app/AppProvider';

const seedRuns: Run[] = [
  { id:'EX-2048', pipeline:'Cash reconciliation', source:'cash_2026-11-22.csv', started:'10:42:18', duration:'02m 14s', status:'Succeeded', rows:'24,891' },
  { id:'EX-2047', pipeline:'Cash reconciliation', source:'cash_2026-11-22_b.csv', started:'10:38:05', duration:'01m 52s', status:'Running', rows:'18,340' },
  { id:'EX-2046', pipeline:'Cash reconciliation', source:'cash_2026-11-21.csv', started:'09:56:32', duration:'03m 08s', status:'Failed', rows:'—' },
  { id:'EX-2045', pipeline:'Cash reconciliation', source:'cash_2026-11-21_a.csv', started:'09:24:11', duration:'02m 03s', status:'Succeeded', rows:'22,016' },
  { id:'EX-2044', pipeline:'Cash reconciliation', source:'cash_2026-11-20.csv', started:'17:12:49', duration:'01m 44s', status:'Succeeded', rows:'19,602' },
  { id:'EX-2043', pipeline:'Cash reconciliation', source:'cash_2026-11-20_b.csv', started:'16:02:20', duration:'02m 31s', status:'Succeeded', rows:'26,104' },
];

export default function Dashboard() {
  const { t, language, setLanguage, theme, setTheme } = useApp();
  const [runs, setRuns] = useState(seedRuns);
  const [filter, setFilter] = useState<ExecutionFilter>('All runs');
  const [query, setQuery] = useState('');
  const [modal, setModal] = useState(false);
  const [selected, setSelected] = useState<Run | null>(seedRuns[0]);
  const [notice, setNotice] = useState('');

  const filtered = useMemo(() => runs.filter(r =>
    (filter === 'All runs' || r.status === filter) &&
    [r.id, r.source, r.pipeline].join(' ').toLowerCase().includes(query.toLowerCase())
  ), [runs, filter, query]);

  const launch = ({ source }: NewExecutionInput) => {
    const run: Run = {
      id: 'EX-' + (2050 + runs.length), pipeline: 'Cash reconciliation',
      source: source.split('/').pop() || source, started: t('justNow'),
      duration: '—', status: 'Running', rows: '—'
    };
    setRuns([run, ...runs]);
    setSelected(run);
    setModal(false);
    setNotice(t('demoCreated'));
  };

  const toggleTheme = () => setTheme(theme === 'dark' ? 'light' : 'dark');

  return <div className="shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Workflow size={19}/></div><span>flowforge</span><small>ETL</small></div>
      <div className="workspace"><div className="workspace-icon">F</div><div><strong>Data Operations</strong><span>{t('workspace')}</span></div><ChevronDown size={14}/></div>
      <div className="nav-label">{t('workspaceNav')}</div>
      <nav>
        <a className="active"><LayoutDashboard size={17}/>{t('overview')}</a>
        <a><Activity size={17}/>{t('pipelineRuns')}<span className="nav-count">{runs.length}</span></a>
        <a><Workflow size={17}/>{t('pipelines')}</a>
        <a><Database size={17}/>{t('dataSources')}</a>
      </nav>
      <div className="nav-label second">{t('manage')}</div>
      <nav><a><FileClock size={17}/>{t('schedules')}</a><a><Settings2 size={17}/>{t('settings')}</a></nav>
      <div className="sidebar-bottom">
        <div className="connection"><span className="online-dot"/><div><b>{t('localDemo')}</b><small>{t('apiNotConnected')}</small></div><ShieldCheck size={15}/></div>
        <div className="profile"><div className="avatar">AM</div><div><b>Alex Miranda</b><small>{t('backendDeveloper')}</small></div><ChevronDown size={15}/></div>
      </div>
    </aside>
    <main className="main">
      <header className="topbar">
        <div className="crumb">{t('workspace')} <span>/</span> <b>{t('overview')}</b></div>
        <div className="top-actions">
          <label className="language-control"><span className="sr-only">{t('language')}</span>
            <select value={language} onChange={e => setLanguage(e.target.value as 'en' | 'es' | 'fr')} aria-label={t('language')}>
              <option value="en">EN</option><option value="es">ES</option><option value="fr">FR</option>
            </select>
          </label>
          <button className="icon-btn theme-toggle" onClick={toggleTheme} aria-label={theme === 'dark' ? t('themeDark') : t('themeLight')} title={theme === 'dark' ? t('themeDark') : t('themeLight')}>
            {theme === 'dark' ? <Sun size={17}/> : <Moon size={17}/>}
          </button>
          <button className="icon-btn" aria-label="Help"><CircleHelp size={17}/></button>
          <button className="icon-btn notification" aria-label="Notifications"><Bell size={17}/><i/></button>
          <div className="avatar small">AM</div>
        </div>
      </header>
      <div className="page">
        <div className="welcome">
          <div><div className="eyebrow"><Sparkles size={13}/> {t('operationsOverview')}</div><h1>{t('greeting')}, Alex<span>.</span></h1><p>{t('dashboardIntro')}</p></div>
          <button className="primary" onClick={()=>{setModal(true);setNotice('')}}><Plus size={16}/> {t('newPipelineRun')}</button>
        </div>
        <div className="metric-grid">
          <Metric label={t('totalExecutions')} value="1,284" change="+12.8%" up icon={<Activity size={17}/>} caption={t('previous7Days')}/>
          <Metric label={t('successRate')} value="98.4%" change="+2.4%" up icon={<ShieldCheck size={17}/>} caption={t('previous7Days')}/>
          <Metric label={t('avgDuration')} value="2m 18s" change="-8.2%" up icon={<Clock3 size={17}/>} caption={t('previous7Days')}/>
          <Metric label={t('recordsProcessed')} value="2.48M" change="+18.6%" up icon={<Database size={17}/>} caption={t('previous7Days')}/>
        </div>
        <div className="content-grid">
          <section className="panel chart-panel">
            <div className="panel-head"><div><h2>{t('executionActivity')}</h2><p>{t('runsLast7Days')}</p></div><button className="select">{t('last7Days')} <ChevronDown size={14}/></button></div>
            <div className="chart"><div className="chart-y"><span>40</span><span>30</span><span>20</span><span>10</span><span>0</span></div><div className="chart-body"><div className="grid-lines"><i/><i/><i/><i/><i/></div><div className="bars">{[28,36,22,44,32,48,39].map((h,i)=><div className="bar-pair" key={i}><div className="bar success-bar" style={{height:(h*1.5)+'%'}}/><div className="bar fail-bar" style={{height:(i===2?10:4)+'%'}}/></div>)}</div><div className="days"><span>Mon</span><span>Tue</span><span>Wed</span><span>Thu</span><span>Fri</span><span>Sat</span><span>Sun</span></div></div></div>
            <div className="chart-legend"><span><i className="legend-success"/>{t('successful')}</span><span><i className="legend-fail"/>{t('failed')}</span></div>
          </section>
          <section className="panel pipeline-panel">
            <div className="panel-head"><div><h2>{t('pipelineHealth')}</h2><p>{t('currentPipelineStatus')}</p></div><button className="icon-btn" aria-label="Open pipeline health"><ArrowUpRight size={16}/></button></div>
            <div className="health-card"><div className="health-ring"><strong>98.4<small>%</small></strong></div><div><b>{t('allSystemsOperational')}</b><span>{t('activePipelines')}</span></div></div>
            <div className="pipeline-row"><span className="pipeline-icon"><Workflow size={16}/></span><div><b>{t('cashReconciliation')}</b><small>{t('dailyLast12')}</small></div><span className="health-pill">{t('healthy')}</span></div>
            <div className="pipeline-row"><span className="pipeline-icon blue"><Database size={16}/></span><div><b>{t('referenceSync')}</b><small>{t('hourlyLast38')}</small></div><span className="health-pill">{t('healthy')}</span></div>
            <div className="pipeline-row"><span className="pipeline-icon violet"><Activity size={16}/></span><div><b>{t('archiveProcessor')}</b><small>{t('weeklyLast2')}</small></div><span className="health-pill">{t('healthy')}</span></div>
          </section>
        </div>
        <ExecutionsTable runs={filtered} filter={filter} query={query} selectedRunId={selected?.id ?? null} onFilterChange={setFilter} onQueryChange={setQuery} onSelectRun={setSelected}/>
        <footer>FlowForge ETL <span>•</span> {t('dashboardPrototype')} <span>•</span> {t('illustrativeData')}</footer>
      </div>
    </main>
    {selected && <ExecutionDetails run={selected} onClose={() => setSelected(null)}/>}
    <NewExecutionModal open={modal} notice={notice} onClose={() => setModal(false)} onCreate={launch}/>
  </div>;
}
