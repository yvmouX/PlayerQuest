import React, { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { ScrollText, Users, BarChart3, Globe, Box } from 'lucide-react';
import { useLanguage } from '../context/LanguageContext';
import { Language } from '../types';

export const Layout: React.FC = () => {
  const { language, setLanguage, t } = useLanguage();
  const [isLangMenuOpen, setIsLangMenuOpen] = useState(false);

  const navClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 px-4 py-3 rounded-lg transition-colors duration-200 ${
      isActive
        ? 'bg-mc-accent text-white font-medium'
        : 'text-gray-400 hover:bg-mc-border hover:text-white'
    }`;

  return (
    <div className="flex h-screen bg-mc-dark">
      {/* Sidebar */}
      <aside className="w-64 bg-mc-panel border-r border-mc-border flex flex-col">
        <div className="p-6 flex items-center gap-3 border-b border-mc-border">
          <div className="bg-mc-accent p-2 rounded">
            <Box size={24} className="text-white" />
          </div>
          <h1 className="text-lg font-bold text-white tracking-wide">{t('app.title')}</h1>
        </div>

        <nav className="flex-1 p-4 space-y-2">
          <NavLink to="/stats" className={navClass}>
            <BarChart3 size={20} />
            <span>{t('nav.stats')}</span>
          </NavLink>
          <NavLink to="/" className={navClass}>
            <ScrollText size={20} />
            <span>{t('nav.quests')}</span>
          </NavLink>
          <NavLink to="/players" className={navClass}>
            <Users size={20} />
            <span>{t('nav.players')}</span>
          </NavLink>
        </nav>

        <div className="p-4 border-t border-mc-border">
          <div className="text-xs text-gray-500 mb-2 uppercase font-bold tracking-wider">Server Status</div>
          <div className="flex items-center gap-2 text-sm text-green-500">
            <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
            Connected
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Header */}
        <header className="h-16 bg-mc-panel border-b border-mc-border flex items-center justify-between px-6 shadow-sm">
          <div className="text-gray-400 text-sm">v2.4.0-SNAPSHOT</div>
          
          <div className="flex items-center gap-4">
            <div 
              className="relative"
              onMouseLeave={() => setIsLangMenuOpen(false)}
            >
              <button 
                onClick={() => setIsLangMenuOpen(!isLangMenuOpen)}
                className={`flex items-center gap-2 text-gray-300 hover:text-white px-3 py-2 rounded hover:bg-mc-border transition-colors ${isLangMenuOpen ? 'bg-mc-border text-white' : ''}`}
              >
                <Globe size={18} />
                <span className="text-sm font-medium">{language}</span>
              </button>
              
              {isLangMenuOpen && (
                <div className="absolute right-0 top-full pt-2 w-32 z-50">
                  <div className="bg-mc-panel border border-mc-border rounded shadow-xl overflow-hidden">
                    <button 
                      onClick={() => { setLanguage('en-US'); setIsLangMenuOpen(false); }} 
                      className="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-mc-border hover:text-white"
                    >
                      English
                    </button>
                    <button 
                      onClick={() => { setLanguage('zh-CN'); setIsLangMenuOpen(false); }} 
                      className="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-mc-border hover:text-white"
                    >
                      简体中文
                    </button>
                    <button 
                      onClick={() => { setLanguage('ja-JP'); setIsLangMenuOpen(false); }} 
                      className="w-full text-left px-4 py-2 text-sm text-gray-300 hover:bg-mc-border hover:text-white"
                    >
                      日本語
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Scrollable View Area */}
        <main className="flex-1 overflow-y-auto p-6 scroll-smooth">
          <Outlet />
        </main>
      </div>
    </div>
  );
};