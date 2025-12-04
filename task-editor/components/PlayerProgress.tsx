import React, { useState } from 'react';
import { Search, Filter, RefreshCw, CheckCircle, MoreHorizontal } from 'lucide-react';
import { MOCK_PLAYERS } from '../services/mockData';
import { useLanguage } from '../context/LanguageContext';

export const PlayerProgress: React.FC = () => {
  const { t } = useLanguage();
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');

  const toggleSelect = (id: string) => {
    const newSet = new Set(selectedIds);
    if (newSet.has(id)) newSet.delete(id);
    else newSet.add(id);
    setSelectedIds(newSet);
  };

  const getStatusColor = (status: string) => {
    switch(status) {
      case 'completed': return 'bg-green-500/10 text-green-500 border-green-500/20';
      case 'in_progress': return 'bg-blue-500/10 text-blue-500 border-blue-500/20';
      case 'claimed': return 'bg-purple-500/10 text-purple-500 border-purple-500/20';
      default: return 'bg-gray-500/10 text-gray-500 border-gray-500/20';
    }
  };

  return (
    <div className="space-y-6">
      {/* Header Actions */}
      <div className="flex flex-col md:flex-row justify-between gap-4">
        <div className="flex items-center gap-3">
            <h2 className="text-2xl font-bold text-white tracking-tight">{t('nav.players')}</h2>
            <span className="text-sm bg-mc-panel border border-mc-border px-2 py-0.5 rounded text-gray-400">Total: {MOCK_PLAYERS.length}</span>
        </div>
        <div className="flex gap-3">
             <div className="relative">
                <Search className="absolute left-3 top-2.5 text-gray-500" size={16} />
                <input 
                    type="text" 
                    placeholder="Search player or UUID..." 
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="pl-9 pr-4 py-2 bg-mc-panel border border-mc-border rounded text-sm text-white focus:outline-none focus:border-mc-accent w-64"
                />
            </div>
            <button className="flex items-center gap-2 px-3 py-2 bg-mc-panel border border-mc-border hover:bg-mc-border text-gray-300 rounded text-sm transition-colors">
                <Filter size={16} />
                Filter
            </button>
        </div>
      </div>

      {/* Batch Operations Bar */}
      {selectedIds.size > 0 && (
          <div className="bg-blue-600/10 border border-blue-600/30 p-3 rounded-lg flex items-center justify-between animate-in fade-in slide-in-from-top-2">
              <span className="text-sm text-blue-400 font-medium ml-2">{selectedIds.size} players selected</span>
              <div className="flex gap-2">
                  <button className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs font-medium hover:bg-blue-500">Reset Progress</button>
                  <button className="px-3 py-1.5 bg-green-600 text-white rounded text-xs font-medium hover:bg-green-500">Mark Completed</button>
              </div>
          </div>
      )}

      {/* Table */}
      <div className="bg-mc-panel border border-mc-border rounded-lg overflow-hidden">
        <table className="w-full text-left">
            <thead className="bg-[#252525] border-b border-mc-border text-xs uppercase text-gray-500 font-semibold tracking-wider">
                <tr>
                    <th className="p-4 w-12 text-center">
                        <input type="checkbox" className="rounded bg-mc-dark border-gray-600" />
                    </th>
                    <th className="p-4">Player</th>
                    <th className="p-4">Quest</th>
                    <th className="p-4">Progress</th>
                    <th className="p-4">Status</th>
                    <th className="p-4 text-right">Actions</th>
                </tr>
            </thead>
            <tbody className="divide-y divide-mc-border">
                {MOCK_PLAYERS.filter(p => p.name.toLowerCase().includes(searchTerm.toLowerCase())).map((player) => (
                    <tr key={player.id} className="hover:bg-white/5 transition-colors group">
                        <td className="p-4 text-center">
                            <input 
                                type="checkbox" 
                                checked={selectedIds.has(player.id)}
                                onChange={() => toggleSelect(player.id)}
                                className="rounded bg-mc-dark border-gray-600" 
                            />
                        </td>
                        <td className="p-4">
                            <div className="flex items-center gap-3">
                                <img src={player.avatarUrl} alt="" className="w-8 h-8 rounded bg-gray-700" />
                                <div>
                                    <div className="text-sm font-medium text-white">{player.name}</div>
                                    <div className="text-[10px] text-gray-500 font-mono">{player.id.substring(0,8)}...</div>
                                </div>
                            </div>
                        </td>
                        <td className="p-4">
                            <span className="text-sm text-gray-300">{player.questId}</span>
                        </td>
                        <td className="p-4">
                            <div className="flex items-center gap-2">
                                <div className="flex-1 w-24 h-1.5 bg-mc-dark rounded-full overflow-hidden">
                                    <div className="h-full bg-mc-accent transition-all duration-500" style={{width: `${player.progress}%`}} />
                                </div>
                                <span className="text-xs text-gray-400">{player.progress}%</span>
                            </div>
                        </td>
                        <td className="p-4">
                            <span className={`px-2 py-1 rounded text-xs border ${getStatusColor(player.status)}`}>
                                {t(`status.${player.status}`)}
                            </span>
                        </td>
                        <td className="p-4 text-right">
                             <button className="text-gray-500 hover:text-white p-1 rounded hover:bg-mc-border">
                                <MoreHorizontal size={16} />
                            </button>
                        </td>
                    </tr>
                ))}
            </tbody>
        </table>
      </div>
    </div>
  );
};
