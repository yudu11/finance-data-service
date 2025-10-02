import axios from 'axios';
import type { PriceData } from './types';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
});

export async function fetchSymbols(): Promise<string[]> {
  const response = await api.get<string[]>('/symbols');
  return response.data;
}

export async function fetchPriceData(symbol: string): Promise<PriceData[]> {
  const response = await api.get<PriceData[]>('/getPriceData', {
    params: { symbol }
  });
  return response.data;
}

export default api;
