import React from 'react';
import {
    Bar,
    BarChart,
    Cell,
    Line,
    LineChart,
    Pie,
    PieChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis
} from 'recharts';
import {useLanguage} from '../context/LanguageContext';

const COLORS = ['#3b8526', '#EAB308', '#3B82F6', '#EF4444'];

const DATA_COMPLETION = [
  { name: 'Completed', value: 400 },
  { name: 'In Progress', value: 300 },
  { name: 'Not Started', value: 300 },
  { name: 'Abandoned', value: 200 },
];

const DATA_ACTIVITY = [
  { day: 'Mon', users: 40 },
  { day: 'Tue', users: 30 },
  { day: 'Wed', users: 20 },
  { day: 'Thu', users: 27 },
  { day: 'Fri', users: 18 },
  { day: 'Sat', users: 23 },
  { day: 'Sun', users: 34 },
];

const Card: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => (
  <div className="bg-mc-panel border border-mc-border rounded-lg p-6 flex flex-col h-80">
    <h3 className="text-lg font-medium text-white mb-6 tracking-wide">{title}</h3>
    <div className="flex-1 min-h-0">
      {children}
    </div>
  </div>
);

export const Statistics: React.FC = () => {
  const { t } = useLanguage();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
         <h2 className="text-2xl font-bold text-white tracking-tight">{t('nav.stats')}</h2>
         <div className="flex gap-2 bg-mc-panel p-1 rounded border border-mc-border">
            <button className="px-3 py-1 text-xs bg-mc-border text-white rounded">Last 7 Days</button>
            <button className="px-3 py-1 text-xs text-gray-400 hover:text-white">Last 30 Days</button>
         </div>
      </div>
      
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card title="Quest Completion Status">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={DATA_COMPLETION}
                cx="50%"
                cy="50%"
                innerRadius={60}
                outerRadius={80}
                paddingAngle={5}
                dataKey="value"
                stroke="none"
              >
                {DATA_COMPLETION.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip 
                contentStyle={{ backgroundColor: '#1d1d1d', border: '1px solid #4a4a4a', borderRadius: '4px' }}
                itemStyle={{ color: '#fff' }}
              />
            </PieChart>
          </ResponsiveContainer>
        </Card>

        <Card title="Player Activity (Last 7 Days)">
           <ResponsiveContainer width="100%" height="100%">
            <LineChart data={DATA_ACTIVITY}>
              <XAxis dataKey="day" stroke="#6b7280" fontSize={12} tickLine={false} axisLine={false} />
              <YAxis stroke="#6b7280" fontSize={12} tickLine={false} axisLine={false} />
              <Tooltip 
                 contentStyle={{ backgroundColor: '#1d1d1d', border: '1px solid #4a4a4a', borderRadius: '4px' }}
                 itemStyle={{ color: '#fff' }}
              />
              <Line type="monotone" dataKey="users" stroke="#3b8526" strokeWidth={3} dot={{fill: '#3b8526'}} />
            </LineChart>
          </ResponsiveContainer>
        </Card>

        <Card title="Rewards Distributed">
             <ResponsiveContainer width="100%" height="100%">
                <BarChart data={[
                    { name: 'XP', amount: 12000 },
                    { name: '$', amount: 50000 },
                    { name: 'Items', amount: 3400 },
                ]}>
                    <XAxis dataKey="name" stroke="#6b7280" fontSize={12} tickLine={false} axisLine={false} />
                    <YAxis stroke="#6b7280" fontSize={12} tickLine={false} axisLine={false} />
                    <Tooltip cursor={{fill: '#374151'}} contentStyle={{ backgroundColor: '#1d1d1d', border: '1px solid #4a4a4a' }} />
                    <Bar dataKey="amount" fill="#EAB308" radius={[4, 4, 0, 0]} />
                </BarChart>
             </ResponsiveContainer>
        </Card>

        {/* Leaderboard */}
        <div className="bg-mc-panel border border-mc-border rounded-lg p-6 h-80 overflow-y-auto">
             <h3 className="text-lg font-medium text-white mb-4 tracking-wide">Top Quests</h3>
             <div className="space-y-3">
                 {[1,2,3,4,5].map(i => (
                     <div key={i} className="flex items-center justify-between p-3 bg-mc-dark rounded border border-mc-border">
                         <div className="flex items-center gap-3">
                             <div className={`w-6 h-6 rounded flex items-center justify-center text-xs font-bold ${i === 1 ? 'bg-yellow-500 text-black' : 'bg-gray-700 text-gray-300'}`}>
                                 {i}
                             </div>
                             <span className="text-sm text-gray-200">The Zombie Slayer {i}</span>
                         </div>
                         <span className="text-xs text-gray-500">{1000 - (i * 50)} completions</span>
                     </div>
                 ))}
             </div>
        </div>
      </div>
    </div>
  );
};