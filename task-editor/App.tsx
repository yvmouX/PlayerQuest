import React from 'react';
import {HashRouter, Navigate, Route, Routes} from 'react-router-dom';
import {Layout} from './components/Layout';
import {QuestEditor} from './components/QuestEditor';
import {PlayerProgress} from './components/PlayerProgress';
import {Statistics} from './components/Statistics';
import {LanguageProvider} from './context/LanguageContext';

const App: React.FC = () => {
  return (
    <LanguageProvider>
      <HashRouter>
        <Routes>
          <Route path="/" element={<Layout />}>
            <Route index element={<QuestEditor />} />
            <Route path="players" element={<PlayerProgress />} />
            <Route path="stats" element={<Statistics />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </HashRouter>
    </LanguageProvider>
  );
};

export default App;
