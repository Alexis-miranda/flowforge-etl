import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type Language = 'en' | 'es' | 'fr';
export type Theme = 'dark' | 'light';

const messages = {
  en: {
    workspace: 'Workspace', overview: 'Overview', workspaceNav: 'WORKSPACE', manage: 'MANAGE',
    pipelineRuns: 'Pipeline runs', pipelines: 'Pipelines', dataSources: 'Data sources', schedules: 'Schedules', settings: 'Settings',
    localDemo: 'Local demo mode', apiNotConnected: 'API not connected', backendDeveloper: 'Backend developer',
    operationsOverview: 'OPERATIONS OVERVIEW', greeting: 'Good morning', dashboardIntro: "Here's what's happening across your data pipelines.",
    newPipelineRun: 'New pipeline run', totalExecutions: 'Total executions', successRate: 'Success rate', avgDuration: 'Avg. duration',
    recordsProcessed: 'Records processed', previous7Days: 'vs. previous 7 days', executionActivity: 'Execution activity',
    runsLast7Days: 'Pipeline runs over the last 7 days', last7Days: 'Last 7 days', successful: 'Successful', failed: 'Failed',
    pipelineHealth: 'Pipeline health', currentPipelineStatus: 'Current pipeline status', allSystemsOperational: 'All systems operational',
    activePipelines: 'Across 3 active pipelines', healthy: 'Healthy', recentExecutions: 'Recent executions',
    monitorActivity: 'Monitor your latest pipeline activity', viewAllRuns: 'View all runs', allRuns: 'All runs',
    succeeded: 'Succeeded', running: 'Running', statusFailed: 'Failed', searchExecutions: 'Search executions...',
    filters: 'Filters', execution: 'EXECUTION', pipeline: 'PIPELINE', inputSource: 'INPUT SOURCE', started: 'STARTED',
    duration: 'DURATION', records: 'RECORDS', status: 'STATUS', noExecutions: 'No executions match this filter.',
    dashboardPrototype: 'Dashboard prototype', illustrativeData: 'Data shown is illustrative', executionDetail: 'EXECUTION DETAIL',
    inputFile: 'INPUT FILE', pipelineSteps: 'Pipeline steps', extract: 'Extract source file', extractDetail: 'Read input and detect format',
    validate: 'Validate records', validateDetail: 'Schema, required fields and amount rules', prepare: 'Prepare cash data',
    prepareDetail: 'Normalize values and map fields', persist: 'Persist to database', persistDetail: 'SQL Server transactional write',
    publish: 'Publish output', publishDetail: 'Write output artifact', sampleDetails: 'Execution details are sample data until connected to the REST API.',
    closeDetails: 'Close execution details', close: 'Close', newRunTitle: 'New pipeline run', configureRun: 'Configure and start a Cash reconciliation run.',
    active: 'Active', inputPath: 'Input file path', backOffice: 'Back office code', outputDirectory: 'Output directory',
    demoOnly: 'Demo only — this form creates a local sample execution. It does not submit to the backend yet.',
    chooseInput: 'Choose an input file path to continue.', cancel: 'Cancel', startDemo: 'Start demo run',
    demoCreated: 'Demo execution created locally. API integration is the next step.', themeDark: 'Switch to light theme',
    themeLight: 'Switch to dark theme', language: 'Language', english: 'English', spanish: 'Español', french: 'Français',
    cashReconciliation: 'Cash reconciliation', referenceSync: 'Reference data sync', archiveProcessor: 'Archive processor',
    dailyLast12: 'Daily · Last run 12 min ago', hourlyLast38: 'Hourly · Last run 38 min ago', weeklyLast2: 'Weekly · Last run 2 days ago',
    today: 'Today', yesterday: 'Yesterday', justNow: 'Just now',
  },
  es: {
    workspace: 'Espacio de trabajo', overview: 'Resumen', workspaceNav: 'ESPACIO DE TRABAJO', manage: 'GESTIÓN',
    pipelineRuns: 'Ejecuciones', pipelines: 'Pipelines', dataSources: 'Fuentes de datos', schedules: 'Programaciones', settings: 'Ajustes',
    localDemo: 'Modo de demostración local', apiNotConnected: 'API no conectada', backendDeveloper: 'Desarrollador backend',
    operationsOverview: 'RESUMEN DE OPERACIONES', greeting: 'Buenos días', dashboardIntro: 'Esto es lo que ocurre en tus pipelines de datos.',
    newPipelineRun: 'Nueva ejecución', totalExecutions: 'Ejecuciones totales', successRate: 'Tasa de éxito', avgDuration: 'Duración media',
    recordsProcessed: 'Registros procesados', previous7Days: 'vs. los 7 días anteriores', executionActivity: 'Actividad de ejecuciones',
    runsLast7Days: 'Ejecuciones de pipelines en los últimos 7 días', last7Days: 'Últimos 7 días', successful: 'Correctas', failed: 'Fallidas',
    pipelineHealth: 'Estado de los pipelines', currentPipelineStatus: 'Estado actual de los pipelines', allSystemsOperational: 'Todos los sistemas operativos',
    activePipelines: 'En 3 pipelines activos', healthy: 'Correcto', recentExecutions: 'Ejecuciones recientes',
    monitorActivity: 'Supervisa la actividad reciente de tus pipelines', viewAllRuns: 'Ver todas', allRuns: 'Todas',
    succeeded: 'Correcta', running: 'En curso', statusFailed: 'Fallida', searchExecutions: 'Buscar ejecuciones...',
    filters: 'Filtros', execution: 'EJECUCIÓN', pipeline: 'PIPELINE', inputSource: 'ORIGEN', started: 'INICIO',
    duration: 'DURACIÓN', records: 'REGISTROS', status: 'ESTADO', noExecutions: 'No hay ejecuciones que coincidan con este filtro.',
    dashboardPrototype: 'Prototipo del dashboard', illustrativeData: 'Los datos mostrados son ilustrativos', executionDetail: 'DETALLE DE EJECUCIÓN',
    inputFile: 'ARCHIVO DE ENTRADA', pipelineSteps: 'Pasos del pipeline', extract: 'Extraer archivo de origen', extractDetail: 'Leer la entrada y detectar el formato',
    validate: 'Validar registros', validateDetail: 'Esquema, campos obligatorios y reglas de importes', prepare: 'Preparar datos de caja',
    prepareDetail: 'Normalizar valores y asignar campos', persist: 'Guardar en la base de datos', persistDetail: 'Escritura transaccional en SQL Server',
    publish: 'Publicar resultado', publishDetail: 'Escribir el archivo de salida', sampleDetails: 'Los detalles son datos de ejemplo hasta conectar la API REST.',
    closeDetails: 'Cerrar detalles de ejecución', close: 'Cerrar', newRunTitle: 'Nueva ejecución', configureRun: 'Configura e inicia una conciliación de caja.',
    active: 'Activo', inputPath: 'Ruta del archivo de entrada', backOffice: 'Código de back office', outputDirectory: 'Directorio de salida',
    demoOnly: 'Solo demostración: este formulario crea una ejecución local de ejemplo. Todavía no envía nada al backend.',
    chooseInput: 'Indica la ruta de un archivo de entrada para continuar.', cancel: 'Cancelar', startDemo: 'Iniciar demostración',
    demoCreated: 'Ejecución de demostración creada localmente. La integración con la API será el siguiente paso.', themeDark: 'Cambiar a tema claro',
    themeLight: 'Cambiar a tema oscuro', language: 'Idioma', english: 'English', spanish: 'Español', french: 'Français',
    cashReconciliation: 'Conciliación de caja', referenceSync: 'Sincronización de datos de referencia', archiveProcessor: 'Procesador de archivos',
    dailyLast12: 'Diario · Última ejecución hace 12 min', hourlyLast38: 'Cada hora · Última ejecución hace 38 min', weeklyLast2: 'Semanal · Última ejecución hace 2 días',
    today: 'Hoy', yesterday: 'Ayer', justNow: 'Ahora mismo',
  },
  fr: {
    workspace: 'Espace de travail', overview: 'Vue d’ensemble', workspaceNav: 'ESPACE DE TRAVAIL', manage: 'GESTION',
    pipelineRuns: 'Exécutions', pipelines: 'Pipelines', dataSources: 'Sources de données', schedules: 'Planifications', settings: 'Paramètres',
    localDemo: 'Mode démo local', apiNotConnected: 'API non connectée', backendDeveloper: 'Développeur backend',
    operationsOverview: 'VUE D’ENSEMBLE DES OPÉRATIONS', greeting: 'Bonjour', dashboardIntro: 'Voici l’activité de vos pipelines de données.',
    newPipelineRun: 'Nouvelle exécution', totalExecutions: 'Exécutions totales', successRate: 'Taux de réussite', avgDuration: 'Durée moyenne',
    recordsProcessed: 'Enregistrements traités', previous7Days: 'par rapport aux 7 jours précédents', executionActivity: 'Activité des exécutions',
    runsLast7Days: 'Exécutions des pipelines sur les 7 derniers jours', last7Days: '7 derniers jours', successful: 'Réussies', failed: 'Échouées',
    pipelineHealth: 'État des pipelines', currentPipelineStatus: 'État actuel des pipelines', allSystemsOperational: 'Tous les systèmes fonctionnent',
    activePipelines: 'Sur 3 pipelines actifs', healthy: 'Opérationnel', recentExecutions: 'Exécutions récentes',
    monitorActivity: 'Suivez l’activité récente de vos pipelines', viewAllRuns: 'Voir toutes les exécutions', allRuns: 'Toutes',
    succeeded: 'Réussie', running: 'En cours', statusFailed: 'Échouée', searchExecutions: 'Rechercher des exécutions...',
    filters: 'Filtres', execution: 'EXÉCUTION', pipeline: 'PIPELINE', inputSource: 'SOURCE', started: 'DÉMARRÉE',
    duration: 'DURÉE', records: 'ENREGISTREMENTS', status: 'ÉTAT', noExecutions: 'Aucune exécution ne correspond à ce filtre.',
    dashboardPrototype: 'Prototype du tableau de bord', illustrativeData: 'Les données affichées sont illustratives', executionDetail: 'DÉTAIL DE L’EXÉCUTION',
    inputFile: 'FICHIER SOURCE', pipelineSteps: 'Étapes du pipeline', extract: 'Extraire le fichier source', extractDetail: 'Lire l’entrée et détecter le format',
    validate: 'Valider les enregistrements', validateDetail: 'Schéma, champs requis et règles de montant', prepare: 'Préparer les données de caisse',
    prepareDetail: 'Normaliser les valeurs et mapper les champs', persist: 'Enregistrer dans la base de données', persistDetail: 'Écriture transactionnelle SQL Server',
    publish: 'Publier le résultat', publishDetail: 'Écrire le fichier de sortie', sampleDetails: 'Les détails sont des exemples jusqu’à la connexion à l’API REST.',
    closeDetails: 'Fermer les détails de l’exécution', close: 'Fermer', newRunTitle: 'Nouvelle exécution', configureRun: 'Configurez et lancez une réconciliation de caisse.',
    active: 'Actif', inputPath: 'Chemin du fichier source', backOffice: 'Code back-office', outputDirectory: 'Dossier de sortie',
    demoOnly: 'Démo uniquement : ce formulaire crée une exécution locale fictive. Il ne contacte pas encore le backend.',
    chooseInput: 'Veuillez saisir le chemin d’un fichier source.', cancel: 'Annuler', startDemo: 'Lancer la démo',
    demoCreated: 'Exécution de démonstration créée localement. La connexion à l’API sera la prochaine étape.', themeDark: 'Passer au thème clair',
    themeLight: 'Passer au thème sombre', language: 'Langue', english: 'English', spanish: 'Español', french: 'Français',
    cashReconciliation: 'Rapprochement de caisse', referenceSync: 'Synchronisation des données de référence', archiveProcessor: 'Processeur d’archives',
    dailyLast12: 'Quotidien · Dernière exécution il y a 12 min', hourlyLast38: 'Toutes les heures · Dernière exécution il y a 38 min', weeklyLast2: 'Hebdomadaire · Dernière exécution il y a 2 jours',
    today: 'Aujourd’hui', yesterday: 'Hier', justNow: 'À l’instant',
  },
} as const;

type TranslationKey = keyof typeof messages.en;
type AppContextValue = { language: Language; setLanguage: (language: Language) => void; theme: Theme; setTheme: (theme: Theme) => void; t: (key: TranslationKey) => string };

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<Language>(() => {
    const saved = localStorage.getItem('flowforge-language');
    return saved === 'es' || saved === 'fr' || saved === 'en' ? saved : 'en';
  });
  const [theme, setTheme] = useState<Theme>(() => localStorage.getItem('flowforge-theme') === 'light' ? 'light' : 'dark');

  useEffect(() => { localStorage.setItem('flowforge-language', language); }, [language]);
  useEffect(() => {
    localStorage.setItem('flowforge-theme', theme);
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  const value = useMemo<AppContextValue>(() => ({
    language, setLanguage, theme, setTheme,
    t: (key) => messages[language][key] ?? messages.en[key],
  }), [language, theme]);

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const context = useContext(AppContext);
  if (!context) throw new Error('useApp must be used within AppProvider');
  return context;
}
