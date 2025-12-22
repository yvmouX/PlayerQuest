import {Quest, QuestReward, QuestType, RewardType} from '../types';

export const MOCK_REWARD_LIBRARY: QuestReward[] = [
  { id: 'lib_1', name: '100 Gold Coins', type: RewardType.MONEY, value: 100 },
  { id: 'lib_2', name: 'Diamond Sword', type: RewardType.ITEM, value: 'diamond_sword' },
  { id: 'lib_3', name: 'Level Up (500 XP)', type: RewardType.XP, value: 500 },
  { id: 'lib_4', name: 'Starter Kit', type: RewardType.COMMAND, value: 'kit starter' },
];

export const MOCK_QUESTS: Quest[] = [
  {
    id: 'quest_001',
    name: 'Zombie Slayer',
    description: 'Kill 10 Zombies to protect the village.',
    type: QuestType.LIMIT,
    objectives: [
      { 
        id: 'obj_1', 
        action: 'kill_mob', 
        target: 'zombie', 
        targetAmount: 10,
        currentAmount: 0,
        finished: false,
        objectiveRewards: [
          { id: 'r_o1', type: RewardType.MONEY, value: 10 }
        ],
        createAt: new Date().toISOString(),
        updateAt: new Date().toISOString()
      }
    ],
    questRewards: [{ id: 'rew_1', type: RewardType.MONEY, value: 100 }],
    createAt: new Date().toISOString(),
    updateAt: new Date().toISOString(),
  },
  {
    id: 'quest_002',
    name: 'The Miner',
    description: 'Mine 64 Diamonds.',
    type: QuestType.CYCLE,
    objectives: [
      { 
        id: 'obj_2', 
        action: 'block_break', 
        target: 'diamond_ore', 
        targetAmount: 64,
        currentAmount: 12,
        finished: false,
        objectiveRewards: [],
        createAt: new Date().toISOString(),
        updateAt: new Date().toISOString()
      }
    ],
    questRewards: [
      { id: 'rew_2', type: RewardType.XP, value: 1000 },
      { id: 'rew_3', type: RewardType.ITEM, value: 'diamond_pickaxe' }
    ],
    createAt: new Date().toISOString(),
    updateAt: new Date().toISOString(),
  }
];

export const MOCK_PLAYERS = [
    { id: 'p_1', name: 'Steve', avatarUrl: 'https://mc-heads.net/avatar/Steve', questId: 'quest_001', progress: 45, status: 'in_progress', lastActive: Date.now() },
    { id: 'p_2', name: 'Alex', avatarUrl: 'https://mc-heads.net/avatar/Alex', questId: 'quest_002', progress: 100, status: 'completed', lastActive: Date.now() - 3600000 },
    { id: 'p_3', name: 'Notch', avatarUrl: 'https://mc-heads.net/avatar/Notch', questId: 'quest_001', progress: 0, status: 'not_started', lastActive: Date.now() - 86400000 },
];
