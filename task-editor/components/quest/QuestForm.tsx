import React from 'react';
import {CheckCircle2, Circle, Gift, Target, Trash2} from 'lucide-react';
import {Quest, QuestReward, QuestType, RewardType} from '../../types';
import {LibraryDropdown} from '../helper/LibraryDropdown';
import {useLanguage} from '../../context/LanguageContext';

interface QuestFormProps {
    quest: Quest;
    onChange: (newQuest: Quest) => void;
    rewardLibrary: QuestReward[];
}

/**
 * 任务编辑表单组件 (Quest Editing Form Component)
 * 包含基本信息、目标列表和奖励列表的编辑功能。
 */
export const QuestForm: React.FC<QuestFormProps> = ({ quest, onChange, rewardLibrary }) => {
    const { t } = useLanguage();

    // --- 内部逻辑 (Internal Logic) ---

    const addObjective = () => {
        onChange({
            ...quest,
            objectives: [...quest.objectives, {
                id: `obj_${Date.now()}`,
                action: 'block_break',
                target: 'stone',
                targetAmount: 1,
                currentAmount: 0,
                finished: false,
                objectiveRewards: [],
                createAt: new Date().toISOString(),
                updateAt: new Date().toISOString()
            }]
        });
    };

    const removeObjective = (id: string) => {
        onChange({
            ...quest,
            objectives: quest.objectives.filter(o => o.id !== id)
        });
    };

    const addReward = (objectiveId?: string, template?: QuestReward) => {
        const newReward: QuestReward = template ? {
            id: `rew_${Date.now()}`,
            templateId: template.id,
            type: template.type,
            value: template.value
        } : {
            id: `rew_${Date.now()}`,
            type: RewardType.MONEY,
            value: 100
        };

        if (objectiveId) {
            // 添加到特定目标 (Add to objective)
            const newObjectives = quest.objectives.map(obj => {
                if (obj.id === objectiveId) {
                    return { ...obj, objectiveRewards: [...(obj.objectiveRewards || []), newReward] };
                }
                return obj;
            });
            onChange({ ...quest, objectives: newObjectives });
        } else {
            // 添加到全局奖励 (Add to global rewards)
            onChange({ ...quest, questRewards: [...quest.questRewards, newReward] });
        }
    };

    const removeReward = (rewardId: string, objectiveId?: string) => {
        if (objectiveId) {
            const newObjectives = quest.objectives.map(obj => {
                if (obj.id === objectiveId) {
                    return { ...obj, objectiveRewards: (obj.objectiveRewards || []).filter(r => r.id !== rewardId) };
                }
                return obj;
            });
            onChange({ ...quest, objectives: newObjectives });
        } else {
            onChange({ ...quest, questRewards: quest.questRewards.filter(r => r.id !== rewardId) });
        }
    };

    return (
        <div className="p-6 space-y-8 overflow-y-auto">
            {/* 基本信息 (Basic Info) */}
            <div className="space-y-4">
                <h3 className="text-lg font-medium text-white flex items-center gap-2 border-b border-mc-border pb-2">Basic Info</h3>
                <div className="grid grid-cols-2 gap-4">
                    <div>
                        <label className="block text-xs font-medium text-gray-400 mb-1">Quest ID</label>
                        <input disabled value={quest.id} className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-gray-500 text-sm" />
                    </div>
                    <div>
                        <label className="block text-xs font-medium text-gray-400 mb-1">{t('label.type')}</label>
                        <select
                            value={quest.type}
                            onChange={(e) => onChange({ ...quest, type: e.target.value as QuestType })}
                            className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-white text-sm focus:border-mc-accent focus:outline-none"
                        >
                            <option value={QuestType.LIMIT}>Limit</option>
                            <option value={QuestType.CYCLE}>Cycle</option>
                            <option value={QuestType.FOREVER}>Forever</option>
                            <option value={QuestType.NONE}>None</option>
                        </select>
                    </div>
                </div>
                <div>
                    <label className="block text-xs font-medium text-gray-400 mb-1">{t('label.name')}</label>
                    <input
                        value={quest.name}
                        onChange={(e) => onChange({ ...quest, name: e.target.value })}
                        className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-white text-sm focus:border-mc-accent focus:outline-none"
                    />
                </div>
                <div>
                    <label className="block text-xs font-medium text-gray-400 mb-1">Description</label>
                    <textarea
                        value={quest.description}
                        onChange={(e) => onChange({ ...quest, description: e.target.value })}
                        className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-white text-sm focus:border-mc-accent focus:outline-none resize-none h-20"
                    />
                </div>
            </div>

            {/* 目标列表 (Objectives) */}
            <div className="space-y-4">
                <div className="flex justify-between items-center border-b border-mc-border pb-2">
                    <h3 className="text-lg font-medium text-white flex items-center gap-2">
                        <Target size={20} className="text-blue-400" />
                        {t('label.objectives')}
                    </h3>
                    <button onClick={addObjective} className="text-xs bg-blue-600/20 text-blue-400 px-2 py-1 rounded hover:bg-blue-600/40">+ Add Objective</button>
                </div>

                <div className="space-y-4">
                    {quest.objectives.map((obj, idx) => (
                        <div key={obj.id} className="bg-mc-dark p-4 rounded border border-mc-border group relative">
                            <div className="absolute top-4 left-4 text-xs font-mono text-gray-600">#{idx + 1}</div>
                            <div className="ml-8 space-y-4">
                                {/* 目标定义行 (Definition Row) */}
                                <div className="grid grid-cols-12 gap-3 items-end">
                                    <div className="col-span-3">
                                        <label className="text-[10px] text-gray-500 uppercase">Action</label>
                                        <input
                                            value={obj.action}
                                            onChange={(e) => {
                                                const newObjs = [...quest.objectives];
                                                newObjs[idx].action = e.target.value;
                                                onChange({ ...quest, objectives: newObjs });
                                            }}
                                            className="w-full bg-mc-panel border border-mc-border rounded px-2 py-1 text-sm text-white"
                                        />
                                    </div>
                                    <div className="col-span-4">
                                        <label className="text-[10px] text-gray-500 uppercase">Target</label>
                                        <input
                                            value={obj.target}
                                            onChange={(e) => {
                                                const newObjs = [...quest.objectives];
                                                newObjs[idx].target = e.target.value;
                                                onChange({ ...quest, objectives: newObjs });
                                            }}
                                            className="w-full bg-mc-panel border border-mc-border rounded px-2 py-1 text-sm text-white"
                                        />
                                    </div>
                                    <div className="col-span-2">
                                        <label className="text-[10px] text-gray-500 uppercase">Target Amount</label>
                                        <input
                                            type="number"
                                            value={obj.targetAmount}
                                            onChange={(e) => {
                                                const newObjs = [...quest.objectives];
                                                newObjs[idx].targetAmount = parseInt(e.target.value);
                                                onChange({ ...quest, objectives: newObjs });
                                            }}
                                            className="w-full bg-mc-panel border border-mc-border rounded px-2 py-1 text-sm text-white"
                                        />
                                    </div>
                                    <div className="col-span-3 flex justify-end items-center pb-1 gap-2 pr-12">
                                        {/* 状态指示器 (Status Indicator) */}
                                        <div className={`flex items-center gap-1 text-xs px-2 py-1 rounded border ${obj.finished ? 'border-green-800 bg-green-900/20 text-green-500' : 'border-gray-700 bg-gray-800 text-gray-500'}`}>
                                            {obj.finished ? <CheckCircle2 size={12} /> : <Circle size={12} />}
                                            {obj.finished ? 'Completed' : 'Active'}
                                        </div>
                                    </div>
                                </div>

                                {/* 目标奖励部分 (Sub-Rewards Section) */}
                                <div className="bg-mc-panel/50 rounded p-3 border border-mc-border/50">
                                    <div className="flex justify-between items-center mb-2">
                                        <span className="text-xs font-bold text-gray-500 uppercase flex items-center gap-1">
                                            <Gift size={12} /> Objective Rewards
                                        </span>
                                        <div className="flex gap-2">
                                            {/* 添加自定义奖励 */}
                                            <button onClick={() => addReward(obj.id)} className="text-[10px] bg-mc-border px-2 py-0.5 rounded text-gray-300 hover:bg-gray-600">
                                                + Custom
                                            </button>
                                            {/* 从库中添加奖励 */}
                                            <LibraryDropdown
                                                library={rewardLibrary}
                                                onSelect={(t) => addReward(obj.id, t)}
                                            />
                                        </div>
                                    </div>

                                    <div className="space-y-1">
                                        {obj.objectiveRewards && obj.objectiveRewards.length > 0 ? obj.objectiveRewards.map(r => (
                                            <div key={r.id} className="flex items-center justify-between text-xs bg-mc-dark px-2 py-1.5 rounded border border-mc-border/50">
                                                <div className="flex gap-2">
                                                    <span className="text-yellow-500 font-bold">{r.type}</span>
                                                    <span className="text-gray-300">{r.value}</span>
                                                    {r.templateId && <span className="text-[10px] text-gray-600 italic border border-gray-700 px-1 rounded ml-1">Linked</span>}
                                                </div>
                                                <button onClick={() => removeReward(r.id, obj.id)} className="text-gray-600 hover:text-red-500"><Trash2 size={12} /></button>
                                            </div>
                                        )) : (
                                            <div className="text-[10px] text-gray-600 italic">No specific rewards for this objective.</div>
                                        )}
                                    </div>
                                </div>
                            </div>
                            <button onClick={() => removeObjective(obj.id)} className="absolute top-4 right-4 text-gray-600 hover:text-red-500 p-1">
                                <Trash2 size={16} />
                            </button>
                        </div>
                    ))}
                </div>
            </div>

            {/* 全局奖励 (Global Rewards) */}
            <div className="space-y-4">
                <div className="flex justify-between items-center border-b border-mc-border pb-2">
                    <h3 className="text-lg font-medium text-white flex items-center gap-2">
                        <Gift size={20} className="text-yellow-500" />
                        Global Rewards
                    </h3>
                    <div className="flex gap-2">
                        {/* 添加自定义奖励 */}
                        <button onClick={() => addReward()} className="text-xs bg-mc-border px-2 py-1 rounded text-gray-300 hover:bg-gray-600">
                            + Custom
                        </button>
                        {/* 从库中添加奖励 */}
                        <LibraryDropdown
                            library={rewardLibrary}
                            onSelect={(t) => addReward(undefined, t)}
                        />
                    </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                    {quest.questRewards && quest.questRewards.length > 0 ? (
                        quest.questRewards.map(reward => (
                            <div key={reward.id} className="bg-mc-dark p-3 rounded border border-mc-border flex justify-between items-center relative group">
                                <div className="flex items-center gap-3">
                                    <div className="w-8 h-8 bg-mc-panel rounded flex items-center justify-center text-yellow-500">
                                        <Gift size={16} />
                                    </div>
                                    <div>
                                        <div className="text-xs font-bold text-white uppercase flex items-center gap-2">
                                            {reward.type}
                                            {reward.templateId && <span className="text-[9px] font-normal text-gray-500 border border-gray-700 px-1 rounded">Linked</span>}
                                        </div>
                                        <div className="text-xs text-gray-500">Value: {reward.value}</div>
                                    </div>
                                </div>
                                <button onClick={() => removeReward(reward.id)} className="text-gray-600 hover:text-red-500 p-1">
                                    <Trash2 size={16} />
                                </button>
                            </div>
                        ))
                    ) : (
                        <div className="col-span-2 text-center text-gray-500 text-sm py-4 italic">No global rewards configured</div>
                    )}
                </div>
            </div>
        </div>
    );
};
