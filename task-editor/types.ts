
export type Language = 'en-US' | 'zh-CN' | 'ja-JP';

export interface LocalizedString {
  [key: string]: string;
}

export interface QuestProgress {
    playerUuid: string; // UUID
    quest: Quest;
    status: 'not_started' | 'in_progress' | 'completed' | 'claimed';
}

export interface Quest {
    id: string;
    type: QuestType;
    name: string;
    description: string;
    objectives: QuestObjective[];
    questRewards?: QuestReward[]; // Made optional to match backend leniency, though backend might not return it yet
    createAt: string; // Renamed from createdAt
    updateAt: string; // Renamed from updatedAt
}

export interface QuestObjective {
    id?: string; // Made optional as backend doesn't send it, but frontend generates it
    action: string; // Renamed from actionType
    target: string;
    finished: boolean;
    currentAmount: number; // Renamed from currentProgress
    targetAmount: number; // Renamed from targetProgress
    objectiveRewards?: QuestReward[]; // Made optional
    createAt: string; // Renamed from createdAt
    updateAt: string; // Renamed from updatedAt
}

// A reusable template for rewards (now using QuestReward directly)
// export interface RewardTemplate { ... } - Removed in favor of QuestReward

// TODO: Add more fields to QuestReward 未完成
export interface QuestReward {
  id: string;
  name?: string; // Friendly name for the library or display
  templateId?: string; // Reference to origin template if applicable
  type: RewardType;
  value: string | number; // ID for items, amount for others
  meta?: any; // For NBT or extra data
}

export enum QuestType {
  CYCLE = 'CYCLE',
  FOREVER = 'FOREVER',
  LIMIT = 'LIMIT',
  NONE = 'NONE',
}

export enum RewardType {
  ITEM = 'item',
  XP = 'xp',
  MONEY = 'money',
  COMMAND = 'command',
}
