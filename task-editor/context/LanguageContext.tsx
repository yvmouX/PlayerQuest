import React, {createContext, useContext, useState} from 'react';
import {Language} from '../types';

const translations = {
  'en-US': {
    'app.title': 'Quest Editor',
    'nav.quests': 'Quests',
    'nav.players': 'Players',
    'nav.stats': 'Statistics',
    'btn.save': 'Save Changes',
    'btn.reset': 'Reset',
    'btn.create': 'Create Quest',
    'label.name': 'Quest Name',
    'label.desc': 'Description',
    'label.type': 'Quest Type',
    'label.objectives': 'Objectives',
    'label.rewards': 'Rewards',
    'status.in_progress': 'In Progress',
    'status.completed': 'Completed',
    'status.claimed': 'Claimed',
    'status.not_started': 'Not Started',
  },
  'zh-CN': {
    'app.title': '任务编辑器',
    'nav.quests': '任务列表',
    'nav.players': '玩家管理',
    'nav.stats': '数据统计',
    'btn.save': '保存更改',
    'btn.reset': '重置',
    'btn.create': '新建任务',
    'label.name': '任务名称',
    'label.desc': '任务描述',
    'label.type': '任务类型',
    'label.objectives': '目标条件',
    'label.rewards': '奖励配置',
    'status.in_progress': '进行中',
    'status.completed': '已完成',
    'status.claimed': '已领取',
    'status.not_started': '未开始',
  },
  'ja-JP': {
    'app.title': 'クエストエディタ',
    'nav.quests': 'クエスト一覧',
    'nav.players': 'プレイヤー管理',
    'nav.stats': '統計データ',
    'btn.save': '変更を保存',
    'btn.reset': 'リセット',
    'btn.create': 'クエスト作成',
    'label.name': 'クエスト名',
    'label.desc': '説明',
    'label.type': 'タイプ',
    'label.objectives': '目的',
    'label.rewards': '報酬',
    'status.in_progress': '進行中',
    'status.completed': '完了',
    'status.claimed': '受取済',
    'status.not_started': '未開始',
  }
};

interface LanguageContextType {
  language: Language;
  setLanguage: (lang: Language) => void;
  t: (key: string) => string;
}

const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

export const LanguageProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [language, setLanguage] = useState<Language>('zh-CN');

  const t = (key: string) => {
    // @ts-ignore
    return translations[language][key] || key;
  };

  return (
    <LanguageContext.Provider value={{ language, setLanguage, t }}>
      {children}
    </LanguageContext.Provider>
  );
};

export const useLanguage = () => {
  const context = useContext(LanguageContext);
  if (!context) {
    throw new Error('useLanguage must be used within a LanguageProvider');
  }
  return context;
};
