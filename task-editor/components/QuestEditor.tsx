import React, { useState, useEffect } from 'react';
import { Save, RotateCcw, Code, Loader2 } from 'lucide-react';
import { Quest, QuestType, RewardType, QuestReward } from '../types';
import { QuestService } from '../services/api';
import { MOCK_REWARD_LIBRARY } from '../services/mockData';
import { useLanguage } from '../context/LanguageContext';
import { Sidebar } from './quest/Sidebar';
import { QuestForm } from './quest/QuestForm';
import { RewardForm } from './quest/RewardForm';

/**
 * 任务编辑器主组件 (Main Quest Editor Component)
 * 管理整个编辑器的状态，包括任务列表、奖励库、当前选中的项以及保存/加载逻辑。
 */
export const QuestEditor: React.FC = () => {
  const { t } = useLanguage();
  
  // 数据状态 (Data State)
  const [quests, setQuests] = useState<Quest[]>([]);
  const [rewardLibrary, setRewardLibrary] = useState<QuestReward[]>(MOCK_REWARD_LIBRARY);
  
  // UI 状态 (UI State)
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'quests' | 'rewards'>('quests');
  const [activeId, setActiveId] = useState<string | null>(null);
  
  // 编辑状态 (Editing State)
  const [quest, setQuest] = useState<Quest | null>(null);
  const [rewardTemplate, setRewardTemplate] = useState<QuestReward | null>(null);
  
  const [jsonMode, setJsonMode] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isDirty, setIsDirty] = useState(false);

  // 脏检查副作用 (Dirty Check Effect)
  useEffect(() => {
    if (activeTab === 'quests') {
        if (!quest) {
            setIsDirty(false);
            return;
        }
        const original = quests.find(q => q.id === quest.id);
        if (original) {
            setIsDirty(JSON.stringify(quest) !== JSON.stringify(original));
        }
    } else {
        if (!rewardTemplate) {
            setIsDirty(false);
            return;
        }
        const original = rewardLibrary.find(r => r.id === rewardTemplate.id);
        if (original) {
            setIsDirty(JSON.stringify(rewardTemplate) !== JSON.stringify(original));
        }
    }
  }, [quest, rewardTemplate, quests, rewardLibrary, activeTab]);

  // 获取任务列表 (Fetch Quests)
  useEffect(() => {
    const fetchQuests = async () => {
      setLoading(true);
      try {
        const data = await QuestService.getAll();
        setQuests(data);
        if (data.length > 0) setActiveId(data[0].id);
      } catch (error) {
        console.error("Critical error loading quests:", error);
      } finally {
        setLoading(false);
      }
    };
    fetchQuests();
  }, []);

  // 同步编辑器选择 (Sync Editor Selection)
  useEffect(() => {
    if (!activeId) return;
    
    if (activeTab === 'quests') {
      const found = quests.find(q => q.id === activeId);
      if (found) {
        setQuest({ ...found });
        setRewardTemplate(null);
      }
    } else {
      const found = rewardLibrary.find(r => r.id === activeId);
      if (found) {
        setRewardTemplate({ ...found });
        setQuest(null);
      }
    }
  }, [activeId, activeTab, quests, rewardLibrary]);

  // --- 操作 (Actions) ---

  const handleSave = async () => {
    setIsSaving(true);
    await new Promise(r => setTimeout(r, 600)); // 模拟延迟
    
    if (activeTab === 'quests' && quest) {
        setQuests(prev => prev.map(q => q.id === quest.id ? quest : q));
        await QuestService.save(quest);
    } else if (activeTab === 'rewards' && rewardTemplate) {
        setRewardLibrary(prev => prev.map(r => r.id === rewardTemplate.id ? rewardTemplate : r));
    }
    
    setIsSaving(false);
    alert('Saved successfully!');
  };

  const handleSwitch = async (newId: string | null, newTab: 'quests' | 'rewards' = activeTab) => {
    if (isDirty) {
        if (window.confirm("You have unsaved changes.\n\nClick OK to Save & Switch.\nClick Cancel to Stay on current page.")) {
            await handleSave();
        } else {
            return; // 取消切换
        }
    }
    
    if (newTab !== activeTab) {
        setActiveTab(newTab);
        if (newId === null) {
             if (newTab === 'quests') {
                 setActiveId(quests.length > 0 ? quests[0].id : null);
             } else {
                 setActiveId(rewardLibrary.length > 0 ? rewardLibrary[0].id : null);
             }
             return;
        }
    }
    setActiveId(newId);
  };

  const handleCreateNew = async () => {
    if (isDirty) {
        if (window.confirm("You have unsaved changes.\n\nClick OK to Save & Create New.\nClick Cancel to Stay on current page.")) {
            await handleSave();
        } else {
            return; // 取消创建
        }
    }
    createNew();
  };

  const createNew = () => {
    if (activeTab === 'quests') {
        const newQuest: Quest = {
            id: `quest_${Date.now()}`,
            name: 'New Quest',
            description: '',
            type: QuestType.LIMIT,
            objectives: [],
            questRewards: [],
            createAt: new Date().toISOString(),
            updateAt: new Date().toISOString()
        };
        setQuests(prev => [...prev, newQuest]);
        setActiveId(newQuest.id);
    } else {
        const newReward: QuestReward = {
            id: `lib_${Date.now()}`,
            name: 'New Reward',
            type: RewardType.MONEY,
            value: 100
        };
        setRewardLibrary(prev => [...prev, newReward]);
        setActiveId(newReward.id);
    }
  };

  // --- 渲染 (Render) ---

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full text-gray-400 gap-2">
        <Loader2 className="animate-spin" />
        <span>Loading...</span>
      </div>
    );
  }

  return (
    <div className="flex gap-6 h-full">
      {/* 侧边栏 (Sidebar) */}
      <Sidebar 
        quests={quests}
        rewardLibrary={rewardLibrary}
        activeTab={activeTab}
        activeId={activeId}
        onSwitch={handleSwitch}
        onCreate={handleCreateNew}
      />

      {/* 主编辑区域 (Main Editor Area) */}
      <div className="flex-1 flex flex-col gap-4">
        {/* 工具栏 (Toolbar) */}
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
            <div className="flex gap-2 items-center">
                {isDirty && (
                    <span className="text-xs text-yellow-500 font-bold bg-yellow-900/30 px-2 py-1 rounded border border-yellow-700/50 flex items-center gap-1">
                        Modified
                    </span>
                )}
                <button 
                    onClick={() => {
                        // 重置逻辑 (Reset Logic)
                        if(activeTab === 'quests') {
                            const original = quests.find(q => q.id === activeId);
                            if (original) setQuest({...original});
                        } else {
                            const original = rewardLibrary.find(r => r.id === activeId);
                            if (original) setRewardTemplate({...original});
                        }
                    }}
                    className="flex items-center gap-2 px-4 py-2 bg-mc-border hover:bg-gray-600 text-white rounded text-sm font-medium transition-colors"
                >
                    <RotateCcw size={16} />
                    {t('btn.reset')}
                </button>
                <button 
                    onClick={handleSave}
                    disabled={isSaving}
                    className="flex items-center gap-2 px-4 py-2 bg-mc-accent hover:bg-green-700 text-white rounded text-sm font-medium transition-colors shadow-lg shadow-green-900/20 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                    {isSaving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
                    {t('btn.save')}
                </button>
            </div>
        </div>

        {/* 编辑器内容 (Editors) */}
        <div className="flex-1 bg-mc-panel border border-mc-border rounded-lg overflow-hidden flex flex-col relative">
            {jsonMode ? (
                 <textarea 
                    className="w-full h-full bg-[#1e1e1e] text-green-400 font-mono p-4 text-sm resize-none focus:outline-none"
                    value={JSON.stringify(activeTab === 'quests' ? quest : rewardTemplate, null, 2)}
                    readOnly // 简单只读模式
                />
            ) : (
                <>
                 {activeTab === 'quests' && quest && (
                    <QuestForm 
                        quest={quest} 
                        onChange={setQuest} 
                        rewardLibrary={rewardLibrary} 
                    />
                 )}

                 {activeTab === 'rewards' && rewardTemplate && (
                    <RewardForm 
                        reward={rewardTemplate} 
                        onChange={setRewardTemplate} 
                    />
                 )}
                </>
            )}
            
            {/* 空状态提示 (Empty State) */}
            {((activeTab === 'quests' && !quest) || (activeTab === 'rewards' && !rewardTemplate)) && !jsonMode && (
                <div className="flex items-center justify-center h-full text-gray-500 italic">
                    Select an item to edit
                </div>
            )}
        </div>
      </div>
    </div>
  );
};
