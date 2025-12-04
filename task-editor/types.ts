export type Language = 'en-US' | 'zh-CN' | 'ja-JP';

export interface LocalizedString {
  [key: string]: string;
}

export enum QuestType {
  SINGLE = 'single',
  MULTI = 'multi',
  SERIES = 'series',
}

export enum RewardType {
  ITEM = 'item',
  XP = 'xp',
  MONEY = 'money',
  COMMAND = 'command',
}

export interface QuestReward {
  id: string;
  type: RewardType;
  value: string | number; // ID for items, amount for others
  meta?: any; // For NBT or extra data
}

export interface QuestObjective {
  id: string;
  type: string; // e.g., 'kill_mob', 'reach_location'
  target: string;
  count: number;
}

export interface Quest {
  id: string;
  name: LocalizedString;
  description: LocalizedString;
  type: QuestType;
  objectives: QuestObjective[];
  rewards: QuestReward[];
  createdAt: number;
  updatedAt: number;
}

export interface PlayerProgress {
  id: string; // UUID
  name: string;
  avatarUrl: string;
  questId: string;
  progress: number; // 0-100
  status: 'not_started' | 'in_progress' | 'completed' | 'claimed';
  lastActive: number;
}

export interface StatData {
  name: string;
  value: number;
}
