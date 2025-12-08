import React from 'react';
import { BookOpen, Library, Plus } from 'lucide-react';
import { Quest, QuestReward } from '../../types';

interface SidebarProps {
    quests: Quest[];
    rewardLibrary: QuestReward[];
    activeTab: 'quests' | 'rewards';
    activeId: string | null;
    onSwitch: (id: string | null, tab?: 'quests' | 'rewards') => void;
    onCreate: () => void;
}

/**
 * 侧边栏组件 (Sidebar Component)
 * 显示任务列表和奖励库列表，支持切换和创建新项。
 */
export const Sidebar: React.FC<SidebarProps> = ({
    quests,
    rewardLibrary,
    activeTab,
    activeId,
    onSwitch,
    onCreate
}) => {
    return (
        <div className="w-64 flex-shrink-0 bg-mc-panel border border-mc-border rounded-lg overflow-hidden flex flex-col">
            {/* 标签页切换 (Tabs) */}
            <div className="flex border-b border-mc-border">
                <button 
                    onClick={() => onSwitch(quests.length > 0 ? quests[0].id : null, 'quests')}
                    className={`flex-1 py-3 text-xs font-bold uppercase tracking-wide flex justify-center items-center gap-2 ${activeTab === 'quests' ? 'bg-mc-panel text-white border-b-2 border-mc-accent' : 'bg-[#252525] text-gray-500 hover:text-gray-300'}`}
                >
                    <BookOpen size={14} />
                    Quests
                </button>
                <button 
                    onClick={() => onSwitch(rewardLibrary.length > 0 ? rewardLibrary[0].id : null, 'rewards')}
                    className={`flex-1 py-3 text-xs font-bold uppercase tracking-wide flex justify-center items-center gap-2 ${activeTab === 'rewards' ? 'bg-mc-panel text-white border-b-2 border-yellow-500' : 'bg-[#252525] text-gray-500 hover:text-gray-300'}`}
                >
                    <Library size={14} />
                    Library
                </button>
            </div>

            {/* 操作栏 (Actions) */}
            <div className="p-3 border-b border-mc-border bg-mc-border/10">
                <button 
                    onClick={onCreate}
                    className={`w-full flex items-center justify-center gap-2 py-2 rounded text-sm font-medium transition-colors text-white ${activeTab === 'quests' ? 'bg-mc-accent hover:bg-green-700' : 'bg-yellow-600 hover:bg-yellow-700'}`}
                >
                    <Plus size={16} />
                    Create {activeTab === 'quests' ? 'Quest' : 'Reward'}
                </button>
            </div>

            {/* 列表项 (List Items) */}
            <div className="flex-1 overflow-y-auto p-2 space-y-1">
                {activeTab === 'quests' ? quests.map(q => (
                    <button
                        key={q.id}
                        onClick={() => onSwitch(q.id)}
                        className={`w-full text-left px-3 py-3 rounded text-sm transition-colors group ${
                            activeId === q.id 
                            ? 'bg-blue-600/20 text-blue-400 border border-blue-500/50' 
                            : 'text-gray-400 hover:bg-mc-border hover:text-gray-200'
                        }`}
                    >
                        <div className="font-medium truncate">{q.name || q.id}</div>
                        <div className="text-xs opacity-60 truncate font-mono">{q.id}</div>
                    </button>
                )) : rewardLibrary.map(r => (
                    <button
                        key={r.id}
                        onClick={() => onSwitch(r.id)}
                        className={`w-full text-left px-3 py-3 rounded text-sm transition-colors group ${
                            activeId === r.id 
                            ? 'bg-yellow-600/20 text-yellow-400 border border-yellow-500/50' 
                            : 'text-gray-400 hover:bg-mc-border hover:text-gray-200'
                        }`}
                    >
                        <div className="font-medium truncate">{r.name}</div>
                        <div className="flex items-center gap-2 text-xs opacity-60 mt-1">
                            <span className="uppercase text-[10px] border border-gray-600 px-1 rounded">{r.type}</span>
                            <span className="truncate">{r.value}</span>
                        </div>
                    </button>
                ))}
            </div>
        </div>
    );
};
