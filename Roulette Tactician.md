import React, { useState, useRef, useEffect, useLayoutEffect, useMemo } from 'react';
import { Trash2, DollarSign, History, Calculator, Save, RotateCcw, X, Edit2, Grid, List, ChevronRight, Folder, Download, Upload, Trash, Settings } from 'lucide-react';

// --- Configuration ---
const CHIP_VALUES = [1, 5, 25, 100, 500, 1000];
const CHIP_COLORS = {
  1: 'bg-slate-200 text-slate-800 border-yellow-500 border-2',
  5: 'bg-red-600 text-white border-yellow-500 border-2',
  25: 'bg-green-600 text-white border-yellow-500 border-2',
  100: 'bg-slate-900 text-white border-yellow-500 border-2',
  500: 'bg-purple-700 text-white border-yellow-500 border-2',
  1000: 'bg-yellow-500 text-black border-black border-2'
};

// Visual Grid Data
const ROW_TOP = [3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36];
const ROW_MID = [2, 5, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35];
const ROW_BOT = [1, 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34];
const RED_NUMBERS = new Set([1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36]);

// --- Utility: Heatmap Color Logic ---
const getHeatColor = (n, stats, hotList, coldList) => {
    // 1. Check Manual Overrides (Absolute Extremes)
    if (hotList.includes(n)) return '#facc15'; // Manual Hot: Bright Yellow
    if (coldList.includes(n)) return '#172554'; // Manual Cold: Deepest Navy

    // Define Palette Points
    const COLOR_GRAY = { r: 100, g: 116, b: 139 }; // Neutral (slate-500)
    
    // SAFE TARGETS (Distinct from Manual Extremes)
    // Warm: Golden Orange (Not as bright as #facc15)
    const COLOR_WARM_TARGET = { r: 202, g: 138, b: 4 }; // Yellow-700
    // Cool: Royal Blue (Not as dark as #172554)
    const COLOR_COOL_TARGET = { r: 59, g: 130, b: 246 }; // Blue-500

    // Get the calculated rank for this number among non-manual numbers
    // rank 0 is best score, rank N is worst score
    const rankData = stats.nonManualRanks[n];
    
    // If n is manual or something went wrong, default to Gray
    if (!rankData) return `rgb(${COLOR_GRAY.r}, ${COLOR_GRAY.g}, ${COLOR_GRAY.b})`;

    const { rank, totalNonManual, score } = rankData;
    const medianRank = (totalNonManual - 1) / 2;

    let r, g, b;

    // Determine position relative to Median
    if (rank < medianRank) {
        // --- TOP HALF (Yellow Tones) ---
        // Normalize 0..Median to 1.0..0.0 intensity
        // Rank 0 (Best) -> Intensity 1.0
        // Rank near Median -> Intensity 0.0
        const intensity = 1 - (rank / medianRank);
        
        // Ensure even the slightest warmth is visible if > 0 spins
        const effectiveIntensity = Math.max(0.1, intensity);

        r = Math.round(COLOR_GRAY.r + ((COLOR_WARM_TARGET.r - COLOR_GRAY.r) * effectiveIntensity));
        g = Math.round(COLOR_GRAY.g + ((COLOR_WARM_TARGET.g - COLOR_GRAY.g) * effectiveIntensity));
        b = Math.round(COLOR_GRAY.b + ((COLOR_WARM_TARGET.b - COLOR_GRAY.b) * effectiveIntensity));
        
    } else {
        // --- BOTTOM HALF (Blue Tones) ---
        // Normalize Median..Total to 0.0..1.0 intensity
        // Rank near Median -> Intensity 0.0
        // Rank Total (Worst) -> Intensity 1.0
        const intensity = (rank - medianRank) / (totalNonManual - medianRank);
        
        // PROGRESSIVE COOLING LOGIC:
        // Scale the Blue intensity based on Total Spins.
        // Start: Grayish. 50 Spins: Target Blue.
        // This ensures unknown numbers don't turn bright blue immediately.
        const spinFactor = Math.min(stats.totalHistoryCount, 50) / 50; 
        
        const finalIntensity = intensity * spinFactor;

        r = Math.round(COLOR_GRAY.r + ((COLOR_COOL_TARGET.r - COLOR_GRAY.r) * finalIntensity));
        g = Math.round(COLOR_GRAY.g + ((COLOR_COOL_TARGET.g - COLOR_GRAY.g) * finalIntensity));
        b = Math.round(COLOR_GRAY.b + ((COLOR_COOL_TARGET.b - COLOR_GRAY.b) * finalIntensity));
    }

    return `rgb(${r},${g},${b})`;
};

const getRouletteColor = (n) => {
    if (n === 0 || n === '00') return '#15803d'; // Green
    if (RED_NUMBERS.has(n)) return '#dc2626'; // Red
    return '#0f172a'; // Black
};

// --- Sub-Components ---

const Chip = ({ value, className = "", style = {} }) => {
  const colorClass = CHIP_COLORS[value] || CHIP_COLORS[1];
  return (
    <div 
      className={`rounded-full flex items-center justify-center font-bold shadow-md select-none ${colorClass} ${className}`}
      style={{ 
        width: '2.4em', 
        height: '2.4em', 
        fontSize: '0.6em', 
        boxShadow: '0 2px 4px rgba(0,0,0,0.4)',
        zIndex: 50,
        ...style 
      }} 
    >
      {value >= 1000 ? '1k' : value}
    </div>
  );
};

const Keypad = ({ onEnter, onClose, onEditHistory, showEditHistoryLink = true, title = "Enter Result" }) => {
    const [input, setInput] = useState('');
    const handleNum = (n) => setInput(prev => (prev + n).slice(0, 2)); 
    const handleSubmit = () => {
        if (input === '') return;
        const num = parseInt(input, 10);
        if (!isNaN(num) && num >= 0 && num <= 36) { onEnter(num); setInput(''); }
    };
    return (
        <div className="absolute inset-0 bg-slate-900/95 z-[100] flex flex-col items-center justify-center p-4">
            <div className="w-full max-w-sm bg-slate-800 rounded-xl border border-slate-700 shadow-2xl overflow-hidden">
                <div className="p-4 bg-slate-700 flex justify-between items-center"><span className="font-bold text-white">{title}</span><button onClick={onClose} className="p-1 hover:bg-slate-600 rounded"><X size={20}/></button></div>
                <div className="p-6 bg-black flex justify-center items-center border-b border-slate-600"><span className={`text-6xl font-mono font-bold ${input ? 'text-white' : 'text-slate-600'}`}>{input || '--'}</span></div>
                <div className="grid grid-cols-3 gap-1 p-2 bg-slate-800">
                    {[1, 2, 3, 4, 5, 6, 7, 8, 9].map(n => <button key={n} onClick={() => handleNum(n.toString())} className="p-4 bg-slate-700 hover:bg-slate-600 text-2xl font-bold rounded active:scale-95 transition-transform">{n}</button>)}
                    <button onClick={() => setInput(prev => prev.slice(0, -1))} className="p-4 bg-red-900/50 hover:bg-red-900/70 text-red-200 font-bold rounded flex items-center justify-center">DEL</button>
                    <button onClick={() => handleNum('0')} className="p-4 bg-slate-700 hover:bg-slate-600 text-2xl font-bold rounded">0</button>
                    <button onClick={handleSubmit} className="p-4 bg-green-600 hover:bg-green-500 text-white font-bold rounded flex items-center justify-center">OK</button>
                </div>
                {showEditHistoryLink && <div className="p-3 border-t border-slate-700 text-center"><button onClick={onEditHistory} className="text-sm text-blue-400 hover:underline flex items-center justify-center gap-2 w-full"><Edit2 size={14}/> Edit Spin History</button></div>}
            </div>
        </div>
    );
};

const HotColdEditor = ({ type, numbers, onSave, onClose }) => {
    const [selectedIdx, setSelectedIdx] = useState(0);
    const [tempNumbers, setTempNumbers] = useState([...numbers]);
    const handleInput = (num) => {
        const currentVal = tempNumbers[selectedIdx];
        let newVal = (currentVal === null) ? num : parseInt(`${currentVal}${num}`);
        if (newVal > 36) newVal = num;
        const newNums = [...tempNumbers]; newNums[selectedIdx] = newVal; setTempNumbers(newNums);
    };
    const clearSlot = (e, idx) => { e.stopPropagation(); const n = [...tempNumbers]; n[idx] = null; setTempNumbers(n); setSelectedIdx(idx); };

    return (
        <div className="absolute inset-0 bg-slate-900/95 z-[100] flex flex-col items-center justify-center p-4">
            <div className="w-full max-w-sm bg-slate-800 rounded-xl border border-slate-700 shadow-2xl flex flex-col h-[80vh]">
                <div className={`p-4 flex justify-between items-center ${type === 'HOT' ? 'bg-yellow-900/50' : 'bg-blue-900/50'}`}>
                    <span className={`font-bold ${type === 'HOT' ? 'text-yellow-400' : 'text-blue-400'}`}>Edit {type} Numbers</span>
                    <button onClick={() => { onSave(tempNumbers); onClose(); }} className="p-1 px-3 bg-green-700 rounded text-sm font-bold text-white">Done</button>
                </div>
                <div className="flex justify-between p-4 bg-slate-900 border-b border-slate-700">
                    {tempNumbers.map((num, idx) => (
                        <div key={idx} onClick={() => setSelectedIdx(idx)} className={`relative w-12 h-12 flex items-center justify-center rounded-lg border-2 font-bold text-xl cursor-pointer transition-all ${selectedIdx === idx ? 'border-white bg-slate-700 scale-110 z-10' : 'border-slate-600 bg-slate-800'} ${num !== null && (type === 'HOT' ? 'text-yellow-400' : 'text-blue-400')}`}>
                            {num !== null ? num : '-'}
                            {num !== null && <button onClick={(e) => clearSlot(e, idx)} className="absolute -top-2 -right-2 bg-slate-700 rounded-full text-slate-400 hover:text-white p-0.5"><X size={12} /></button>}
                            <span className="absolute -bottom-4 text-[9px] text-slate-500">#{idx + 1}</span>
                        </div>
                    ))}
                </div>
                <div className="flex-1 bg-slate-800 p-2"><div className="grid grid-cols-3 gap-2 h-full">{[1, 2, 3, 4, 5, 6, 7, 8, 9].map(n => <button key={n} onClick={() => handleInput(n)} className="bg-slate-700 hover:bg-slate-600 text-white text-2xl font-bold rounded active:scale-95">{n}</button>)}<button className="bg-transparent"></button><button onClick={() => handleInput(0)} className="bg-slate-700 hover:bg-slate-600 text-white text-2xl font-bold rounded">0</button><button onClick={() => { onSave(tempNumbers); onClose(); }} className="bg-green-700 hover:bg-green-600 text-white font-bold rounded flex flex-col items-center justify-center text-sm"><Save size={18}/> SAVE</button></div></div>
            </div>
        </div>
    );
};

const HistoryEditor = ({ history, setHistory, onClose }) => {
    return (
        <div className="absolute inset-0 bg-slate-900 z-[100] flex flex-col p-4 animate-in fade-in zoom-in-95 duration-200">
             <div className="flex justify-between items-center mb-4 bg-slate-800 p-3 rounded-lg border border-slate-700"><h2 className="text-xl font-bold text-white">Edit History</h2><button onClick={onClose} className="p-2 bg-slate-700 hover:bg-slate-600 rounded text-white transition-colors"><X size={20}/></button></div>
            <div className="flex-1 overflow-y-auto grid grid-cols-6 gap-2 content-start pr-2 custom-scrollbar">
                {history.map((num, idx) => (
                    <div key={idx} className="relative group">
                        <div className="w-full h-12 flex items-center justify-center rounded font-bold text-lg text-white shadow-md border border-white/10" style={{ backgroundColor: getRouletteColor(num) }}>{num}</div>
                        <button onClick={() => { const newHist = [...history]; newHist.splice(idx, 1); setHistory(newHist); }} className="absolute -top-2 -right-2 bg-red-500 rounded-full p-1 opacity-0 group-hover:opacity-100 transition-opacity z-10 shadow-lg hover:scale-110"><X size={12} className="text-white"/></button>
                    </div>
                ))}
                {history.length === 0 && <div className="col-span-6 text-center text-slate-500 mt-10">No spins recorded</div>}
            </div>
        </div>
    );
};

const FullHistoryView = ({ history, onClose, wheelName }) => {
    const downloadCSV = () => {
        if (!history || history.length === 0) return;
        let csvContent = "data:text/csv;charset=utf-8,Spin Index,Number,Color\n";
        history.forEach((num, index) => {
            const spinIndex = history.length - index;
            const colorName = num === 0 ? "Green" : RED_NUMBERS.has(num) ? "Red" : "Black";
            csvContent += `${spinIndex},${num},${colorName}\n`;
        });
        const encodedUri = encodeURI(csvContent);
        const link = document.createElement("a");
        link.setAttribute("href", encodedUri);
        link.setAttribute("download", `${wheelName.replace(/[^a-z0-9]/gi, '_').toLowerCase()}_logs.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    return (
        <div className="absolute inset-0 bg-slate-900 z-[100] flex flex-col p-4 animate-in fade-in zoom-in-95 duration-200">
             <div className="flex justify-between items-center mb-4 bg-slate-800 p-3 rounded-lg border border-slate-700">
                <div className="flex items-center gap-2"><History size={24} className="text-blue-400"/><h2 className="text-xl font-bold text-white">Full Spin History</h2></div>
                <div className="flex gap-2"><button onClick={downloadCSV} className="p-2 bg-blue-600 hover:bg-blue-500 rounded text-white transition-colors flex items-center gap-2 font-bold text-sm"><Download size={18}/> Export CSV</button><button onClick={onClose} className="p-2 bg-slate-700 hover:bg-slate-600 rounded text-white transition-colors"><X size={20}/></button></div>
            </div>
            <div className="flex-1 overflow-y-auto pr-2 custom-scrollbar">
                {history.length === 0 ? <div className="flex flex-col items-center justify-center h-full text-slate-500"><History size={48} className="mb-4 opacity-20"/><p>No spins recorded yet</p></div> : 
                <div className="grid grid-cols-5 sm:grid-cols-8 md:grid-cols-10 gap-3 content-start">
                    {history.map((num, idx) => (
                        <div key={idx} className="flex flex-col items-center">
                            <span className="text-[10px] text-slate-500 mb-1">#{history.length - idx}</span>
                            <div className="w-12 h-12 flex items-center justify-center rounded-lg font-bold text-xl text-white shadow-lg border border-white/10" style={{ backgroundColor: getRouletteColor(num) }}>{num}</div>
                        </div>
                    ))}
                </div>}
            </div>
        </div>
    );
};

const SessionsModal = ({ currentSession, onLoad, onClose }) => {
    const [savedSessions, setSavedSessions] = useState({});
    useEffect(() => { const data = localStorage.getItem('roulette_saved_sessions'); if (data) setSavedSessions(JSON.parse(data)); }, []);
    const handleSave = () => { if (!currentSession.name) return; const newSessions = { ...savedSessions, [currentSession.name]: { ...currentSession, timestamp: Date.now() } }; setSavedSessions(newSessions); localStorage.setItem('roulette_saved_sessions', JSON.stringify(newSessions)); };
    const handleDelete = (name) => { const newSessions = { ...savedSessions }; delete newSessions[name]; setSavedSessions(newSessions); localStorage.setItem('roulette_saved_sessions', JSON.stringify(newSessions)); };
    return (
        <div className="absolute inset-0 bg-slate-900/95 z-[100] flex flex-col items-center justify-center p-4">
            <div className="w-full max-w-md bg-slate-800 rounded-xl border border-slate-700 shadow-2xl flex flex-col max-h-[80vh]">
                <div className="p-4 bg-slate-700 flex justify-between items-center"><div className="flex items-center gap-2"><Folder className="text-yellow-500"/><span className="font-bold text-white">Session Manager</span></div><button onClick={onClose} className="p-1 hover:bg-slate-600 rounded"><X size={20}/></button></div>
                <div className="p-4 border-b border-slate-700 bg-slate-800"><div className="text-xs text-slate-400 mb-1">Current Session Name:</div><div className="flex gap-2"><div className="flex-1 bg-slate-900 px-3 py-2 rounded text-white font-mono border border-slate-600">{currentSession.name}</div><button onClick={handleSave} className="bg-green-600 hover:bg-green-500 text-white px-4 rounded font-bold flex items-center gap-2"><Save size={16}/> Save</button></div></div>
                <div className="flex-1 overflow-y-auto p-2 space-y-2">
                    {Object.entries(savedSessions).length === 0 && <div className="text-center text-slate-500 py-8">No saved sessions found.</div>}
                    {Object.entries(savedSessions).sort((a,b) => b[1].timestamp - a[1].timestamp).map(([name, data]) => (
                        <div key={name} className="bg-slate-700/50 p-3 rounded flex justify-between items-center group hover:bg-slate-700 border border-transparent hover:border-slate-500 transition-all">
                            <div className="overflow-hidden"><div className="font-bold text-white truncate">{name}</div><div className="text-xs text-slate-400">{new Date(data.timestamp).toLocaleString()} • {data.history.length} spins</div></div>
                            <div className="flex gap-2"><button onClick={() => { onLoad(data); onClose(); }} className="p-2 bg-blue-600 hover:bg-blue-500 text-white rounded" title="Load"><Upload size={16}/></button><button onClick={() => handleDelete(name)} className="p-2 bg-red-900/50 hover:bg-red-600 text-red-200 hover:text-white rounded" title="Delete"><Trash size={16}/></button></div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

// --- Main Application ---

export default function RoulettePlanner() {
  const [tableType, setTableType] = useState('EU'); 
  const [chips, setChips] = useState([]); 
  const [selectedChipValue, setSelectedChipValue] = useState(5);
  const [totalBet, setTotalBet] = useState(0);
  const [dragState, setDragState] = useState(null); 
  const [boardScale, setBoardScale] = useState(16);

  // Features State
  const [spinHistory, setSpinHistory] = useState([]);
  const [wheelName, setWheelName] = useState('My Wheel');
  const [hotNumbers, setHotNumbers] = useState([null, null, null, null, null]);
  const [coldNumbers, setColdNumbers] = useState([null, null, null, null, null]);
  const [analysisWindow, setAnalysisWindow] = useState(50); 

  // Modal States
  const [showKeypad, setShowKeypad] = useState(false);
  const [showHistoryEdit, setShowHistoryEdit] = useState(false);
  const [showFullHistory, setShowFullHistory] = useState(false); 
  const [editHotCold, setEditHotCold] = useState(null); 
  const [showSessions, setShowSessions] = useState(false);

  const boardRef = useRef(null);

  const sanitizeNumbers = (arr, length = 0) => {
      if (!Array.isArray(arr)) return length > 0 ? Array(length).fill(null) : [];
      return arr.map(x => (typeof x === 'number' ? x : null)).filter(x => length === 0 || x !== undefined);
  };

  useEffect(() => {
      const saved = localStorage.getItem('roulette_current_state_v10'); // New version for safety
      if (saved) {
          try {
              const data = JSON.parse(saved);
              setSpinHistory(sanitizeNumbers(data.history));
              setWheelName(data.name || 'My Wheel');
              setChips(Array.isArray(data.chips) ? data.chips : []);
              setHotNumbers(sanitizeNumbers(data.hot, 5));
              setColdNumbers(sanitizeNumbers(data.cold, 5));
              if (data.window) setAnalysisWindow(parseInt(data.window) || 50);
          } catch (e) { console.error("Load failed", e); }
      }
  }, []);

  useEffect(() => {
      const data = { history: spinHistory, name: wheelName, chips: chips, hot: hotNumbers, cold: coldNumbers, window: analysisWindow };
      localStorage.setItem('roulette_current_state_v10', JSON.stringify(data));
  }, [spinHistory, wheelName, chips, hotNumbers, coldNumbers, analysisWindow]);

  const loadSessionData = (data) => {
      setSpinHistory(sanitizeNumbers(data.history));
      setWheelName(data.name || 'My Wheel');
      setChips(Array.isArray(data.chips) ? data.chips : []);
      setHotNumbers(sanitizeNumbers(data.hot, 5));
      setColdNumbers(sanitizeNumbers(data.cold, 5));
      if (data.window) setAnalysisWindow(parseInt(data.window) || 50);
  };

  // --- STATS & RANKING SYSTEM ---
  const stats = useMemo(() => {
      const freq = {};
      const lastIndices = {}; 
      const windowedHistory = spinHistory.slice(0, analysisWindow);
      const analysisCount = windowedHistory.length;

      // 1. Compute Base Freq and Recency for ALL numbers (0-36)
      for(let i=0; i<=36; i++) { freq[i] = 0; }

      windowedHistory.forEach((n, idx) => {
          if (n !== null && n !== undefined) {
              freq[n] = (freq[n] || 0) + 1;
              if (lastIndices[n] === undefined) lastIndices[n] = idx;
          }
      });

      // 2. Identify Non-Manual Numbers
      // We only rank numbers that are NOT manually Hot or Cold
      const manualSet = new Set([...hotNumbers, ...coldNumbers].filter(n => n !== null));
      const nonManualNumbers = [];

      for(let i=0; i<=36; i++) {
          if(!manualSet.has(i)) {
              // Calculate a score for sorting
              const f = freq[i];
              const lastIdx = lastIndices[i];
              let recencyScore = 0;
              if (lastIdx !== undefined) {
                  // 1.0 (Recent) -> 0.0 (Old)
                  recencyScore = 1 - (lastIdx / Math.max(1, analysisCount));
              }
              // Score = Frequency + Tie-Breaker
              // If Freq is 0, score is 0.
              const score = f + (recencyScore * 0.99);
              
              nonManualNumbers.push({ n: i, score });
          }
      }

      // 3. Sort Non-Manuals by Score Descending
      // Higher score = Better rank (0 is best)
      // If scores equal (e.g. both 0), sort by number for stability
      nonManualNumbers.sort((a, b) => {
          if (b.score !== a.score) return b.score - a.score;
          return a.n - b.n;
      });

      // 4. Map Ranks
      const nonManualRanks = {};
      nonManualNumbers.forEach((item, index) => {
          nonManualRanks[item.n] = {
              rank: index,
              totalNonManual: nonManualNumbers.length,
              score: item.score
          };
      });

      // Find max freq for chip snapping logic maybe? Not used for heatmap anymore.
      let maxFreq = 0;
      Object.values(freq).forEach(f => { if(f > maxFreq) maxFreq = f; });

      return { freq, maxFreq, lastIndices, totalHistoryCount: spinHistory.length, analysisCount, nonManualRanks };
  }, [spinHistory, analysisWindow, hotNumbers, coldNumbers]);

  useLayoutEffect(() => {
    const handleResize = () => { if (boardRef.current) setBoardScale(Math.max(10, boardRef.current.offsetWidth / 62)); };
    handleResize(); const observer = new ResizeObserver(handleResize); if (boardRef.current) observer.observe(boardRef.current);
    return () => observer.disconnect();
  }, []);

  const GEO = { ZERO_W: 0.08, NUM_W: 0.80, COL_W: 0.12, MAIN_H: 0.60, DOZ_H: 0.15, OUT_H: 0.25 };

  const getLocalCoords = (clientX, clientY) => {
    if (!boardRef.current) return { x: -1, y: -1, w: 1, h: 1 };
    const rect = boardRef.current.getBoundingClientRect();
    const styles = window.getComputedStyle(boardRef.current);
    return {
      x: clientX - rect.left - (parseFloat(styles.borderLeftWidth) || 0),
      y: clientY - rect.top - (parseFloat(styles.borderTopWidth) || 0),
      w: boardRef.current.clientWidth, h: boardRef.current.clientHeight
    };
  };

  const getNumAt = (row, col) => 3 - row + (col * 3);

  const findSnapTarget = (x, y, W, H) => {
    const nx = x / W; const ny = y / H;
    const gridLeft = GEO.ZERO_W; const gridBot = GEO.MAIN_H;

    if (nx > gridLeft && nx < (gridLeft + GEO.NUM_W) && ny < (gridBot + 0.05)) {
      const rawCol = (nx - gridLeft) / GEO.NUM_W * 12; const rawRow = (ny / gridBot) * 3;
      const baseC = Math.floor(rawCol); const baseR = Math.floor(rawRow);
      
      const candidates = [];
      const add = (offsetC, offsetR, type, idFn) => {
        const hitCX = baseC + offsetC; const hitRY = baseR + offsetR; 
        const dist = Math.sqrt(Math.pow(rawCol - hitCX, 2) + Math.pow(rawRow - hitRY, 2));
        candidates.push({ dist, cx: hitCX, ry: hitRY, type, idFn });
      };

      if (baseC >= 0 && baseC < 12 && baseR >= 0 && baseR < 3) add(0.5, 0.5, 'straight', () => `num-${getNumAt(baseR, baseC)}`);

      for (let i = 0; i <= 1; i++) {
        for (let j = 0; j <= 1; j++) {
            const checkC = baseC + i; const checkR = baseR + j;
            if (checkR === 0) { if (checkC > 0 && checkC < 12) add(i, 0, 'line', () => `line-${getNumAt(0, checkC-1)}-${getNumAt(0, checkC)}`); } 
            else if (checkR > 0 && checkR < 3) { if (checkC > 0 && checkC < 12) add(i, j, 'corner', () => `corn-${getNumAt(checkR-1, checkC-1)}-${getNumAt(checkR, checkC)}`); }
        }
      }

      if (baseC > 0 && baseC < 12 && baseR >= 0 && baseR < 3) add(0, 0.5, 'split-v', () => `split-${getNumAt(baseR, baseC-1)}-${getNumAt(baseR, baseC)}`);
      else if (baseC === 0 && baseR >= 0 && baseR < 3) add(0, 0.5, 'split-zero', () => `split-0-${getNumAt(baseR, 0)}`);
      if (baseC < 11 && baseR >= 0 && baseR < 3) add(1, 0.5, 'split-v', () => `split-${getNumAt(baseR, baseC)}-${getNumAt(baseR, baseC+1)}`);
      
      if (baseR > 0 && baseR < 3 && baseC >= 0 && baseC < 12) add(0.5, 0, 'split-h', () => `split-${getNumAt(baseR-1, baseC)}-${getNumAt(baseR, baseC)}`);
      if (baseR < 2 && baseC >= 0 && baseC < 12) add(0.5, 1, 'split-h', () => `split-${getNumAt(baseR, baseC)}-${getNumAt(baseR+1, baseC)}`);

      if (baseR === 0 && baseC >= 0 && baseC < 12) add(0.5, 0, 'street', () => `street-${getNumAt(0, baseC)}`);

      candidates.sort((a, b) => a.dist - b.dist);
      const winner = candidates[0];
      if (winner && winner.dist < 0.6) return { x: (gridLeft + (winner.cx / 12 * GEO.NUM_W)) * W, y: (winner.ry / 3 * gridBot) * H, betId: winner.idFn() };
    }

    if (nx < GEO.ZERO_W && ny < GEO.MAIN_H) {
        if (tableType === 'US') {
            const isZero = ny < (GEO.MAIN_H / 2);
            return { x: (GEO.ZERO_W / 2) * W, y: (isZero ? GEO.MAIN_H * 0.25 : GEO.MAIN_H * 0.75) * H, betId: isZero ? 'num-0' : 'num-00' };
        } else return { x: (GEO.ZERO_W / 2) * W, y: (GEO.MAIN_H / 2) * H, betId: 'num-0' };
    }

    if (nx > (GEO.ZERO_W + GEO.NUM_W) && ny < GEO.MAIN_H) {
        const rowH = GEO.MAIN_H / 3; let rIdx = 0; 
        if (ny > rowH * 2) rIdx = 2; else if (ny > rowH) rIdx = 1; 
        const ids = ['2to1-top', '2to1-mid', '2to1-bot'];
        return { x: (GEO.ZERO_W + GEO.NUM_W + (GEO.COL_W * 0.5)) * W, y: (rIdx + 0.5) * rowH * H, betId: ids[rIdx] };
    }

    if (ny > GEO.MAIN_H && ny < (GEO.MAIN_H + GEO.DOZ_H)) {
        if (nx > GEO.ZERO_W && nx < (GEO.ZERO_W + GEO.NUM_W)) {
            const dIdx = Math.floor((nx - GEO.ZERO_W) / GEO.NUM_W * 3);
            if(dIdx >= 0 && dIdx < 3) return { x: (GEO.ZERO_W + ((dIdx + 0.5) / 3 * GEO.NUM_W)) * W, y: (GEO.MAIN_H + GEO.DOZ_H / 2) * H, betId: `doz-${dIdx+1}` };
        }
    }

    if (ny > (GEO.MAIN_H + GEO.DOZ_H)) {
         if (nx > GEO.ZERO_W && nx < (GEO.ZERO_W + GEO.NUM_W)) {
             const zIdx = Math.floor((nx - GEO.ZERO_W) / GEO.NUM_W * 6);
             const zones = ['low', 'even', 'red', 'black', 'odd', 'high'];
             if(zIdx >= 0 && zIdx < 6) return { x: (GEO.ZERO_W + ((zIdx + 0.5) / 6 * GEO.NUM_W)) * W, y: (GEO.MAIN_H + GEO.DOZ_H + GEO.OUT_H / 2) * H, betId: zones[zIdx] };
         }
    }
    return null;
  };

  const handleBoardDown = (e) => { e.preventDefault(); const { x, y, w, h } = getLocalCoords(e.clientX, e.clientY); const target = findSnapTarget(x, y, w, h); if (target) setChips(prev => [...prev, { id: Date.now(), value: selectedChipValue, x: target.x, y: target.y, betId: target.betId }]); };
  const startDragChip = (e, chipId) => { e.stopPropagation(); e.preventDefault(); const chip = chips.find(c => c.id === chipId); if (!chip) return; let cx = e.clientX, cy = e.clientY; if (e.nativeEvent instanceof TouchEvent) { cx = e.nativeEvent.touches[0].clientX; cy = e.nativeEvent.touches[0].clientY; } setDragState({ chipId, startX: cx, startY: cy, originalX: chip.x, originalY: chip.y, currentX: chip.x, currentY: chip.y, betId: chip.betId }); };
  const onPointerMove = (e) => { if (!dragState) return; e.preventDefault(); let cx = e.clientX, cy = e.clientY; if (e.nativeEvent instanceof TouchEvent) { cx = e.nativeEvent.touches[0].clientX; cy = e.nativeEvent.touches[0].clientY; } setDragState(prev => ({ ...prev, currentX: prev.originalX + (cx - prev.startX), currentY: prev.originalY + (cy - prev.startY), lastPointerX: cx, lastPointerY: cy })); };
  const onPointerUp = (e) => { if (!dragState) return; e.preventDefault(); let cx = dragState.lastPointerX || dragState.startX, cy = dragState.lastPointerY || dragState.startY; if (e.clientX) { cx = e.clientX; cy = e.clientY; } const { x, y, w, h } = getLocalCoords(cx, cy); const isInside = x >= 0 && x <= w && y >= 0 && y <= h; if (!isInside) setChips(prev => prev.filter(c => c.id !== dragState.chipId)); else { const target = findSnapTarget(x, y, w, h); if (target) setChips(prev => prev.map(c => c.id === dragState.chipId ? { ...c, x: target.x, y: target.y, betId: target.betId } : c)); else setChips(prev => prev.map(c => c.id === dragState.chipId ? { ...c, x: dragState.originalX, y: dragState.originalY } : c)); } setDragState(null); };
  const handleTrash = () => setChips([]);
  const isRed = (n) => RED_NUMBERS.has(n);
  useEffect(() => { setTotalBet(chips.reduce((acc, c) => acc + c.value, 0)); }, [chips]);

  const renderNumberBox = (n) => {
      const heat = getHeatColor(n, stats, hotNumbers, coldNumbers);
      const originalBg = isRed(n) ? '#dc2626' : '#0f172a'; 
      return (
        <div key={n} className="relative flex items-center justify-center border-r border-white/20 overflow-hidden">
             <div className="absolute inset-0" style={{ background: `linear-gradient(to bottom, ${originalBg} 50%, ${heat} 50%)` }}></div>
             <span className="relative z-10 font-bold text-white leading-none" style={{ fontSize: '2.5em', transform: 'scaleY(1.1)' }}>{n}</span>
        </div>
      );
  };

  const renderZeroBox = (label, value) => {
      // For Single Zero wheel (EU), value is 0. 
      // For US, 0 and 00. Assuming input 0 maps to 0. 
      // If we had input for 00, we'd map it. Currently keyboard 0-36. 
      // If 00 is displayed but not tracked, it will be Rank N (gray/blue).
      const heat = getHeatColor(value, stats, hotNumbers, coldNumbers);
      const originalBg = '#15803d'; // Green
      return (
         <div className="flex-1 flex items-center justify-center relative overflow-hidden text-white font-bold leading-none border-b border-white/20 last:border-b-0">
             <div className="absolute inset-0" style={{ background: `linear-gradient(to bottom, ${originalBg} 50%, ${heat} 50%)` }}></div>
             <span className="relative z-10" style={{ fontSize: tableType === 'US' ? '3em' : '5em' }}>{label}</span>
         </div>
      );
  };

  return (
    <div className="flex flex-col h-screen bg-slate-900 text-white select-none touch-none overflow-hidden font-sans">
      
      {showKeypad && <Keypad title="Enter Spin Result" onEnter={(n) => { setSpinHistory(p => [n, ...p]); setShowKeypad(false); }} onClose={() => setShowKeypad(false)} onEditHistory={() => { setShowKeypad(false); setShowHistoryEdit(true); }} />}
      {editHotCold && <HotColdEditor type={editHotCold} numbers={editHotCold === 'HOT' ? hotNumbers : coldNumbers} onSave={(nums) => editHotCold === 'HOT' ? setHotNumbers(nums) : setColdNumbers(nums)} onClose={() => setEditHotCold(null)} />}
      {showHistoryEdit && <HistoryEditor history={spinHistory} setHistory={setSpinHistory} onClose={() => setShowHistoryEdit(false)} />}
      {showFullHistory && <FullHistoryView history={spinHistory} onClose={() => setShowFullHistory(false)} wheelName={wheelName} />}
      {showSessions && <SessionsModal currentSession={{ name: wheelName, history: spinHistory, chips, hot: hotNumbers, cold: coldNumbers, window: analysisWindow }} onLoad={loadSessionData} onClose={() => setShowSessions(false)} />}

      <div className="flex flex-col gap-2 p-2 bg-slate-800 border-b border-slate-700 shadow-lg z-10 shrink-0">
         <div className="flex justify-between items-center">
            <h1 className="text-xl font-bold text-yellow-500 hidden sm:block">RouletteTactician</h1>
            <input type="text" value={wheelName} onChange={(e) => setWheelName(e.target.value)} className="bg-slate-700 text-white px-2 py-1 rounded border border-slate-600 text-sm w-32 focus:outline-none focus:border-yellow-500" placeholder="Table Name" />
            
            <div className="flex items-center gap-2">
                <div className="flex items-center bg-slate-700 rounded px-2 py-1 text-xs text-slate-300">
                    <span className="mr-1 hidden md:inline">Analyze Last:</span>
                    <input 
                        type="number" 
                        min="1" 
                        max="999"
                        value={analysisWindow} 
                        onChange={(e) => setAnalysisWindow(parseInt(e.target.value) || 50)}
                        className="w-10 bg-slate-800 text-white text-center rounded border border-slate-600 focus:border-yellow-500 outline-none"
                    />
                </div>

                <button onClick={() => setShowSessions(true)} className="p-2 bg-slate-700 hover:bg-slate-600 rounded text-yellow-500" title="Manage Sessions"><Folder size={18}/></button>
                <div className="flex bg-slate-700 rounded-lg p-0.5">
                    <button onClick={() => setTableType('US')} className={`px-2 py-1 rounded text-xs ${tableType === 'US' ? 'bg-blue-600' : 'text-slate-400'}`}>US</button>
                    <button onClick={() => setTableType('EU')} className={`px-2 py-1 rounded text-xs ${tableType === 'EU' ? 'bg-blue-600' : 'text-slate-400'}`}>EU</button>
                </div>
                <button onClick={() => setShowKeypad(true)} className="flex items-center gap-1 bg-indigo-600 hover:bg-indigo-500 text-white px-3 py-1 rounded text-sm font-bold shadow-lg active:scale-95 transition-transform"><Calculator size={16}/> Spin</button>
            </div>
         </div>

         <div className="flex justify-between items-center bg-black/40 p-1.5 rounded text-xs sm:text-sm">
             <button onClick={() => setEditHotCold('HOT')} className="flex gap-2 items-center hover:bg-white/5 px-2 rounded transition">
                 <span className="text-yellow-400 font-bold">HOT:</span>
                 {hotNumbers.some(n => n !== null) ? hotNumbers.map((h, i) => h !== null && <span key={i} className="font-mono bg-yellow-900/50 px-1 rounded text-yellow-100">{h}</span>) : <span className="text-slate-600 text-[10px] italic">Tap to set</span>}
             </button>
             <button onClick={() => setEditHotCold('COLD')} className="flex gap-2 items-center hover:bg-white/5 px-2 rounded transition">
                 <span className="text-blue-400 font-bold">COLD:</span>
                 {coldNumbers.some(n => n !== null) ? coldNumbers.map((c, i) => c !== null && <span key={i} className="font-mono bg-blue-900/50 px-1 rounded text-blue-100">{c}</span>) : <span className="text-slate-600 text-[10px] italic">Tap to set</span>}
             </button>
         </div>
         
         <div className="flex items-center gap-2 bg-slate-900/50 p-1 rounded overflow-hidden h-10 border border-slate-700/50">
             <div className="flex items-center gap-1 px-2 border-r border-slate-700 shrink-0"><span className="text-[10px] text-slate-400 uppercase font-bold">Last 20</span></div>
             <div className="flex-1 flex gap-1 overflow-x-auto scrollbar-hide items-center">
                 {spinHistory.length === 0 ? <span className="text-slate-600 text-xs px-2 italic">No spins yet</span> : null}
                 {spinHistory.slice(0, 20).map((num, idx) => (
                     <div key={idx} className="shrink-0 w-7 h-7 flex items-center justify-center rounded font-bold text-sm text-white shadow-sm border border-white/10" style={{ backgroundColor: getRouletteColor(num) }}>{num}</div>
                 ))}
             </div>
             <button onClick={() => setShowFullHistory(true)} className="px-2 h-full flex items-center justify-center text-slate-400 hover:text-white bg-slate-800 hover:bg-slate-700 rounded transition-colors shrink-0"><List size={16} /></button>
         </div>
      </div>

      <div className="flex-1 relative flex items-center justify-center bg-green-900 p-2 sm:p-4 overflow-hidden">
        <div ref={boardRef} onPointerDown={handleBoardDown} className="relative shadow-2xl border-[0.8em] border-yellow-900 rounded-lg bg-green-800" style={{ width: '100%', maxWidth: '1200px', aspectRatio: '2.8/1', fontSize: `${boardScale}px`, boxShadow: 'inset 0 0 2em rgba(0,0,0,0.5)' }}>
            <div className="absolute inset-0 grid h-full" style={{ gridTemplateColumns: '8% 80% 12%' }}>
                <div className="flex flex-col border-r border-white/20">
                    <div className="flex flex-col border-b border-white/20 bg-green-700" style={{ height: '60%' }}>
                        {tableType === 'US' ? <>{renderZeroBox('0', 0)}{renderZeroBox('00', '00')}</> : renderZeroBox('0', 0)}
                    </div>
                    <div className="flex-grow bg-green-800"></div>
                </div>
                <div className="flex flex-col h-full">
                    <div className="grid border-b border-white/20" style={{ height: '60%', gridTemplateRows: 'repeat(3, 1fr)', gridTemplateColumns: 'repeat(12, minmax(0, 1fr))' }}>{ROW_TOP.map(renderNumberBox)}{ROW_MID.map(renderNumberBox)}{ROW_BOT.map(renderNumberBox)}</div>
                    <div className="grid border-b border-white/20" style={{ height: '15%', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
                        {['1st 12', '2nd 12', '3rd 12'].map((l, i) => <div key={i} className="flex items-center justify-center border-r border-white/20 font-bold text-white/90 leading-none whitespace-nowrap" style={{ fontSize: '2.5em' }}>{l}</div>)}
                    </div>
                    <div className="grid" style={{ height: '25%', gridTemplateColumns: 'repeat(6, minmax(0, 1fr))' }}>
                        <div className="flex items-center justify-center border-r border-white/20 font-bold text-white/90 leading-none whitespace-nowrap" style={{ fontSize: '2em' }}>1-18</div>
                        <div className="flex items-center justify-center border-r border-white/20 font-bold text-white/90 leading-none whitespace-nowrap" style={{ fontSize: '2em' }}>EVEN</div>
                        <div className="border-r border-white/20 bg-red-600"></div>
                        <div className="border-r border-white/20 bg-slate-900"></div>
                        <div className="flex items-center justify-center border-r border-white/20 font-bold text-white/90 leading-none whitespace-nowrap" style={{ fontSize: '2em' }}>ODD</div>
                        <div className="flex items-center justify-center font-bold text-white/90 leading-none whitespace-nowrap" style={{ fontSize: '2em' }}>19-36</div>
                    </div>
                </div>
                <div className="flex flex-col h-full">
                    <div className="grid border-b border-white/20" style={{ height: '60%', gridTemplateRows: 'repeat(3, minmax(0, 1fr))' }}>
                        {[1, 2, 3].map(i => <div key={i} className="flex items-center justify-center border-b border-white/20 font-bold text-white/90 text-center leading-none whitespace-nowrap" style={{ fontSize: '2em' }}>2 to 1</div>)}
                    </div>
                    <div className="flex-grow"></div>
                </div>
            </div>
            {chips.map(chip => (
                <div key={chip.id} onPointerDown={(e) => startDragChip(e, chip.id)} className="absolute z-20 cursor-grab active:cursor-grabbing hover:scale-110 transition-transform" style={{ left: chip.id === dragState?.chipId ? dragState.currentX : chip.x, top: chip.id === dragState?.chipId ? dragState.currentY : chip.y, transform: 'translate(-50%, -50%)', pointerEvents: dragState?.chipId === chip.id ? 'none' : 'auto' }}>
                    <Chip value={chip.value} />
                </div>
            ))}
        </div>
      </div>

      <div className="bg-slate-800 p-3 border-t border-slate-700 z-10 shrink-0">
         <div className="flex justify-between items-center px-2">
             <div className="flex items-center gap-2 text-green-400 font-mono text-lg"><DollarSign size={16} />{totalBet.toLocaleString()}</div>
             <button onClick={handleTrash} className="text-red-400 hover:text-red-300 flex items-center gap-1 text-xs uppercase font-bold tracking-wider"><Trash2 size={16}/> Clear Chips</button>
         </div>
         <div className="flex justify-center gap-4 overflow-x-auto py-2">
            {CHIP_VALUES.map(val => (
                <button key={val} onClick={() => setSelectedChipValue(val)} className={`transform transition-all ${selectedChipValue === val ? 'scale-110 -translate-y-2' : 'opacity-75'}`}>
                    <Chip value={val} style={{ width: 40, height: 40, fontSize: 12 }} />
                </button>
            ))}
         </div>
      </div>
      {dragState && <div className="fixed inset-0 z-50 cursor-grabbing" onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerLeave={onPointerUp} />}
    </div>
  );
}