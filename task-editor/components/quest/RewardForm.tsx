import React from 'react';
import { RewardType, QuestReward } from '../../types';

interface RewardFormProps {
    reward: QuestReward;
    onChange: (newReward: QuestReward) => void;
}

/**
 * 奖励库编辑表单组件 (Reward Library Editing Form Component)
 * 用于编辑奖励库中的奖励模板。
 */
export const RewardForm: React.FC<RewardFormProps> = ({ reward, onChange }) => {
    return (
        <div className="p-6 space-y-6">
            <div className="border-b border-mc-border pb-4 mb-4">
                <h2 className="text-xl font-bold text-white mb-1">Edit Reward Template</h2>
                <p className="text-sm text-gray-500">Configure reusable rewards for your library.</p>
            </div>

            <div className="space-y-4 max-w-lg">
                <div>
                    <label className="block text-xs font-medium text-gray-400 mb-1">Template Name</label>
                    <input
                        value={reward.name || ''}
                        onChange={(e) => onChange({ ...reward, name: e.target.value })}
                        className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-white text-sm focus:border-yellow-500 focus:outline-none"
                    />
                </div>
                <div>
                    <label className="block text-xs font-medium text-gray-400 mb-1">Reward Type</label>
                    <select
                        value={reward.type}
                        onChange={(e) => onChange({ ...reward, type: e.target.value as RewardType })}
                        className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-white text-sm focus:border-yellow-500 focus:outline-none"
                    >
                        <option value={RewardType.MONEY}>Money</option>
                        <option value={RewardType.ITEM}>Item</option>
                        <option value={RewardType.XP}>Experience</option>
                    </select>
                </div>
                <div>
                    <label className="block text-xs font-medium text-gray-400 mb-1">Value / Amount</label>
                    <input
                        type="number"
                        value={reward.value}
                        onChange={(e) => onChange({ ...reward, value: parseInt(e.target.value) || 0 })}
                        className="w-full bg-mc-dark border border-mc-border rounded px-3 py-2 text-white text-sm focus:border-yellow-500 focus:outline-none"
                    />
                </div>
            </div>
        </div>
    );
};
