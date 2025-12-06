import React, { useState } from 'react';
import { QuestReward } from '../../types';

export const LibraryDropdown: React.FC<{
  library: QuestReward[];
  onSelect: (template: QuestReward) => void;
  buttonClassName?: string;
}> = ({ library, onSelect, buttonClassName }) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="relative" onMouseLeave={() => setIsOpen(false)}>
      <button 
        onClick={() => setIsOpen(!isOpen)}
        className={buttonClassName || "text-[10px] bg-yellow-600/20 text-yellow-500 border border-yellow-600/30 px-2 py-0.5 rounded hover:bg-yellow-600/40 transition-colors"}
      >
        + From Library
      </button>
      {isOpen && (
        <div className="absolute right-0 top-full pt-1 w-48 z-50">
          <div className="bg-mc-panel border border-mc-border shadow-xl rounded max-h-48 overflow-y-auto">
            {library.map(lib => (
              <button 
                key={lib.id}
                onClick={() => {
                  onSelect(lib);
                  setIsOpen(false);
                }}
                className="w-full text-left px-3 py-2 text-xs hover:bg-mc-border text-gray-300 hover:text-white border-b border-mc-border/20 last:border-0"
              >
                <div className="font-medium truncate">{lib.name}</div>
                <div className="text-[10px] text-gray-500 flex justify-between">
                    <span>{lib.type}</span>
                    <span>{lib.value}</span>
                </div>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
