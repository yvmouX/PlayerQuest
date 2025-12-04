import React, { useState, useEffect } from 'react';
import { Save, RotateCcw, Plus, Trash2, Code, Gift, Target } from 'lucide-react';
import { Quest, QuestType, RewardType, Language } from '../types';
import { MOCK_QUESTS } from '../services/mockData';
import { useLanguage } from '../context/LanguageContext';

export const QuestEditor: React.FC = () => {
  const { language, t } = useLanguage();
  const [activeQuestId, setActiveQuestId] = useState<string>(MOCK_QUESTS[0].id);
  const [quest, setQuest] = useState<Quest>(MOCK_QUESTS[0]);
  const [jsonMode, setJsonMode] = useState(false);

  // Sync state when selection changes
  useEffect(() => {
    const found = MOCK_QUESTS.find(q => q.id === activeQuestId);
    if (found) setQuest({ ...found }); // Clone to avoid direct mutation of mock
  }, [activeQuestId]);

  const handleSave = () => {
    // Mock save
    console.log('Saving Quest:', quest);
    alert('Quest saved successfully (Mock)');
  };

  const updateLocalizedField = (field: 'name' | 'description', lang: string, value: string) => {
    setQuest(prev => ({
      ...prev,
      [field]: { ...prev[field], [lang]: value }
    }));
  };

  const addObjective = () => {
    setQuest(prev => ({
      ...prev,
      objectives: [...prev.objectives, { id: `obj_${Date.now()}`, type: 'block_break', target: 'stone', count: 1 }]
    }));
  };

  const removeObjective = (id: string) => {
    setQuest(prev => ({
      ...prev,
      objectives: prev.objectives.filter(o => o.id !== id)
    }));
  };

  return (
    <div className="flex gap-6 h-full">
      {/* Quest List Sidebar */}
      <div className="w-64 flex-shrink-0 bg-mc-panel border border-mc-border rounded-lg overflow-hidden flex flex-col">
        <div className="p-3 border-b border-mc-border bg-mc-border/20">
          <button className="w-full flex items-center justify-center gap-2 bg-mc-accent hover:bg-green-700 text-white py-2 rounded text-sm font-medium transition-colors">
            <Plus size={16} />
            {t('btn.create')}
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {MOCK_QUESTS.map(q => (
            <button
              key={q.id}
              onClick={() => setActiveQuestId(q.id)}
              className={`w-full text-left px-3 py-3 rounded text-sm transition-colors ${
                activeQuestId === q.id 
                  ? 'bg-blue-600/20 text-blue-400 border border-blue-500/50' 
                  : 'text-gray-400 hover:bg-mc-border hover:text-gray-200'
              }`}
            >
              <div className="font-medium truncate">{q.name[language] || q.name['en-US']}</div>
              <div className="text-xs opacity-60 truncate">{q.id}</div>
            </button>
          ))}
        </div>
      </div>

      {/* Editor Panel */}
      <div className="flex-1 flex flex-col gap-4">
        {/* Toolbar */}
        <div className="flex justify-between items-center bg-mc-panel p-4 rounded-lg border border-mc-border">
            <div className="flex gap-2">
                 <button 
                  onClick={() => setJsonMode(!jsonMode)}
                  className={`flex items-center gap-2 px-4 py-2 rounded text-sm font-medium transition-colors ${jsonMode ? 'bg-purple-600 text-white' : 'bg-mc-border text-gray-300'}`}
                >
                  <Code size={16} />
                  JSON
                </button>
            </div>
            <div className="flex gap-2">
                <button 
                  onClick={() => setQuest(MOCK_QUESTS.find(q => q.id === activeQuestId)!)}
                  className="flex items-center gap-2 px-4 py-2 bg-mc-border hover:bg-gray-600 text-white rounded text-sm font-medium transition-colors"
                >
                  <RotateCcw size={16} />
                  {t('btn.reset')}
                </button>
                <button 
                  onClick={handleSave}
                  className="flex items-center gap-2 px-4 py-2 bg-mc-accent hover:bg-green-700 text-white rounded text-sm font-medium transition-colors shadow-lg shadow-green-900/20"
                >
                  <Save size={16} />
                  {t('btn.save')}
                </button>
            </div>
        </div>

        {/* Content */}
        <div className="flex-1 bg-mc-panel border border-mc-border rounded-lg overflow-hidden flex flex-col">
            {jsonMode ? (
                <textarea 
                    className="w-full h-full bg-[#1e1e1e] text-green-400 font-mono p-4 text-sm resize-none focus:outline-none"
                    value={JSON.stringify(quest, null, 2)}
                    onChange={(e) => {
                        try {
                            setQuest(JSON.parse(e.target.value));
                        } catch(err) {
                            // ignore parse error
                        }
                    }}
                />
            ) : (
                <div className="p-6 space-y-8 overflow-y-auto">
                    {/* Basic Info */}
                    <div className="space-y-4">
                        <h3 className="text-lg font-medium text-white flex items-center gap-2 border-b border-mc-border pb-2">
                            Basic Information
                        </h3>
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-xs font-medium text-gray-400 mb-1">Quest ID (Read Only)</label>
                                <input 
                                    disabled 
                                    value={quest.id} 
                                    className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-gray-500 text-sm"
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-gray-400 mb-1">{t('label.type')}</label>
                                <select 
                                    value={quest.type}
                                    onChange={(e) => setQuest({...quest, type: e.target.value as QuestType})}
                                    className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-white text-sm focus:border-mc-accent focus:outline-none"
                                >
                                    <option value={QuestType.SINGLE}>Single Player</option>
                                    <option value={QuestType.MULTI}>Multiplayer</option>
                                    <option value={QuestType.SERIES}>Quest Chain</option>
                                </select>
                            </div>
                        </div>

                        {/* Multi-lang Name */}
                        <div>
                            <label className="block text-xs font-medium text-gray-400 mb-1">{t('label.name')} ({language})</label>
                            <input 
                                value={quest.name[language] || ''} 
                                onChange={(e) => updateLocalizedField('name', language, e.target.value)}
                                className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-white text-sm focus:border-mc-accent focus:outline-none"
                            />
                        </div>

                         {/* Multi-lang Description */}
                         <div>
                            <label className="block text-xs font-medium text-gray-400 mb-1">{t('label.desc')} ({language})</label>
                            <textarea 
                                value={quest.description[language] || ''} 
                                onChange={(e) => updateLocalizedField('description', language, e.target.value)}
                                rows={3}
                                className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-white text-sm focus:border-mc-accent focus:outline-none"
                            />
                        </div>
                    </div>

                    {/* Objectives */}
                    <div className="space-y-4">
                        <div className="flex justify-between items-center border-b border-mc-border pb-2">
                            <h3 className="text-lg font-medium text-white flex items-center gap-2">
                                <Target size={20} className="text-blue-400" />
                                {t('label.objectives')}
                            </h3>
                            <button onClick={addObjective} className="text-xs bg-blue-600/20 text-blue-400 px-2 py-1 rounded hover:bg-blue-600/40">+ Add Objective</button>
                        </div>
                        
                        <div className="space-y-3">
                            {quest.objectives.map((obj, idx) => (
                                <div key={obj.id} className="bg-mc-dark p-3 rounded border border-mc-border flex gap-3 items-start group">
                                    <div className="mt-2 text-xs text-gray-500 font-mono">#{idx + 1}</div>
                                    <div className="flex-1 grid grid-cols-3 gap-3">
                                        <div>
                                            <label className="text-[10px] text-gray-500 uppercase">Type</label>
                                            <input value={obj.type} onChange={(e) => {
                                                const newObjs = [...quest.objectives];
                                                newObjs[idx].type = e.target.value;
                                                setQuest({...quest, objectives: newObjs});
                                            }} className="w-full bg-mc-panel border border-mc-border rounded px-2 py-1 text-sm text-white" />
                                        </div>
                                        <div>
                                            <label className="text-[10px] text-gray-500 uppercase">Target</label>
                                            <input value={obj.target} onChange={(e) => {
                                                const newObjs = [...quest.objectives];
                                                newObjs[idx].target = e.target.value;
                                                setQuest({...quest, objectives: newObjs});
                                            }} className="w-full bg-mc-panel border border-mc-border rounded px-2 py-1 text-sm text-white" />
                                        </div>
                                        <div>
                                            <label className="text-[10px] text-gray-500 uppercase">Count</label>
                                            <input type="number" value={obj.count} onChange={(e) => {
                                                const newObjs = [...quest.objectives];
                                                newObjs[idx].count = parseInt(e.target.value);
                                                setQuest({...quest, objectives: newObjs});
                                            }} className="w-full bg-mc-panel border border-mc-border rounded px-2 py-1 text-sm text-white" />
                                        </div>
                                    </div>
                                    <button onClick={() => removeObjective(obj.id)} className="text-gray-600 hover:text-red-500 p-1">
                                        <Trash2 size={16} />
                                    </button>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Rewards */}
                    <div className="space-y-4">
                        <div className="flex justify-between items-center border-b border-mc-border pb-2">
                            <h3 className="text-lg font-medium text-white flex items-center gap-2">
                                <Gift size={20} className="text-yellow-500" />
                                {t('label.rewards')}
                            </h3>
                            <button className="text-xs bg-yellow-600/20 text-yellow-500 px-2 py-1 rounded hover:bg-yellow-600/40">+ Add Reward</button>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                             {quest.rewards.map((reward) => (
                                 <div key={reward.id} className="bg-mc-dark p-3 rounded border border-mc-border flex items-center justify-between">
                                     <div className="flex items-center gap-3">
                                         <div className="w-8 h-8 rounded bg-yellow-500/10 flex items-center justify-center text-yellow-500 font-bold text-xs">
                                             {reward.type === RewardType.XP ? 'XP' : '$'}
                                         </div>
                                         <div>
                                             <div className="text-sm font-medium text-gray-200">{reward.type.toUpperCase()}</div>
                                             <div className="text-xs text-gray-500">Value: {reward.value}</div>
                                         </div>
                                     </div>
                                 </div>
                             ))}
                        </div>
                    </div>
                </div>
            )}
        </div>
      </div>
    </div>
  );
};
