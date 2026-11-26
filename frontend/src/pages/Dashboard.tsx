import { useEffect, useMemo, useState } from 'react';
import {
  Activity, Bell, ChevronDown, CircleHelp, Database,
  FileClock, LayoutDashboard, Moon, Plus, Settings2, ShieldCheck, Sparkles,
  Sun, Workflow
} from 'lucide-react';
import { ExecutionsTable } from '../features/executions/ExecutionsTable';
import type { ExecutionFilter, Run } from '../features/executions/types';
import { ExecutionDetails } from '../features/executions/ExecutionDetails';
import { NewExecutionModal, type NewExecutionInput } from '../features/executions/NewExecutionModal';
import { useApp } from '../app/AppProvider';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080';


export default function Dashboard() {
  const { t, language, setLanguage, theme, setTheme } = useApp();
  const [runs, setRuns] = useState<Run[]>([]);
  const [apiState, setApiState] = useState<'loading' | 'connected' | 'error'>('loading');
  const [filter, setFilter] = useState<ExecutionFilter>('All runs');
  const [query, setQuery] = useState('');
  const [modal, setModal] = useState(false);
  const [selected, setSelected] = useState<Run | null>(null);
  const [notice, setNotice] = useState('');

  const filtered = useMemo(() => runs.filter(r =>
    (filter === 'All runs' || r.status === filter) &&
    [r.id, r.source, r.pipeline].join(' ').toLowerCase().includes(query.toLowerCase())
  ), [runs, filter, query]);

  const refreshExecutions = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/executions?limit=50`);
      if (!response.ok) throw new Error(`API responded ${response.status}`);
      const payload = await response.json() as Array<{ executionId: string; pipelineId: string; status: string; requestedAt?: string | null; startedAt?: string | null; errorMessage?: string | null }>;
      const actualRuns: Run[] = payload.map(item => ({
        id: item.executionId,
        pipeline: item.pipelineId || 'Cash',
        source: 'Cash CSV',
        started: new Date(item.startedAt || item.requestedAt || Date.now()).toLocaleString(),
        duration: '—',
        status: ['SUCCEEDED', 'SUCCESS', 'COMPLETED'].includes(item.status.toUpperCase()) ? 'Succeeded' : item.status.toUpperCase() === 'FAILED' ? 'Failed' : 'Running',
        rows: '—',
        errorMessage: item.errorMessage
      }));
      setRuns(actualRuns);
      setSelected(current => current ? actualRuns.find(run => run.id === current.id) ?? null : null);
      setApiState('connected');
    } catch {
      setApiState('error');
    }
  };

  useEffect(() => {
    void refreshExecutions();
    const timer = window.setInterval(() => void refreshExecutions(), 5000);
    return () => window.clearInterval(timer);
  }, []);

  const launch = async ({ file }: NewExecutionInput) => {
    const response = await fetch(`${API_BASE}/api/uploads`, {
      method: 'POST',
      headers: { 'Content-Type': 'text/csv', 'X-File-Name': encodeURIComponent(file.name) },
      body: file
    });
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.error || 'No se pudo subir el CSV.');
    setModal(false);
    setNotice(`CSV recibido. Ejecución ${payload.executionId} encolada.`);
    await refreshExecutions();
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
        <div className="connection"><span className="online-dot"/><div><b>FlowForge API</b><small>{apiState === 'connected' ? 'Conectada' : apiState === 'loading' ? 'Conectando…' : 'Sin conexión'}</small></div><ShieldCheck size={15}/></div>
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
        <div className="panel" style={{padding:'14px 18px',marginBottom:14}}>
          <div className="panel-head"><div><h2>Conexión con API</h2><p>{apiState === 'connected' ? 'Ejecuciones reales consultadas desde el backend.' : apiState === 'loading' ? 'Conectando con FlowForge API…' : 'No se pudo conectar. Comprueba que la API Java esté arrancada.'}</p></div><span className="health-pill">{apiState === 'connected' ? 'Conectada' : apiState === 'loading' ? 'Conectando' : 'Sin conexión'}</span></div>
        </div>
        <ExecutionsTable runs={filtered} filter={filter} query={query} selectedRunId={selected?.id ?? null} onFilterChange={setFilter} onQueryChange={setQuery} onSelectRun={setSelected}/>
        <footer>FlowForge ETL <span>•</span> Datos de ejecuciones obtenidos de la API</footer>
      </div>
    </main>
    {selected && <ExecutionDetails run={selected} onClose={() => setSelected(null)}/>}
    <NewExecutionModal open={modal} notice={notice} onClose={() => setModal(false)} onCreate={launch}/>
  </div>;
}
