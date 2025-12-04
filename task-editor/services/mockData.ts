import { Quest, PlayerProgress, QuestType, RewardType } from '../types';

export const MOCK_QUESTS: Quest[] = [
  {
    id: 'quest_001',
    name: { 'en-US': 'Zombie Slayer', 'zh-CN': '僵尸杀手', 'ja-JP': 'ゾンビスレイヤー' },
    description: { 'en-US': 'Kill 10 Zombies to protect the village.', 'zh-CN': '消灭10只僵尸以保卫村庄。', 'ja-JP': '村を守るためにゾンビを10体倒す。' },
    type: QuestType.SINGLE,
    objectives: [{ id: 'obj_1', type: 'kill_mob', target: 'zombie', count: 10 }],
    rewards: [{ id: 'rew_1', type: RewardType.MONEY, value: 100 }],
    createdAt: Date.now(),
    updatedAt: Date.now(),
  },
  {
    id: 'quest_002',
    name: { 'en-US': 'The Miner', 'zh-CN': '矿工', 'ja-JP': '鉱夫' },
    description: { 'en-US': 'Mine 64 Diamonds.', 'zh-CN': '开采64个钻石。', 'ja-JP': 'ダイヤモンドを64個採掘する。' },
    type: QuestType.SERIES,
    objectives: [{ id: 'obj_2', type: 'block_break', target: 'diamond_ore', count: 64 }],
    rewards: [{ id: 'rew_2', type: RewardType.XP, value: 500 }],
    createdAt: Date.now() - 100000,
    updatedAt: Date.now(),
  },
];

export const MOCK_PLAYERS: PlayerProgress[] = [
  {
    id: 'uuid-1',
    name: 'Steve',
    avatarUrl: 'https://picsum.photos/32/32',
    questId: 'quest_001',
    progress: 50,
    status: 'in_progress',
    lastActive: Date.now() - 3600000,
  },
  {
    id: 'uuid-2',
    name: 'Alex',
    avatarUrl: 'https://picsum.photos/32/32',
    questId: 'quest_001',
    progress: 100,
    status: 'completed',
    lastActive: Date.now() - 7200000,
  },
  {
    id: 'uuid-3',
    name: 'Herobrine',
    avatarUrl: 'https://picsum.photos/32/32',
    questId: 'quest_002',
    progress: 0,
    status: 'not_started',
    lastActive: Date.now() - 86400000,
  },
];
