import {Quest} from '../types';
import {MOCK_QUESTS} from './mockData';

const API_BASE_URL = 'http://localhost:2222/api';

interface ApiResponse {
    code: number;
    msg: string;
    data: Quest[];
}

/**
 * Maps the backend Task Definition format to the frontend Quest format.
 */
const mapApiToQuest = (apiQuest: Quest): Quest => {
  // Clean up name if it contains extra quotes (e.g., "\"break\"")
  const cleanName = apiQuest.name ? apiQuest.name.replace(/^"|"$/g, '') : apiQuest.id;
  
  return {
    ...apiQuest,
    name: cleanName,
    objectives: Array.isArray(apiQuest.objectives) ? apiQuest.objectives.map((obj, index) => ({
        ...obj,
        // Generate ID for frontend if missing
        id: obj.id || `obj_${apiQuest.id}_${index}`,
        objectiveRewards: obj.objectiveRewards || []
    })) : [],
    questRewards: apiQuest.questRewards || []
  };
};

export const QuestService = {
  /**
   * Fetches all quest definitions.
   * Falls back to mock data if the API is unavailable.
   */
  async getAll(): Promise<Quest[]> {
    try {
      const response = await fetch(`${API_BASE_URL}/taskDefList`);
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const data: ApiResponse = await response.json();
      
      if (!data.data || !Array.isArray(data.data)) {
        console.warn('API returned unexpected format:', data);
        return MOCK_QUESTS;
      }

      return data.data.map(mapApiToQuest);
    } catch (error) {
      console.warn("API unavailable (using mock data):", error);
      return MOCK_QUESTS;
    }
  },

    /**
     * Saves a quest definition.
     * Uses /api/create for both creating and updating quests (upsert).
     */
    async save(quest: Quest): Promise<void> {
        try {
            const response = await fetch(`${API_BASE_URL}/create`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(quest),
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            // Log success but don't need to return data as the UI updates optimistically or re-fetches
            console.log('Quest saved successfully');
        } catch (error) {
            console.error("Failed to save quest:", error);
            throw error;
        }
    }
};
